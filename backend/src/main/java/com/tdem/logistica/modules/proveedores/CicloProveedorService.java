package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoProveedor;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;
import com.tdem.logistica.repository.ProveedorRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigirMotivo;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.limpio;

/**
 * Ciclo de incorporacion (DEC-L069): Borrador -> Pendiente de revision ->
 * Habilitado / Observado / Rechazado. Solo habilitar activa al proveedor para
 * Compras. Ningun paso es automatico y todos quedan en log_cambios.
 */
@Service
@Transactional
public class CicloProveedorService {

    public static final String HABILITAR_CON_ADVERTENCIAS = "HABILITAR_CON_ADVERTENCIAS";

    private final ProveedorRepository proveedorRepository;
    private final ProveedorService proveedores;
    private final FichaProveedorService ficha;
    private final Auditoria auditoria;

    public CicloProveedorService(ProveedorRepository proveedorRepository, ProveedorService proveedores,
                                 FichaProveedorService ficha, Auditoria auditoria) {
        this.proveedorRepository = proveedorRepository;
        this.proveedores = proveedores;
        this.ficha = ficha;
        this.auditoria = auditoria;
    }

    /** Borrador u observado pasa a revision. Exige al menos un medio de contacto. */
    public ProveedorDto enviar(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(id);
        String antes = p.getStatus();
        if (!EstadoProveedor.BORRADOR.equals(antes) && !EstadoProveedor.OBSERVADO.equals(antes)) {
            throw ApiException.badRequest("Solo se envía a revisión un proveedor en borrador u observado.");
        }
        if (limpio(p.getEmail()) == null && limpio(p.getPhone()) == null && limpio(p.getWhatsappPhone()) == null) {
            throw ApiException.badRequest("Registra al menos un correo o teléfono de contacto antes de enviar.");
        }
        p.setStatus(EstadoProveedor.PENDIENTE_REVISION);
        p.setSubmittedAt(OffsetDateTime.now());
        proveedorRepository.save(p);
        auditoria.registrar("PROVEEDOR", id, "ENVIAR_REVISION", Map.of("estado", antes),
                Map.of("estado", p.getStatus()), principal.userId());
        return ProveedorMapper.toDto(p);
    }

    /**
     * Decide una solicitud en revision. Observar y rechazar exigen motivo;
     * habilitar lo exige solo si hay advertencias (DEC-L069, decision del usuario).
     */
    public ProveedorDto decidir(UUID id, String decision, String motivo, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(id);
        if (!EstadoProveedor.PENDIENTE_REVISION.equals(p.getStatus())) {
            throw ApiException.badRequest("Solo se decide un proveedor pendiente de revisión.");
        }
        String m = limpio(motivo);
        Map<String, Object> despues = new HashMap<>();
        String accion;
        switch (decision) {
            case "HABILITAR" -> {
                List<String> avisos = ficha.advertencias(id);
                if (!avisos.isEmpty() && m == null) {
                    throw new ApiException("Para habilitar con advertencias escribe el motivo: "
                            + String.join(" ", avisos), HABILITAR_CON_ADVERTENCIAS, HttpStatus.BAD_REQUEST);
                }
                if (!avisos.isEmpty()) {
                    despues.put("advertencias", avisos);
                }
                p.setStatus(EstadoProveedor.HABILITADO);
                p.setActive(true);
                accion = "HABILITAR";
            }
            case "OBSERVAR" -> {
                m = exigirMotivo(m, "Escribe qué debe corregir el proveedor.");
                p.setStatus(EstadoProveedor.OBSERVADO);
                accion = "OBSERVAR";
            }
            case "RECHAZAR" -> {
                m = exigirMotivo(m, "Escribe el motivo del rechazo.");
                p.setStatus(EstadoProveedor.RECHAZADO);
                accion = "RECHAZAR";
            }
            default -> throw ApiException.badRequest("Decisión inválida.");
        }
        p.setStatusReason(m);
        p.setDecidedAt(OffsetDateTime.now());
        p.setDecidedBy(principal.userId());
        proveedorRepository.save(p);
        despues.put("estado", p.getStatus());
        if (m != null) {
            despues.put("motivo", m);
        }
        auditoria.registrar("PROVEEDOR", id, accion, Map.of("estado", EstadoProveedor.PENDIENTE_REVISION),
                despues, principal.userId());
        return ProveedorMapper.toDto(p);
    }

    /** Un rechazado vuelve a borrador (no se duplica el RUC). */
    public ProveedorDto reabrir(UUID id, String motivo, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(id);
        if (!EstadoProveedor.RECHAZADO.equals(p.getStatus())) {
            throw ApiException.badRequest("Solo se reabre un proveedor rechazado.");
        }
        String m = exigirMotivo(motivo, "Escribe el motivo para reabrir la solicitud.");
        p.setStatus(EstadoProveedor.BORRADOR);
        p.setStatusReason(m);
        proveedorRepository.save(p);
        auditoria.registrar("PROVEEDOR", id, "REABRIR", Map.of("estado", EstadoProveedor.RECHAZADO),
                Map.of("estado", p.getStatus(), "motivo", m), principal.userId());
        return ProveedorMapper.toDto(p);
    }

    /** Activo/inactivo para Compras, solo en un habilitado. Desactivar exige motivo. */
    public ProveedorDto cambiarActivo(UUID id, boolean activo, String motivo, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(id);
        if (!EstadoProveedor.HABILITADO.equals(p.getStatus())) {
            throw ApiException.badRequest("Solo un proveedor habilitado se activa o desactiva.");
        }
        if (p.isActive() == activo) {
            return ProveedorMapper.toDto(p);
        }
        String m = activo ? limpio(motivo) : exigirMotivo(motivo, "Escribe el motivo para desactivar al proveedor.");
        p.setActive(activo);
        proveedorRepository.save(p);
        Map<String, Object> despues = new HashMap<>();
        despues.put("activo", activo);
        if (m != null) {
            despues.put("motivo", m);
        }
        auditoria.registrar("PROVEEDOR", id, activo ? "ACTIVAR" : "DESACTIVAR", Map.of("activo", !activo),
                despues, principal.userId());
        return ProveedorMapper.toDto(p);
    }
}
