package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.entity.RequerimientoItem;
import com.tdem.logistica.repository.RequerimientoItemRepository;
import com.tdem.logistica.repository.SalidaItemRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RF-079: el estado de atencion de un requerimiento NUNCA se marca a mano. Se
 * calcula comparando, por request_item_id, lo solicitado contra lo despachado
 * cuyo envio ya fue recepcionado en destino, mas lo comprado con la caja chica
 * del proyecto que Logistica ya regularizo (DEC-L054: no pasa por el almacen).
 */
@Component
public class AtencionCalculator {

    private static final Set<String> ESTADOS_EN_ATENCION = Set.of(
            EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);

    private final RequerimientoItemRepository requerimientoItemRepository;
    private final SalidaItemRepository salidaItemRepository;
    private final JdbcTemplate jdbc;

    AtencionCalculator(RequerimientoItemRepository requerimientoItemRepository,
                       SalidaItemRepository salidaItemRepository, JdbcTemplate jdbc) {
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.salidaItemRepository = salidaItemRepository;
        this.jdbc = jdbc;
    }

    /** Devuelve el estado que corresponde; si el REQ no esta en atencion, conserva el actual. */
    public String calcular(Requerimiento r) {
        if (!ESTADOS_EN_ATENCION.contains(r.getStatus())) {
            return r.getStatus();
        }
        List<RequerimientoItem> items = requerimientoItemRepository.findByRequerimiento(r.getRequestId());
        // Lo entregado de todos los items en una consulta (antes una por item).
        Map<UUID, BigDecimal> entregados = new HashMap<>();
        for (Object[] fila : salidaItemRepository.sumarEntregadoPorItem(r.getRequestId())) {
            entregados.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        jdbc.query("""
                select ci.request_item_id, sum(ci.quantity) from caja_chica_item ci
                join caja_chica_ref f on f.petty_cash_ref_id = ci.petty_cash_ref_id
                where f.request_id = ? and f.destination = 'PROYECTO' and f.regularization_status = 'REGULARIZADO'
                group by ci.request_item_id""", rs -> {
            entregados.merge(rs.getObject(1, UUID.class), rs.getBigDecimal(2), BigDecimal::add);
        }, r.getRequestId());
        boolean algunoEntregado = false;
        boolean todosCompletos = !items.isEmpty();
        for (RequerimientoItem item : items) {
            BigDecimal entregado = entregados.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO);
            if (entregado.signum() > 0) {
                algunoEntregado = true;
            }
            if (entregado.compareTo(item.getQtyRequested()) < 0) {
                todosCompletos = false;
            }
        }
        if (todosCompletos) {
            return EstadoRequerimiento.ATENCION_COMPLETA;
        }
        return algunoEntregado ? EstadoRequerimiento.ATENCION_PARCIAL : EstadoRequerimiento.EN_ATENCION;
    }
}
