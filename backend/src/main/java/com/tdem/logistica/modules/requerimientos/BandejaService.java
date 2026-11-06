package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.modules.requerimientos.dto.AvanceRequerimientoDto;
import com.tdem.logistica.modules.requerimientos.dto.BandejaDto;
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
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final Set<String> APROBADOS = Set.of(
            EstadoRequerimiento.VERIFICACION_STOCK, EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);
    private static final Set<String> DESPACHABLES = Set.of(
            EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);
    private static final Set<String> CERRADOS = Set.of(
            EstadoRequerimiento.ATENCION_COMPLETA, EstadoRequerimiento.RECHAZADO);
    /** Acciones de compras (incluye las que esperan al proveedor). */
    private static final Set<String> DE_COMPRAS = Set.of(
            "EMITIR_OC", "ADJUDICAR", "REGISTRAR_CAJA_CHICA", "DECIDIR_RUTA", "PEDIR_COTIZACION",
            "ESPERA_COTIZACION", "RECIBIR_OC");
    private static final Map<String, Integer> PESO_PRIORIDAD = Map.of("CRITICA", 0, "URGENTE", 1, "NORMAL", 2);

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
                     coalesce(sum(si.qty) filter (where exists (
                       select 1 from envio v where v.issue_id = si.issue_id and v.status in ('RECEPCIONADO','CERRADO'))), 0) as entregado
                from salida_item si join it on it.request_item_id = si.request_item_id
               group by si.request_item_id
            ),
            res as (
              select rs.request_item_id, sum(rs.qty_reserved) as listo
                from reserva_stock rs join it on it.request_item_id = rs.request_item_id
               where rs.status = 'RESERVADA'
               group by rs.request_item_id
            ),
            nec as (
              select n.need_id, n.request_item_id, it.request_id, n.status, n.qty, n.consolidation_id, k.status as kstatus,
                     case when n.status = 'CAJA_CHICA' then
                       coalesce((select sum(cci.quantity) from caja_chica_item cci
                                   join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                                  where cci.request_item_id = n.request_item_id and cr.regularization_status <> 'ANULADO'), 0)
                     else coalesce((select sum(rs.qty_reserved) from reserva_stock rs
                                     where rs.need_id = n.need_id and rs.status in ('RESERVADA','DESPACHADA')), 0)
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
                     coalesce(s.entregado, 0) as entregado,
                     coalesce(s.despachado, 0) - coalesce(s.entregado, 0) as camino,
                     coalesce(r.listo, 0) as listo,
                     coalesce((select sum(greatest(nc.qty - nc.recibido, 0)) from nec nc
                                where nc.request_item_id = it.request_item_id), 0) as compra
                from it
                left join sal s on s.request_item_id = it.request_item_id
                left join res r on r.request_item_id = it.request_item_id
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
              select distinct on (nc.request_id) nc.request_id, o.order_id
                from nec nc
                join orden_item oi on oi.consolidation_id = nc.consolidation_id
                join orden_abastecimiento o on o.order_id = oi.order_id
               where o.status in ('EMITIDA','EN_ATENCION','RECEPCION_PARCIAL')
               order by nc.request_id, o.created_at
            ),
            env as (
              select distinct on (v.request_id) v.request_id, v.inventory_note_id
                from envio v join base b on b.request_id = v.request_id
               where v.status in ('PENDIENTE','EN_TRANSITO') and v.inventory_note_id is not null
               order by v.request_id
            )
            select b.*, coalesce(i.n, 0) as items_count, i.resumen,
                   coalesce(a.f_entregado, 0) as f_entregado, coalesce(a.f_camino, 0) as f_camino,
                   coalesce(a.f_listo, 0) as f_listo, coalesce(a.f_compra, 0) as f_compra,
                   coalesce(a.entregados, 0) as entregados,
                   coalesce(x.ruta, false) as ruta, coalesce(x.caja, false) as caja, coalesce(x.emitir, false) as emitir,
                   x.pedir, x.adjudicar, x.esperando, oc.order_id, env.inventory_note_id
              from base b
              left join items i on i.request_id = b.request_id
              left join avance a on a.request_id = b.request_id
              left join acc x on x.request_id = b.request_id
              left join oc on oc.request_id = b.request_id
              left join env on env.request_id = b.request_id
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
            if (rs.getBoolean("caja")) {
                acciones.add(new BandejaDto.Accion("REGISTRAR_CAJA_CHICA", false, id.toString()));
            }
            if (rs.getBoolean("ruta")) {
                acciones.add(new BandejaDto.Accion("DECIDIR_RUTA", false, null));
            }
            agregar(acciones, "PEDIR_COTIZACION", false, rs.getString("pedir"));
            agregar(acciones, "ESPERA_COTIZACION", true, rs.getString("esperando"));
            if (orden != null) {
                acciones.add(new BandejaDto.Accion("RECIBIR_OC", true, orden.toString()));
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
        } else if (EstadoRequerimiento.BORRADOR.equals(status) || EstadoRequerimiento.OBSERVADO.equals(status)) {
            vistas.add("pendientes");
        } else {
            vistas.add("proceso");
        }

        double entregado = rs.getDouble("f_entregado");
        double camino = Math.min(rs.getDouble("f_camino"), 1 - entregado);
        double listo = Math.min(rs.getDouble("f_listo"), 1 - entregado - camino);
        double compra = Math.min(rs.getDouble("f_compra"), Math.max(0, 1 - entregado - camino - listo));

        BandejaDto.Fila fila = new BandejaDto.Fila(
                id, rs.getString("request_code"), rs.getString("empresa"), rs.getString("ceco_code"),
                rs.getString("ceco_name"), requester, rs.getString("solicitante"), rs.getString("priority"), status,
                rs.getObject("required_date", LocalDate.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("items_count"), rs.getInt("entregados"), rs.getString("resumen"),
                new BandejaDto.Avance(redondear(entregado), redondear(camino), redondear(listo), redondear(compra)),
                acciones);
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
                   coalesce((select sum(si.qty) from salida_item si where si.request_item_id = it.request_item_id), 0) as despachado,
                   coalesce((select sum(si.qty) from salida_item si where si.request_item_id = it.request_item_id
                               and exists (select 1 from envio v where v.issue_id = si.issue_id
                                             and v.status in ('RECEPCIONADO','CERRADO'))), 0) as entregado,
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
                    BigDecimal entregado = rs.getBigDecimal("entregado");
                    return new AvanceRequerimientoDto.Item(
                            rs.getObject("request_item_id", UUID.class), rs.getString("codigo"), rs.getString("name"),
                            rs.getString("attribute_value"), rs.getString("uom"), rs.getBigDecimal("q"), entregado,
                            despachado.subtract(entregado), rs.getBigDecimal("listo"), rs.getBigDecimal("compra"),
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
