package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.modules.stock.AlmacenCentral;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DEC-L086: TDEM, duena del almacen central, vende a GOYDEL y METRIN lo que
 * entrega en sus proyectos. Lo que va a un CECO de otra empresa se valoriza al
 * precio de venta vigente de esa empresa (precio_venta_producto, DEC-L035) y
 * el precio queda congelado en la linea; lo que va a un CECO de TDEM no es venta.
 */
@Component
public class VentaInterna {

    private final AlmacenCentral almacenCentral;
    private final NamedParameterJdbcTemplate jdbc;

    public VentaInterna(AlmacenCentral almacenCentral, NamedParameterJdbcTemplate jdbc) {
        this.almacenCentral = almacenCentral;
        this.jdbc = jdbc;
    }

    /** La empresa a la que TDEM le vende, o null si el destino es de la propia TDEM. */
    public Empresa compradora(Empresa destino) {
        return destino.getCompanyId().equals(almacenCentral.empresa().getCompanyId()) ? null : destino;
    }

    /** Precio de venta vigente (PEN) de cada producto para la empresa; sin fila = sin precio. */
    public Map<UUID, BigDecimal> precios(Empresa compradora, Collection<UUID> productIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (compradora == null || productIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                select product_id, sale_price from precio_venta_producto
                 where company_id = :empresa and active and product_id in (:productos)""",
                new MapSqlParameterSource().addValue("empresa", compradora.getCompanyId())
                        .addValue("productos", productIds),
                rs -> {
                    out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
                });
        return out;
    }
}
