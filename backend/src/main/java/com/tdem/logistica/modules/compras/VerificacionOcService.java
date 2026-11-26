package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoOrden;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.compras.dto.GuardarVerificacionRequest;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto.Item;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto.NotaRef;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto.Persona;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * F-LOG-08 "Verificacion de adquisiciones por orden de compra" (DEC-L102): una por
 * OC, cuando ya se recibio todo lo que pasa por el almacen. Logistica marca por
 * producto Y/F (funcionalidad y estandares pueden no aplicar; especificaciones
 * siempre), escribe la conclusion y elige quien da el V°B° del area solicitante o
 * especialista. Se relaciona sola con las Notas de Inventario que despachan lo
 * comprado (por las lineas de requerimiento que atiende la OC).
 */
@Service
public class VerificacionOcService {

    private final JdbcTemplate jdbc;
    private final OrigenCompras origen;
    private final Auditoria auditoria;

    public VerificacionOcService(JdbcTemplate jdbc, OrigenCompras origen, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.origen = origen;
        this.auditoria = auditoria;
    }

    /** Cabecera de la OC para la pantalla y el PDF. */
    public record Cabecera(UUID orderId, String codigo, String estado, boolean aprobada, String lugarEntrega,
                           String proveedor, String proveedorRuc, String proveedorTelefono, String empresaCode) {
    }

    @Transactional(readOnly = true)
    public VerificacionOcDto obtener(UUID orderId, UsuarioPrincipal principal) {
        if (!OrdenCompraService.esDecisorDeOrden(principal)) {
            throw ApiException.forbidden("No tienes permiso para ver órdenes de compra.");
        }
        return armar(orderId, principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN));
    }

    @Transactional
    public VerificacionOcDto guardar(UUID orderId, GuardarVerificacionRequest r, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("Solo Logística registra la verificación de una orden de compra.");
        }
        jdbc.query("select 1 from orden_abastecimiento where order_id = ? for update", rs -> { }, orderId);
        VerificacionOcDto actual = armar(orderId, true);
        if (!actual.disponible()) {
            throw ApiException.badRequest(actual.motivo());
        }
        Map<UUID, GuardarVerificacionRequest.Item> porLinea = new HashMap<>();
        for (GuardarVerificacionRequest.Item i : r.items()) {
            porLinea.put(i.orderItemId(), i);
        }
        Set<UUID> lineas = new HashSet<>();
        actual.items().forEach(i -> lineas.add(i.orderItemId()));
        if (!lineas.equals(porLinea.keySet())) {
            throw ApiException.badRequest("Verifica todas las líneas de la orden, y solo esas.");
        }
        Integer especialista = jdbc.queryForObject(
                "select count(*) from usuario where user_id = ? and active", Integer.class, r.especialistaUserId());
        if (especialista == null || especialista == 0) {
            throw ApiException.badRequest("Quien da el V°B° del área solicitante debe ser un usuario activo.");
        }

        Map<String, Object> antes = actual.registrada() ? resumen(actual) : null;
        jdbc.update("""
                insert into verificacion_oc (order_id, verified_by, specialist_user_id, conclusion)
                values (?, ?, ?, ?)
                on conflict (order_id) do update
                   set verified_by = excluded.verified_by, specialist_user_id = excluded.specialist_user_id,
                       conclusion = excluded.conclusion, updated_at = now()""",
                orderId, principal.userId(), r.especialistaUserId(), r.conclusion().trim());
        jdbc.update("delete from verificacion_oc_item where order_id = ?", orderId);
        List<Object[]> filas = new ArrayList<>();
        for (GuardarVerificacionRequest.Item i : r.items()) {
            filas.add(new Object[]{i.orderItemId(), orderId, i.funcionalidad(), i.estandares(),
                    i.especificaciones(), limpio(i.observacion())});
        }
        jdbc.batchUpdate("""
                insert into verificacion_oc_item (order_item_id, order_id, functionality, standards, specifications,
                                                  observation)
                values (?, ?, ?, ?, ?, ?)""", filas);
        VerificacionOcDto guardada = armar(orderId, true);
        auditoria.registrar("ORDEN_COMPRA", orderId, "VERIFICAR_F_LOG_08", antes, resumen(guardada),
                principal.userId());
        return guardada;
    }

    /** Ordenes con F-LOG-08 registrado que despachan algo de esta Nota de Inventario. */
    public List<VerificacionDeNota> deNota(UUID noteId) {
        return jdbc.query("""
                select distinct o.order_id, o.order_code
                  from nota_inventario ni
                  join salida_item si on si.issue_id = ni.issue_id
                  join orden_item oi on oi.request_item_id = si.request_item_id
                       or oi.consolidation_id in (select n.consolidation_id from necesidad_compra n
                                                   where n.request_item_id = si.request_item_id)
                  join orden_abastecimiento o on o.order_id = oi.order_id
                  join verificacion_oc v on v.order_id = o.order_id
                 where ni.inventory_note_id = ?
                 order by o.order_code""",
                (rs, n) -> new VerificacionDeNota(rs.getObject(1, UUID.class), rs.getString(2)), noteId);
    }

    public record VerificacionDeNota(UUID orderId, String orderCode) {
    }

    // ---------------------------------------------------------------- lectura

    public Cabecera cabecera(UUID orderId) {
        return jdbc.query("""
                select o.order_code, o.status, o.approved_at is not null, o.direct_delivery, o.delivery_location,
                       p.legal_name, p.ruc, coalesce(nullif(p.phone, ''), p.whatsapp_phone), e.code
                  from orden_abastecimiento o
                  join proveedor p on p.supplier_id = o.supplier_id
                  join empresa e on e.company_id = o.company_id
                 where o.order_id = ?""",
                rs -> {
                    if (!rs.next()) {
                        throw ApiException.notFound("Orden de compra no encontrada.");
                    }
                    String lugar = rs.getString(5);
                    if (rs.getBoolean(4)) {
                        lugar = "Entrega directa en proyecto" + (lugar == null || lugar.isBlank() ? "" : ": " + lugar);
                    }
                    return new Cabecera(orderId, rs.getString(1), rs.getString(2), rs.getBoolean(3), lugar,
                            rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9));
                }, orderId);
    }

    /** Todo lo de la pantalla; la autorizacion la valida quien llama. */
    public VerificacionOcDto armar(UUID orderId, boolean gestiona) {
        Cabecera c = cabecera(orderId);
        List<Item> items = items(orderId);
        String motivo = motivo(c, items);

        Object[] v = jdbc.query("""
                select v.verified_at, v.verified_by, v.specialist_user_id, v.conclusion
                  from verificacion_oc v where v.order_id = ?""",
                rs -> rs.next() ? new Object[]{rs.getTimestamp(1), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4)} : null, orderId);
        Map<UUID, Persona> personas = personas();
        boolean registrada = v != null;
        return new VerificacionOcDto(orderId, c.codigo(), motivo == null, motivo, registrada,
                gestiona && motivo == null,
                registrada ? ((Timestamp) v[0]).toInstant() : null,
                registrada ? personas.get((UUID) v[1]) : null,
                registrada && v[2] != null ? personas.get((UUID) v[2]) : null,
                sugerido(orderId),
                registrada ? (String) v[3] : null,
                items, notas(orderId),
                gestiona ? activos(personas) : List.of());
    }

    private List<Persona> activos(Map<UUID, Persona> personas) {
        Set<UUID> activos = new HashSet<>(jdbc.queryForList(
                "select user_id from usuario where active and password_hash is not null", UUID.class));
        return personas.values().stream().filter(p -> activos.contains(p.id())).toList();
    }

    private static String motivo(Cabecera c, List<Item> items) {
        if (EstadoOrden.ANULADA.equals(c.estado())) {
            return "La orden de compra está anulada.";
        }
        if (!c.aprobada()) {
            return "La orden de compra aún no está aprobada.";
        }
        if (items.isEmpty()) {
            return "La orden de compra no tiene líneas.";
        }
        boolean completa = items.stream().allMatch(i -> i.recibida().compareTo(i.cantidad()) >= 0);
        return completa ? null : "Se verifica cuando se haya recibido todo lo de la orden de compra.";
    }

    private List<Item> items(UUID orderId) {
        return jdbc.query("""
                select oi.order_item_id, oi.description, pr.name, pv.attribute_value, um.code, oi.qty_ordered,
                       oi.product_id is not null,
                       coalesce((select sum(ri.qty_received) from recepcion_item ri
                                  where ri.order_item_id = oi.order_item_id), 0),
                       vi.functionality, vi.standards, vi.specifications, vi.observation
                  from orden_item oi
                  left join producto pr on pr.product_id = oi.product_id
                  left join producto_variante pv on pv.variant_id = oi.variant_id
                  left join unidad_medida um on um.uom_id = oi.uom_id
                  left join verificacion_oc_item vi on vi.order_item_id = oi.order_item_id
                 where oi.order_id = ?
                 order by coalesce(pr.sku_base, ''), oi.order_item_id""",
                (rs, n) -> {
                    String descripcion = rs.getString(2);
                    if (descripcion == null || descripcion.isBlank()) {
                        String atributo = rs.getString(4);
                        descripcion = rs.getString(3) + (atributo != null && !atributo.isBlank() ? " (" + atributo + ")" : "");
                    }
                    boolean producto = rs.getBoolean(7);
                    BigDecimal cantidad = rs.getBigDecimal(6);
                    // Servicios y productos escritos a mano no pasan por el almacen (DEC-L080/L094).
                    BigDecimal recibida = producto ? rs.getBigDecimal(8) : cantidad;
                    String um = rs.getString(5) != null ? rs.getString(5) : (producto ? null : "SERV");
                    return new Item(rs.getObject(1, UUID.class), descripcion, um, cantidad, recibida, producto,
                            rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12));
                }, orderId);
    }

    /** Usuarios con cuenta: posibles V°B° del area solicitante o especialista. */
    private Map<UUID, Persona> personas() {
        Map<UUID, Persona> out = new LinkedHashMap<>();
        jdbc.query("""
                select user_id, display_name, cargo, firma_key is not null
                  from usuario where password_hash is not null
                 order by display_name""",
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    out.put(id, new Persona(id, rs.getString(2), rs.getString(3), rs.getBoolean(4)));
                });
        return out;
    }

    /** El solicitante del requerimiento que atiende la OC, si es uno solo. */
    private UUID sugerido(UUID orderId) {
        UUID requestId = OrigenCompras.unico(origen.deOrden(orderId));
        if (requestId == null) {
            return null;
        }
        return jdbc.query("select requester_user_id from requerimiento where request_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, requestId);
    }

    private List<NotaRef> notas(UUID orderId) {
        return jdbc.query("""
                with atendidas as (
                  select n.request_item_id
                    from orden_item oi join necesidad_compra n on n.consolidation_id = oi.consolidation_id
                   where oi.order_id = ?
                  union
                  select oi.request_item_id from orden_item oi
                   where oi.order_id = ? and oi.request_item_id is not null)
                select distinct ni.inventory_note_id, ni.inventory_note_code, ni.status
                  from nota_inventario ni
                  join salida_item si on si.issue_id = ni.issue_id
                 where si.request_item_id in (select request_item_id from atendidas)
                   and ni.status <> 'ANULADA'
                 order by ni.inventory_note_code""",
                (rs, n) -> new NotaRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                orderId, orderId);
    }

    private static Map<String, Object> resumen(VerificacionOcDto v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("especialista", v.especialista() != null ? v.especialista().nombre() : null);
        m.put("conclusion", v.conclusion());
        List<String> lineas = new ArrayList<>();
        for (Item i : v.items()) {
            lineas.add(i.descripcion() + ": " + guion(i.funcionalidad()) + "/" + guion(i.estandares()) + "/"
                    + guion(i.especificaciones()));
        }
        m.put("lineas", lineas);
        return m;
    }

    private static String guion(String s) {
        return s == null ? "-" : s;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
