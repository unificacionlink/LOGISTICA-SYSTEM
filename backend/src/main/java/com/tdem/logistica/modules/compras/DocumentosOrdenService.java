package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.compras.dto.DocumentoOrdenDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Documentos relacionados de una OC (DEC-L089, observacion 7 de Logistica), en
 * una sola consulta: lo que se adjunto al pagar (constancias y factura), las
 * guias y recepciones del proveedor y las Notas de Inventario de los
 * requerimientos de origen. Solo lectura; los archivos privados no se exponen.
 */
@Service
public class DocumentosOrdenService {

    private static final String SQL = """
            with reqs as (
              select o.request_id from orden_abastecimiento o where o.order_id = :orden and o.request_id is not null
              union
              select ri.request_id from orden_item oi
                join requerimiento_item ri on ri.request_item_id = oi.request_item_id
               where oi.order_id = :orden
              union
              select ri.request_id from orden_item oi
                join necesidad_compra n on n.consolidation_id = oi.consolidation_id
                join requerimiento_item ri on ri.request_item_id = n.request_item_id
               where oi.order_id = :orden
            )
            select 'PAGO' as tipo,
                   case d.document_type when 'FACTURA_PDF' then 'Factura ' || coalesce(o.invoice_number, '')
                                        else 'Constancia de pago' end as titulo,
                   d.file_name as detalle, d.uploaded_at as fecha, d.file_url as url, null as ruta
              from documento_relacion r
              join documento d on d.document_id = r.document_id
              join orden_abastecimiento o on o.order_id = r.entity_id
             where r.entity_type = 'ORDEN' and r.entity_id = :orden
            union all
            select 'GUIA', 'Guía de remisión ' || g.series || '-' || g.number, null,
                   g.issue_date::timestamptz, dg.file_url, null
              from guia_remision g left join documento dg on dg.document_id = g.document_id
             where g.order_id = :orden
            union all
            select 'RECEPCION', 'Recepción N° ' || rc.receipt_number,
                   case rc.status when 'COMPLETA' then 'Completa' when 'PARCIAL' then 'Parcial' else 'Observada' end
                     || case when rc.destination_ceco_id is not null then ' · en el proyecto' else ' · en el almacén' end,
                   rc.receipt_date, dc.file_url, '/almacen/ordenes/' || rc.order_id || '/recepcion'
              from recepcion rc left join documento dc on dc.document_id = rc.document_id
             where rc.order_id = :orden
            union all
            select 'NOTA_INVENTARIO', n.inventory_note_code,
                   case n.status when 'ANULADA' then 'Anulada' when 'EMITIDA' then 'Emitida'
                                 when 'DESPACHADA' then 'Despachada' else 'Recepcionada' end || ' · ' || r.request_code,
                   n.issued_at, null, '/almacen/notas/' || n.inventory_note_id
              from nota_inventario n join reqs on reqs.request_id = n.request_id
              join requerimiento r on r.request_id = n.request_id
             order by fecha desc nulls last
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DocumentosOrdenService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DocumentoOrdenDto> listar(UUID orderId, UsuarioPrincipal principal) {
        if (!OrdenCompraService.esDecisorDeOrden(principal)) {
            throw ApiException.forbidden("No tienes permiso para gestionar compras.");
        }
        return jdbc.query(SQL, new MapSqlParameterSource("orden", orderId), (rs, i) -> {
            Timestamp fecha = rs.getTimestamp("fecha");
            String url = rs.getString("url");
            return new DocumentoOrdenDto(rs.getString("tipo"), rs.getString("titulo"), rs.getString("detalle"),
                    fecha != null ? fecha.toInstant() : null,
                    url != null && url.startsWith("http") ? url : null, rs.getString("ruta"));
        });
    }
}
