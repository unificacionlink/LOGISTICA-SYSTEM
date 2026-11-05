package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.Almacen;
import com.tdem.logistica.repository.AlmacenRepository;
import org.springframework.stereotype.Component;

/**
 * DEC-L045: todo el stock lo maneja TDEM. Un solo almacen central atiende a
 * TDEM, GOYDEL y METRIN: reservas al aprobar, recepciones de OC, despachos y
 * entradas por caja chica usan este almacen, sea cual sea la empresa del
 * documento (que se sigue registrando en el movimiento).
 */
@Component
public class AlmacenCentral {

    private final AlmacenRepository almacenRepository;

    public AlmacenCentral(AlmacenRepository almacenRepository) {
        this.almacenRepository = almacenRepository;
    }

    public Almacen obtener() {
        return almacenRepository.findCentralDelGrupo()
                .orElseThrow(() -> ApiException.badRequest("No hay un almacen central del grupo configurado."));
    }

    /** Bloquea la fila del almacen: serializa reservas y cargas de inventario concurrentes. */
    public Almacen bloquear() {
        return almacenRepository.bloquear(obtener().getWarehouseId())
                .orElseThrow(() -> ApiException.badRequest("No hay un almacen central del grupo configurado."));
    }
}
