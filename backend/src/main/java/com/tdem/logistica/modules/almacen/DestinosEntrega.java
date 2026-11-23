package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.NecesidadCompra;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.entity.OrdenItem;
import com.tdem.logistica.entity.RequerimientoItem;
import com.tdem.logistica.repository.NecesidadCompraRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A donde va cada linea de una OC de entrega directa (DEC-L084): el proveedor
 * entrega en el CECO de cada requerimiento, no en el almacen. Una linea de un
 * consolidado se reparte entre sus necesidades de compra en el mismo orden que
 * la recepcion en almacen (urgente, aprovisionamiento, implementacion; luego la
 * mas antigua); una OC directa sin requerimiento va entera al CECO de la OC.
 */
@Component
public class DestinosEntrega {

    /** Parte de una linea de la OC que va a un CECO (y, si aplica, a un item de requerimiento). */
    public record Destino(OrdenItem item, NecesidadCompra necesidad, RequerimientoItem requerimientoItem, Ceco ceco,
                          BigDecimal asignado) {

        public UUID needId() {
            return necesidad != null ? necesidad.getNeedId() : null;
        }

        public UUID requestItemId() {
            return requerimientoItem != null ? requerimientoItem.getRequestItemId() : null;
        }

        /** Clave de la linea: item de OC + necesidad + item de requerimiento. */
        public String clave() {
            return clave(item.getOrderItemId(), needId(), requestItemId());
        }

        public static String clave(UUID orderItemId, UUID needId, UUID requestItemId) {
            return orderItemId + "|" + needId + "|" + requestItemId;
        }
    }

    private final NecesidadCompraRepository necesidadRepository;

    public DestinosEntrega(NecesidadCompraRepository necesidadRepository) {
        this.necesidadRepository = necesidadRepository;
    }

    /**
     * Destinos de las lineas de producto (las de servicio no se entregan). Lanza
     * badRequest si alguna unidad no tiene a donde ir: la OC no puede ser de
     * entrega directa (lo que sobra de un consolidado se recibe en el almacen).
     */
    public List<Destino> destinos(OrdenAbastecimiento orden, List<OrdenItem> items) {
        List<OrdenItem> productos = items.stream().filter(i -> i.getProducto() != null).toList();
        if (productos.isEmpty()) {
            throw ApiException.badRequest("La orden no tiene productos que entregar.");
        }
        List<UUID> consolidados = productos.stream()
                .map(OrdenItem::getConsolidacion).filter(Objects::nonNull)
                .map(k -> k.getConsolidationId()).distinct().toList();
        Map<UUID, List<NecesidadCompra>> porConsolidado = consolidados.isEmpty() ? Map.of()
                : necesidadRepository.findByConsolidaciones(consolidados).stream()
                        .filter(n -> "COMPRA".equals(n.getStatus()))
                        .collect(Collectors.groupingBy(n -> n.getConsolidacion().getConsolidationId(),
                                LinkedHashMap::new, Collectors.toList()));

        List<Destino> out = new ArrayList<>();
        for (OrdenItem oi : productos) {
            if (oi.getConsolidacion() != null) {
                BigDecimal resta = oi.getQtyOrdered();
                for (NecesidadCompra n : porConsolidado.getOrDefault(oi.getConsolidacion().getConsolidationId(), List.of())) {
                    BigDecimal toma = n.getQty().min(resta);
                    if (toma.signum() > 0) {
                        RequerimientoItem ri = n.getRequerimientoItem();
                        out.add(new Destino(oi, n, ri, ri.getRequerimiento().getCeco(), toma));
                        resta = resta.subtract(toma);
                    }
                }
                if (resta.signum() > 0) {
                    throw ApiException.badRequest("\"" + oi.getDescription() + "\" tiene "
                            + resta.stripTrailingZeros().toPlainString()
                            + " unidad(es) que no son de ningún requerimiento: esa orden debe recibirse en el almacén.");
                }
            } else if (oi.getRequerimientoItem() != null) {
                RequerimientoItem ri = oi.getRequerimientoItem();
                out.add(new Destino(oi, null, ri, ri.getRequerimiento().getCeco(), oi.getQtyOrdered()));
            } else if (orden.getCeco() != null) {
                // OC directa (DEC-L080): el requerimiento es solo referencia; va al CECO de la orden.
                out.add(new Destino(oi, null, null, orden.getCeco(), oi.getQtyOrdered()));
            } else {
                throw ApiException.badRequest("Indica el CECO de la orden para entregarla directo.");
            }
        }
        return out;
    }
}
