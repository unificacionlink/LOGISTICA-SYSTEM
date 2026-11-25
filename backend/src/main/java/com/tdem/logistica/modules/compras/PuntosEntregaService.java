package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.entity.PuntoEntrega;
import com.tdem.logistica.modules.compras.dto.CrearPuntoEntregaRequest;
import com.tdem.logistica.modules.compras.dto.PuntoEntregaDto;
import com.tdem.logistica.modules.stock.AlmacenCentral;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.PuntoEntregaRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lista de lugares de entrega de las OC (DEC-L089): el almacen central y las
 * direcciones de los proyectos que Logistica va agregando. La OC guarda el punto
 * elegido y, en delivery_location, su texto (lo que sale en el F-LOG-09).
 */
@Service
@Transactional(readOnly = true)
public class PuntosEntregaService {

    private final PuntoEntregaRepository repository;
    private final CecoRepository cecoRepository;
    private final AlmacenCentral almacenCentral;
    private final Auditoria auditoria;

    public PuntosEntregaService(PuntoEntregaRepository repository, CecoRepository cecoRepository,
                                AlmacenCentral almacenCentral, Auditoria auditoria) {
        this.repository = repository;
        this.cecoRepository = cecoRepository;
        this.almacenCentral = almacenCentral;
        this.auditoria = auditoria;
    }

    private static void exigirLogistica(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar lugares de entrega.");
        }
    }

    public List<PuntoEntregaDto> listar(UsuarioPrincipal principal) {
        exigirLogistica(principal);
        return repository.findActivos().stream().map(PuntosEntregaService::toDto).toList();
    }

    @Transactional
    public PuntoEntregaDto crear(CrearPuntoEntregaRequest r, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        PuntoEntrega p = new PuntoEntrega();
        p.setDeliveryPointId(UUID.randomUUID());
        Ceco ceco = null;
        if (r.cecoId() != null) {
            ceco = cecoRepository.findById(r.cecoId()).filter(Ceco::isActive)
                    .orElseThrow(() -> ApiException.badRequest("CECO no encontrado."));
        }
        p.setCeco(ceco);
        p.setEmpresa(ceco != null ? ceco.getEmpresa() : almacenCentral.empresa());
        p.setPointType(ceco != null ? "CECO" : "OTRO");
        p.setName(r.nombre().trim());
        p.setAddress(r.direccion().trim());
        p.setActive(true);
        p.setCreatedBy(principal.userId());
        repository.insertar(p);
        auditoria.registrar("PUNTO_ENTREGA", p.getDeliveryPointId(), "CREAR", null,
                Map.of("nombre", p.getName(), "direccion", p.getAddress()), principal.userId());
        return toDto(p);
    }

    /** El almacen central, lugar por defecto de la OC que nace de una cotizacion; null si no esta en la lista. */
    public UUID almacenPorDefecto() {
        return repository.findActivos().stream()
                .filter(p -> "ALMACEN".equals(p.getPointType())
                        && p.getEmpresa().getCompanyId().equals(almacenCentral.empresa().getCompanyId()))
                .map(PuntoEntrega::getDeliveryPointId).findFirst().orElse(null);
    }

    /** Fija el lugar elegido en la OC (null = sin lugar) y guarda su texto para el F-LOG-09. */
    public void aplicar(OrdenAbastecimiento orden, UUID deliveryPointId) {
        if (deliveryPointId == null) {
            orden.setPuntoEntrega(null);
            orden.setDeliveryLocation(null);
            return;
        }
        PuntoEntrega p = repository.findById(deliveryPointId).filter(PuntoEntrega::isActive)
                .orElseThrow(() -> ApiException.badRequest("Lugar de entrega no encontrado."));
        orden.setPuntoEntrega(p);
        String texto = p.getName() + " — " + p.getAddress();
        orden.setDeliveryLocation(texto.length() <= 220 ? texto : texto.substring(0, 220));
    }

    static PuntoEntregaDto toDto(PuntoEntrega p) {
        return new PuntoEntregaDto(p.getDeliveryPointId(), p.getName(), p.getAddress(), p.getPointType(),
                p.getEmpresa().getCode(), p.getCeco() != null ? p.getCeco().getCode() : null,
                p.getCeco() != null ? p.getCeco().getName() : null);
    }
}
