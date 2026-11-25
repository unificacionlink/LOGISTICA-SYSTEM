package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.modules.requerimientos.dto.AvanceRequerimientoDto;
import com.tdem.logistica.modules.requerimientos.dto.BandejaDto;
import com.tdem.logistica.modules.requerimientos.dto.TableroDto;
import com.tdem.logistica.repository.RequerimientoRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;

/**
 * Bandeja operativa de Requerimientos (DEC-L048, referencia ERPNext Material
 * Request). Una sola SQL trae, por requerimiento en alcance, su avance y lo que
 * tiene pendiente en compras y almacen (misma logica que las etapas del
 * dashboard); aqui se derivan la siguiente accion, las vistas, la busqueda, el
 * orden, los conteos y la pagina. Solo lectura: no cambia ningun estado.
 */
@Service
public class BandejaService {

    static final int POR_PAGINA = 50;
    static final String ETAPA_APROBACION = "aprobacion";
    static final String ETAPA_ABASTECIMIENTO = "abastecimiento";
    static final String ETAPA_PREPARACION = "preparacion";
    static final String ETAPA_TRANSITO = "transito";
    static final String ETAPA_DIFERENCIAS = "diferencias";
    static final String ETAPA_ATENDIDO = "atendido";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final Set<String> APROBADOS = Set.of(
            EstadoRequerimiento.VERIFICACION_STOCK, EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);
    private static final Set<String> DESPACHABLES = Set.of(
            EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);
    private static final Set<String> CERRADOS = Set.of(
            EstadoRequerimiento.ATENCION_COMPLETA, EstadoRequerimiento.RECHAZADO);
    /** Acciones de compras (incluye las que esperan al proveedor). */
    private static final Set<String> DE_COMPRAS = Set.of(
            "EMITIR_OC", "ADJUDICAR", "REGISTRAR_CAJA_CHICA", "REGULARIZAR_CAJA_CHICA", "DECIDIR_RUTA",
            "PEDIR_COTIZACION", "ESPERA_COTIZACION", "APROBAR_OC", "LIBERAR_OC", "RECIBIR_OC");
    /** Acciones del solicitante que lo dejan en "Pendientes mios" (DEC-L054). */
    private static final Set<String> DEL_SOLICITANTE = Set.of(
            "ENVIAR", "CORREGIR", "REGISTRAR_CAJA_CHICA", "CORREGIR_CAJA_CHICA", "RECIBIR_EN_PROYECTO");
    private static final Map<String, Integer> PESO_PRIORIDAD = Map.of("URGENTE", 0, "APROVISIONAMIENTO", 1, "IMPLEMENTACION", 2);

    private static final String SQL = """
            with base as (
              select r.request_id, r.request_code, r.status, r.priority, r.required_date, r.created_at,
                     r.requester_user_id, e.code as empresa, c.code as ceco_code, c.name as ceco_name,
                     u.display_name as solicitante
                from requerimiento r
                join empresa e on e.company_id = r.company_id
                left join ceco c on c.ceco_id = r.ceco_id
                left join usuario u on u.user_id = r.requester_user_id
               where (:todos or r.requester_user_id = :yo)
                 and (r.status <> 'BORRADOR' or r.requester_user_id = :yo)
            ),
            it as (
              select ri.request_id, ri.request_item_id, ri.qty_requested as q, p.name
                from requerimiento_item ri
                join base b on b.request_id = ri.request_id
                join producto p on p.product_id = ri.product_id
            ),
            sal as (
              select si.request_item_id, sum(si.qty) as despachado,
                     sum(si.dest_accepted_qty) as entregado
                from salida_item si join it on it.request_item_id = si.request_item_id
                join salida s on s.issue_id = si.issue_id and s.status <> 'ANULADA'
               group by si.request_item_id
            ),
            dir as (
              select rci.request_item_id, sum(rci.qty_received) as entregado
                from recepcion_item rci join it on it.request_item_id = rci.request_item_id
               group by rci.request_item_id
            ),
            res as (
              select rs.request_item_id, sum(rs.qty_reserved) as listo
                from reserva_stock rs join it on it.request_item_id = rs.request_item_id
               where rs.status = 'RESERVADA'
               group by rs.request_item_id
            ),
            ccp as (
              select cci.request_item_id, sum(cci.quantity) as regularizado
                from caja_chica_item cci
                join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                join it on it.request_item_id = cci.request_item_id
               where cr.destination = 'PROYECTO' and cr.regularization_status = 'REGULARIZADO'
               group by cci.request_item_id
            ),
            cc as (
              select cr.request_id,
                     bool_or(cr.regularization_status = 'PENDIENTE_REGULARIZACION') as por_revisar,
                     bool_or(cr.regularization_status = 'OBSERVADO') as observada
                from caja_chica_ref cr join base b on b.request_id = cr.request_id
               where cr.destination = 'PROYECTO'
               group by cr.request_id
            ),
            nec as (
              select n.need_id, n.request_item_id, it.request_id, n.status, n.qty, n.consolidation_id, k.status as kstatus,
                     case when n.status = 'CAJA_CHICA' then
                       coalesce((select sum(cci.quantity) from caja_chica_item cci
                                   join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                                  where cci.request_item_id = n.request_item_id and cr.regularization_status <> 'ANULADO'), 0)
                     else coalesce((select sum(rs.qty_reserved) from reserva_stock rs
                                     where rs.need_id = n.need_id and rs.status in ('RESERVADA','DESPACHADA')), 0)
                        -- entrega directa del proveedor al CECO (DEC-L084)
                        + coalesce((select sum(rci.qty_received) from recepcion_item rci where rci.need_id = n.need_id), 0)
                     end as recibido,
                     exists (select 1 from rfq_item q
                               join cotizacion_item ci on ci.rfq_item_id = q.rfq_item_id
                               join cotizacion co on co.quotation_id = ci.quotation_id
                              where q.consolidation_id = n.consolidation_id and co.status <> 'DESCARTADA') as con_ofertas
                from necesidad_compra n
                join it on it.request_item_id = n.request_item_id
                left join consolidacion k on k.consolidation_id = n.consolidation_id
               where n.status <> 'ANULADA'
            ),
            por_item as (
              select it.request_id, it.q,
                     coalesce(s.entregado, 0) + coalesce(cp.regularizado, 0) + coalesce(d.entregado, 0) as entregado,
                     coalesce(s.despachado, 0) - coalesce(s.entregado, 0) as camino,
                     coalesce(r.listo, 0) as listo,
                     coalesce((select sum(greatest(nc.qty - nc.recibido, 0)) from nec nc
                                where nc.request_item_id = it.request_item_id), 0) as compra
                from it
                left join sal s on s.request_item_id = it.request_item_id
                left join res r on r.request_item_id = it.request_item_id
                left join ccp cp on cp.request_item_id = it.request_item_id
                left join dir d on d.request_item_id = it.request_item_id
            ),
            avance as (
              select request_id,
                     avg(least(entregado / nullif(q, 0), 1)) as f_entregado,
                     avg(least(camino / nullif(q, 0), 1)) as f_camino,
                     avg(least(listo / nullif(q, 0), 1)) as f_listo,
                     avg(least(compra / nullif(q, 0), 1)) as f_compra,
                     count(*) filter (where entregado >= q) as entregados
                from por_item group by request_id
            ),
            items as (
              select request_id, count(*) as n, string_agg(name, ' · ' order by name) as resumen
                from it group by request_id
            ),
            acc as (
              select request_id,
                     bool_or(status = 'PENDIENTE_RUTA') as ruta,
                     bool_or(status = 'CAJA_CHICA' and qty > recibido) as caja,
                     bool_or(status = 'COMPRA' and kstatus = 'ADJUDICADA') as emitir,
                     string_agg(distinct cast(consolidation_id as text), ',')
                       filter (where status = 'COMPRA' and kstatus = 'ABIERTA') as pedir,
                     string_agg(distinct cast(consolidation_id as text), ',')
                       filter (where status = 'COMPRA' and kstatus = 'EN_COTIZACION' and con_ofertas) as adjudicar,
                     string_agg(distinct cast(consolidation_id as text), ',')
                       filter (where status = 'COMPRA' and kstatus = 'EN_COTIZACION' and not con_ofertas) as esperando
                from nec group by request_id
            ),
            oc as (
              select distinct on (nc.request_id) nc.request_id, o.order_id, o.status as oc_status,
                     o.released_at is not null as oc_liberada
                from nec nc
                join orden_item oi on oi.consolidation_id = nc.consolidation_id
                join orden_abastecimiento o on o.order_id = oi.order_id
               where o.status in ('POR_APROBAR','EMITIDA','EN_ATENCION','RECEPCION_PARCIAL')
               order by nc.request_id, (o.status = 'POR_APROBAR' or o.released_at is null) desc, o.created_at
            ),
            en_proyecto as (
              select distinct nc.request_id
                from nec nc
                join orden_item oi on oi.consolidation_id = nc.consolidation_id
                join orden_abastecimiento o on o.order_id = oi.order_id
               where o.direct_delivery and o.released_at is not null
                 and o.status in ('EMITIDA','EN_ATENCION','RECEPCION_PARCIAL')
                 and nc.status = 'COMPRA' and nc.qty > nc.recibido
            ),
            prep as (
              select n.request_id,
                     bool_or(n.picked_at is null) as por_piquear,
                     bool_or(n.picked_at is not null and n.packed_at is null) as por_embalar,
                     bool_or(n.packed_at is not null) as por_enviar
                from nota_inventario n join base b on b.request_id = n.request_id
               where n.status = 'EMITIDA'
               group by n.request_id
            ),
            obs as (
              select it.request_id, sum(si.dest_observed_qty) as observado
                from salida_item si join it on it.request_item_id = si.request_item_id
               group by it.request_id
            ),
            env as (
              select distinct on (v.request_id) v.request_id, v.inventory_note_id
                from envio v join base b on b.request_id = v.request_id
               where v.status in ('PENDIENTE','EN_TRANSITO','RECEPCION_PARCIAL') and v.inventory_note_id is not null
               order by v.request_id
            )
            select b.*, coalesce(i.n, 0) as items_count, i.resumen,
                   coalesce(a.f_entregado, 0) as f_entregado, coalesce(a.f_camino, 0) as f_camino,
                   coalesce(a.f_listo, 0) as f_listo, coalesce(a.f_compra, 0) as f_compra,
                   coalesce(a.entregados, 0) as entregados,
                   coalesce(x.ruta, false) as ruta, coalesce(x.caja, false) as caja, coalesce(x.emitir, false) as emitir,
                   x.pedir, x.adjudicar, x.esperando, oc.order_id, oc.oc_status, coalesce(oc.oc_liberada, false) as oc_liberada,
                   env.inventory_note_id,
                   coalesce(cc.por_revisar, false) as cc_por_revisar, coalesce(cc.observada, false) as cc_observada,
                   coalesce(pr.por_piquear, false) as por_piquear, coalesce(pr.por_embalar, false) as por_embalar,
                   coalesce(pr.por_enviar, false) as por_enviar, coalesce(ob.observado, 0) as observado,
                   exists (select 1 from en_proyecto eo where eo.request_id = b.request_id) as en_proyecto
              from base b
              left join items i on i.request_id = b.request_id
              left join avance a on a.request_id = b.request_id
              left join acc x on x.request_id = b.request_id
              left join oc on oc.request_id = b.request_id
              left join env on env.request_id = b.request_id
              left join cc on cc.request_id = b.request_id
              left join prep pr on pr.request_id = b.request_id
              left join obs ob on ob.request_id = b.request_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoService requerimientoService;

    public BandejaService(NamedParameterJdbcTemplate jdbc, RequerimientoRepository requerimientoRepository,
                          RequerimientoService requerimientoService) {
        this.jdbc = jdbc;
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoService = requerimientoService;
    }

    /** Fila en memoria: datos del REQ + texto de busqueda + vistas a las que pertenece. */
    private record Registro(BandejaDto.Fila fila, String busqueda, Set<String> vistas, boolean cerrado) {
    }

    private record DefVista(String id, String label) {
    }

    private static final List<DefVista> VISTAS_GESTOR = List.of(
            new DefVista("atencion", "Requieren atención"),
            new DefVista("compras", "Compras"),
            new DefVista("despacho", "Despacho"),
            new DefVista("seguimiento", "Seguimiento"),
            new DefVista("cerrados", "Cerrados"));
    private static final List<DefVista> VISTAS_SOLICITANTE = List.of(
            new DefVista("pendientes", "Pendientes míos"),
            new DefVista("proceso", "En proceso"),
            new DefVista("cerrados", "Cerrados"));

    public BandejaDto bandeja(String vista, String empresa, String q, int pagina, UsuarioPrincipal principal) {
        boolean gestor = principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
        List<DefVista> defs = gestor ? VISTAS_GESTOR : VISTAS_SOLICITANTE;
        String vistaActiva = defs.stream().map(DefVista::id).filter(v -> v.equals(vista)).findFirst()
                .orElse(defs.get(0).id());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("todos", gestor)
                .addValue("yo", principal.userId());
        LocalDate hoy = LocalDate.now(LIMA);
        List<Registro> todos = jdbc.query(SQL, params, (rs, n) -> registro(rs, gestor, principal.userId()));

        List<String> palabras = Arrays.stream(normalizar(q == null ? "" : q).split("\\s+"))
                .filter(s -> !s.isBlank()).limit(6).toList();
        Predicate<Registro> porBusqueda = r -> palabras.stream().allMatch(r.busqueda()::contains);
        String emp = empresa == null || empresa.isBlank() ? null : empresa;
        Predicate<Registro> porEmpresa = r -> emp == null || emp.equals(r.fila().empresaCode());

        // Conteo de cada vista: con la empresa y la busqueda aplicadas.
        List<BandejaDto.Vista> vistas = defs.stream()
                .map(d -> new BandejaDto.Vista(d.id(), d.label(), (int) todos.stream()
                        .filter(porEmpresa).filter(porBusqueda).filter(r -> r.vistas().contains(d.id())).count()))
                .toList();
        // Conteo por empresa: dentro de la vista activa y la busqueda.
        Map<String, Integer> porEmp = new TreeMap<>();
        todos.stream().filter(porBusqueda).filter(r -> r.vistas().contains(vistaActiva))
                .forEach(r -> porEmp.merge(r.fila().empresaCode(), 1, Integer::sum));
        List<BandejaDto.Empresa> empresas = porEmp.entrySet().stream()
                .map(e -> new BandejaDto.Empresa(e.getKey(), e.getValue())).toList();

        List<Registro> filtrados = todos.stream()
                .filter(porEmpresa).filter(porBusqueda).filter(r -> r.vistas().contains(vistaActiva))
                .sorted(orden("cerrados".equals(vistaActiva), hoy))
                .toList();
        int pag = Math.max(pagina, 0);
        List<BandejaDto.Fila> filas = filtrados.stream()
                .skip((long) pag * POR_PAGINA).limit(POR_PAGINA).map(Registro::fila).toList();
        return new BandejaDto(vistaActiva, vistas, empresas, filtrados.size(), pag, POR_PAGINA, filas);
    }

    // ---------------------------------------------------------------- tablero (DEC-L067)

    private static final List<DefVista> COLUMNAS = List.of(
            new DefVista(ETAPA_APROBACION, "Por aprobar"),
            new DefVista(ETAPA_ABASTECIMIENTO, "Abastecimiento"),
            new DefVista(ETAPA_PREPARACION, "Por despachar"),
            new DefVista(ETAPA_TRANSITO, "En tránsito"),
            new DefVista(ETAPA_DIFERENCIAS, "Con diferencias"),
            new DefVista(ETAPA_ATENDIDO, "Atendido"));

    /**
     * Tablero Kanban de Logistica: cada REQ en la primera etapa con algo pendiente.
     * Atendido muestra solo los mas recientes (es historial).
     */
    public TableroDto tablero(String empresa, String q, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("El tablero es de Logistica.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("todos", true)
                .addValue("yo", principal.userId());
        LocalDate hoy = LocalDate.now(LIMA);
        List<Registro> todos = jdbc.query(SQL, params, (rs, n) -> registro(rs, true, principal.userId()));

        List<String> palabras = Arrays.stream(normalizar(q == null ? "" : q).split("\\s+"))
                .filter(s -> !s.isBlank()).limit(6).toList();
        Predicate<Registro> porBusqueda = r -> palabras.stream().allMatch(r.busqueda()::contains);
        Predicate<Registro> enTablero = r -> r.fila().tablero().etapa() != null;
        String emp = empresa == null || empresa.isBlank() ? null : empresa;

        Map<String, Integer> porEmp = new TreeMap<>();
        todos.stream().filter(enTablero).filter(porBusqueda)
                .forEach(r -> porEmp.merge(r.fila().empresaCode(), 1, Integer::sum));
        List<BandejaDto.Empresa> empresas = porEmp.entrySet().stream()
                .map(e -> new BandejaDto.Empresa(e.getKey(), e.getValue())).toList();

        List<Registro> visibles = todos.stream().filter(enTablero).filter(porBusqueda)
                .filter(r -> emp == null || emp.equals(r.fila().empresaCode())).toList();
        List<TableroDto.Columna> columnas = COLUMNAS.stream().map(c -> {
            boolean historial = ETAPA_ATENDIDO.equals(c.id());
            List<BandejaDto.Fila> tarjetas = visibles.stream()
                    .filter(r -> c.id().equals(r.fila().tablero().etapa()))
                    .sorted(orden(historial, hoy))
                    .map(Registro::fila).toList();
            int max = historial ? 20 : TableroDto.MAX_POR_COLUMNA;
            return new TableroDto.Columna(c.id(), c.label(), tarjetas.size(),
                    tarjetas.stream().limit(max).toList());
        }).toList();
        return new TableroDto(columnas, empresas);
    }

    /**
     * Primera etapa del flujo con algo pendiente: lo que falta de comprar frena antes
     * que lo que falta despachar, y esto antes que lo que esta en camino. Borradores
     * y rechazados no van al tablero (null).
     */
    static String etapa(String status, List<BandejaDto.Accion> acciones, double compra, double listo, double camino,
                        String preparacion, int observado) {
        if (EstadoRequerimiento.PENDIENTE_APROBACION.equals(status) || EstadoRequerimiento.OBSERVADO.equals(status)) {
            return ETAPA_APROBACION;
        }
        if (EstadoRequerimiento.ATENCION_COMPLETA.equals(status)) {
            return ETAPA_ATENDIDO;
        }
        if (!APROBADOS.contains(status)) {
            return null;
        }
        if (compra > 0 || acciones.stream().anyMatch(a -> DE_COMPRAS.contains(a.codigo()))) {
            return ETAPA_ABASTECIMIENTO;
        }
        if (listo > 0 || preparacion != null || acciones.stream().anyMatch(a -> "DESPACHAR".equals(a.codigo()))) {
            return ETAPA_PREPARACION;
        }
        if (camino > 0 || acciones.stream().anyMatch(a -> "CONFIRMAR_ENTREGA".equals(a.codigo()))) {
            return ETAPA_TRANSITO;
        }
        if (observado > 0) {
            return ETAPA_DIFERENCIAS;
        }
        // Aprobado sin nada pendiente registrado: queda a la vista en el primer tramo para revisarlo.
        return ETAPA_ABASTECIMIENTO;
    }

    private static Comparator<Registro> orden(boolean cerrados, LocalDate hoy) {
        Comparator<Registro> recientes = Comparator.comparing(
                (Registro r) -> r.fila().createdAt(), Comparator.nullsLast(Comparator.reverseOrder()));
        if (cerrados) {
            return recientes;
        }
        // Vencidos primero, luego prioridad, fecha requerida mas cercana y el mas antiguo.
        return Comparator.comparing((Registro r) -> !vencido(r.fila(), hoy))
                .thenComparing(r -> PESO_PRIORIDAD.getOrDefault(r.fila().priority(), 3))
                .thenComparing(r -> r.fila().requiredDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.fila().createdAt(), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static boolean vencido(BandejaDto.Fila f, LocalDate hoy) {
        return f.requiredDate() != null && f.requiredDate().isBefore(hoy) && !CERRADOS.contains(f.status());
    }

    private static Registro registro(ResultSet rs, boolean gestor, UUID yo) throws SQLException {
        String status = rs.getString("status");
        UUID requester = rs.getObject("requester_user_id", UUID.class);
        UUID id = rs.getObject("request_id", UUID.class);
        boolean propio = yo.equals(requester);
        boolean aprobado = APROBADOS.contains(status);

        List<BandejaDto.Accion> acciones = new ArrayList<>();
        if (propio && EstadoRequerimiento.BORRADOR.equals(status)) {
            acciones.add(new BandejaDto.Accion("ENVIAR", false, id.toString()));
        }
        if (propio && EstadoRequerimiento.OBSERVADO.equals(status)) {
            acciones.add(new BandejaDto.Accion("CORREGIR", false, id.toString()));
        }
        // DEC-L054: el proyecto compra con su caja chica y su solicitante registra el comprobante.
        if (propio && aprobado && rs.getBoolean("caja")) {
            acciones.add(new BandejaDto.Accion("REGISTRAR_CAJA_CHICA", false, id.toString()));
        }
        if (propio && rs.getBoolean("cc_observada")) {
            acciones.add(new BandejaDto.Accion("CORREGIR_CAJA_CHICA", false, id.toString()));
        }
        // DEC-L084: el proveedor entrega en el proyecto; el solicitante registra lo que le llego.
        if (propio && aprobado && rs.getBoolean("en_proyecto")) {
            acciones.add(new BandejaDto.Accion("RECIBIR_EN_PROYECTO", false, id.toString()));
        }
        if (gestor && aprobado) {
            UUID orden = rs.getObject("order_id", UUID.class);
            UUID nota = rs.getObject("inventory_note_id", UUID.class);
            if (DESPACHABLES.contains(status) && rs.getDouble("f_listo") > 0) {
                acciones.add(new BandejaDto.Accion("DESPACHAR", false, id.toString()));
            }
            if (rs.getBoolean("emitir")) {
                acciones.add(new BandejaDto.Accion("EMITIR_OC", false, null));
            }
            agregar(acciones, "ADJUDICAR", false, rs.getString("adjudicar"));
            if (rs.getBoolean("cc_por_revisar")) {
                acciones.add(new BandejaDto.Accion("REGULARIZAR_CAJA_CHICA", false, null));
            }
            if (rs.getBoolean("caja") && !propio) {
                // La compra la hace el proyecto: Logistica espera (puede registrarla en su nombre).
                acciones.add(new BandejaDto.Accion("REGISTRAR_CAJA_CHICA", true, id.toString()));
            }
            if (rs.getBoolean("ruta")) {
                acciones.add(new BandejaDto.Accion("DECIDIR_RUTA", false, null));
            }
            agregar(acciones, "PEDIR_COTIZACION", false, rs.getString("pedir"));
            agregar(acciones, "ESPERA_COTIZACION", true, rs.getString("esperando"));
            if (orden != null) {
                // DEC-L061: la OC espera su aprobacion, luego Logistica la libera y recien el almacen recibe.
                if ("POR_APROBAR".equals(rs.getString("oc_status"))) {
                    acciones.add(new BandejaDto.Accion("APROBAR_OC", true, orden.toString()));
                } else if (!rs.getBoolean("oc_liberada")) {
                    acciones.add(new BandejaDto.Accion("LIBERAR_OC", false, orden.toString()));
                } else {
                    acciones.add(new BandejaDto.Accion("RECIBIR_OC", true, orden.toString()));
                }
            }
            if (nota != null) {
                acciones.add(new BandejaDto.Accion("CONFIRMAR_ENTREGA", true, nota.toString()));
            }
        }
        if (EstadoRequerimiento.PENDIENTE_APROBACION.equals(status)) {
            acciones.add(new BandejaDto.Accion("APROBACION", true, id.toString()));
        }
        if (!propio && EstadoRequerimiento.OBSERVADO.equals(status)) {
            acciones.add(new BandejaDto.Accion("CORRECCION", true, id.toString()));
        }

        boolean cerrado = CERRADOS.contains(status);
        Set<String> vistas = new HashSet<>();
        if (gestor) {
            boolean actua = acciones.stream().anyMatch(a -> !a.espera());
            if (cerrado) {
                vistas.add("cerrados");
            } else if (actua) {
                vistas.add("atencion");
            } else {
                vistas.add("seguimiento");
            }
            if (aprobado && acciones.stream().anyMatch(a -> DE_COMPRAS.contains(a.codigo()))) {
                vistas.add("compras");
            }
            if (acciones.stream().anyMatch(a -> "DESPACHAR".equals(a.codigo()))) {
                vistas.add("despacho");
            }
        } else if (cerrado) {
            vistas.add("cerrados");
        } else if (EstadoRequerimiento.BORRADOR.equals(status) || EstadoRequerimiento.OBSERVADO.equals(status)
                || acciones.stream().anyMatch(a -> !a.espera() && DEL_SOLICITANTE.contains(a.codigo()))) {
            vistas.add("pendientes");
        } else {
            vistas.add("proceso");
        }

        double entregado = rs.getDouble("f_entregado");
        double camino = Math.min(rs.getDouble("f_camino"), 1 - entregado);
        double listo = Math.min(rs.getDouble("f_listo"), 1 - entregado - camino);
        double compra = Math.min(rs.getDouble("f_compra"), Math.max(0, 1 - entregado - camino - listo));

        String preparacion = rs.getBoolean("por_piquear") ? "PICKING"
                : rs.getBoolean("por_embalar") ? "EMBALAJE" : rs.getBoolean("por_enviar") ? "ENVIO" : null;
        int observado = rs.getBigDecimal("observado").intValue();
        String etapa = etapa(status, acciones, compra, listo, camino, preparacion, observado);

        BandejaDto.Fila fila = new BandejaDto.Fila(
                id, rs.getString("request_code"), rs.getString("empresa"), rs.getString("ceco_code"),
                rs.getString("ceco_name"), requester, rs.getString("solicitante"), rs.getString("priority"), status,
                rs.getObject("required_date", LocalDate.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("items_count"), rs.getInt("entregados"), rs.getString("resumen"),
                new BandejaDto.Avance(redondear(entregado), redondear(camino), redondear(listo), redondear(compra)),
                acciones, new BandejaDto.Tablero(etapa, preparacion, observado));
        String busqueda = normalizar(String.join(" ", Objects.toString(fila.code(), ""),
                Objects.toString(fila.cecoCode(), ""), Objects.toString(fila.cecoNombre(), ""),
                Objects.toString(fila.solicitante(), ""), Objects.toString(fila.resumenItems(), "")));
        return new Registro(fila, busqueda, vistas, cerrado);
    }

    private static void agregar(List<BandejaDto.Accion> acciones, String codigo, boolean espera, String ids) {
        if (ids != null && !ids.isBlank()) {
            acciones.add(new BandejaDto.Accion(codigo, espera, ids));
        }
    }

    private static double redondear(double v) {
        return Math.round(Math.max(0, v) * 1000) / 1000.0;
    }

    static String normalizar(String t) {
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // ================================================================ avance por producto

    private static final String SQL_AVANCE = """
            with it as (
              select ri.request_item_id, ri.qty_requested as q, coalesce(v.internal_variant_code, p.sku_base) as codigo,
                     p.name, v.attribute_value, u.code as uom
                from requerimiento_item ri
                join producto p on p.product_id = ri.product_id
                left join producto_variante v on v.variant_id = ri.variant_id
                join unidad_medida u on u.uom_id = ri.uom_id
               where ri.request_id = :id
            ),
            nec as (
              select n.need_id, n.request_item_id, n.status, n.qty, n.consolidation_id, k.status as kstatus,
                     k.consolidation_code,
                     case when n.status = 'CAJA_CHICA' then
                       coalesce((select sum(cci.quantity) from caja_chica_item cci
                                   join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                                  where cci.request_item_id = n.request_item_id and cr.regularization_status <> 'ANULADO'), 0)
                     else coalesce((select sum(rs.qty_reserved) from reserva_stock rs
                                     where rs.need_id = n.need_id and rs.status in ('RESERVADA','DESPACHADA')), 0)
                        -- entrega directa del proveedor al CECO (DEC-L084)
                        + coalesce((select sum(rci.qty_received) from recepcion_item rci where rci.need_id = n.need_id), 0)
                     end as recibido,
                     (select count(distinct co.quotation_id) from rfq_item q
                        join cotizacion_item ci on ci.rfq_item_id = q.rfq_item_id
                        join cotizacion co on co.quotation_id = ci.quotation_id
                       where q.consolidation_id = n.consolidation_id and co.status <> 'DESCARTADA') as ofertas,
                     (select o.order_id from orden_item oi join orden_abastecimiento o on o.order_id = oi.order_id
                       where oi.consolidation_id = n.consolidation_id and o.status <> 'ANULADA'
                       order by o.created_at desc limit 1) as order_id
                from necesidad_compra n
                join it on it.request_item_id = n.request_item_id
                left join consolidacion k on k.consolidation_id = n.consolidation_id
               where n.status <> 'ANULADA'
            )
            select it.*,
                   coalesce((select sum(si.qty) from salida_item si
                              join salida s on s.issue_id = si.issue_id and s.status <> 'ANULADA'
                              where si.request_item_id = it.request_item_id), 0) as despachado,
                   coalesce((select sum(si.dest_accepted_qty) from salida_item si
                              where si.request_item_id = it.request_item_id), 0)
                     + coalesce((select sum(rci.qty_received) from recepcion_item rci
                                  where rci.request_item_id = it.request_item_id), 0) as entregado,
                   coalesce((select sum(cci.quantity) from caja_chica_item cci
                               join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                              where cci.request_item_id = it.request_item_id and cr.destination = 'PROYECTO'
                                and cr.regularization_status = 'REGULARIZADO'), 0) as regularizado,
                   coalesce((select sum(rs.qty_reserved) from reserva_stock rs
                              where rs.request_item_id = it.request_item_id and rs.status = 'RESERVADA'), 0) as listo,
                   coalesce((select sum(greatest(n.qty - n.recibido, 0)) from nec n where n.request_item_id = it.request_item_id), 0) as compra,
                   coalesce((select sum(n.recibido) from nec n where n.request_item_id = it.request_item_id), 0) as recibido,
                   ne.status as nec_status, ne.kstatus, ne.ofertas, ne.consolidation_id, ne.consolidation_code, ne.order_id,
                   (select o.order_code from orden_abastecimiento o where o.order_id = ne.order_id) as order_code
              from it
              left join lateral (select * from nec n where n.request_item_id = it.request_item_id
                                  order by (n.qty > n.recibido) desc, n.need_id limit 1) ne on true
             order by it.codigo
            """;

    public AvanceRequerimientoDto avance(UUID requestId, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(requestId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        requerimientoService.exigirAcceso(r, principal);
        List<AvanceRequerimientoDto.Item> items = jdbc.query(SQL_AVANCE, new MapSqlParameterSource("id", requestId),
                (rs, n) -> {
                    BigDecimal despachado = rs.getBigDecimal("despachado");
                    BigDecimal regularizado = rs.getBigDecimal("regularizado");
                    BigDecimal entregado = rs.getBigDecimal("entregado").add(regularizado);
                    return new AvanceRequerimientoDto.Item(
                            rs.getObject("request_item_id", UUID.class), rs.getString("codigo"), rs.getString("name"),
                            rs.getString("attribute_value"), rs.getString("uom"), rs.getBigDecimal("q"), entregado,
                            despachado.subtract(entregado.subtract(regularizado)), rs.getBigDecimal("listo"),
                            rs.getBigDecimal("compra"),
                            rs.getBigDecimal("recibido"),
                            etapa(rs.getString("nec_status"), rs.getString("kstatus"), rs.getObject("order_id", UUID.class)),
                            rs.getInt("ofertas"), rs.getObject("consolidation_id", UUID.class),
                            rs.getString("consolidation_code"), rs.getObject("order_id", UUID.class),
                            rs.getString("order_code"));
                });
        return new AvanceRequerimientoDto(items);
    }

    private static String etapa(String necesidad, String consolidado, UUID orden) {
        if (necesidad == null) {
            return null;
        }
        if ("PENDIENTE_RUTA".equals(necesidad)) {
            return "RUTA";
        }
        if ("CAJA_CHICA".equals(necesidad)) {
            return "CAJA_CHICA";
        }
        if (orden != null || "ORDENADA".equals(consolidado)) {
            return "OC";
        }
        return switch (consolidado == null ? "" : consolidado) {
            case "ABIERTA" -> "POR_COTIZAR";
            case "EN_COTIZACION" -> "EN_COTIZACION";
            case "ADJUDICADA" -> "ADJUDICADO";
            default -> null;
        };
    }
}
