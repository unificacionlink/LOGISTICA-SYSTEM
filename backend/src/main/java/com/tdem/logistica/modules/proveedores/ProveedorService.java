package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.GuardarProveedorRequest;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;
import com.tdem.logistica.repository.ProveedorRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Maestro de Proveedores (Lote 5). Evaluacion/reevaluacion de proveedores
 * sigue fuera de alcance (DEC-L004, Jonathan no tiene el TO-BE cerrado).
 */
@Service
@Transactional(readOnly = true)
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public ProveedorService(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    private void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar proveedores.");
        }
    }

    public List<ProveedorDto> listar(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return proveedorRepository.findAllByActiveTrueOrderByLegalName().stream()
                .map(ProveedorMapper::toDto)
                .toList();
    }

    public ProveedorDto obtener(UUID id, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return ProveedorMapper.toDto(buscar(id));
    }

    @Transactional
    public ProveedorDto crear(GuardarProveedorRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (proveedorRepository.existsByRuc(request.ruc())) {
            throw ApiException.badRequest("Ya existe un proveedor con ese RUC.");
        }

        Proveedor p = new Proveedor();
        p.setSupplierId(UUID.randomUUID());
        aplicar(p, request);
        proveedorRepository.save(p);
        return ProveedorMapper.toDto(p);
    }

    @Transactional
    public ProveedorDto actualizar(UUID id, GuardarProveedorRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Proveedor p = buscar(id);
        if (!p.getRuc().equals(request.ruc()) && proveedorRepository.existsByRuc(request.ruc())) {
            throw ApiException.badRequest("Ya existe un proveedor con ese RUC.");
        }
        aplicar(p, request);
        proveedorRepository.save(p);
        return ProveedorMapper.toDto(p);
    }

    private void aplicar(Proveedor p, GuardarProveedorRequest request) {
        p.setRuc(request.ruc());
        p.setLegalName(request.legalName());
        p.setContactName(request.contactName());
        p.setEmail(request.email());
        p.setWhatsappPhone(request.whatsappPhone());
        p.setSupplierType(request.supplierType() != null ? request.supplierType() : "NO_CRITICO");
        p.setPreferredDeliveryMode(request.preferredDeliveryMode());
        p.setActive(request.active());
    }

    private Proveedor buscar(UUID id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Proveedor no encontrado."));
    }
}
