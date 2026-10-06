package com.tdem.logistica.documentos;

import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.tracking.TrackingService;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * RF-038/082/083: PDFs corporativos. La autorizacion es la misma de la
 * pantalla de cada documento: se obtiene el DTO por el servicio del modulo
 * (que valida rol/ownership) y recien entonces se dibuja.
 */
@Service
@Transactional(readOnly = true)
public class DocumentosService {

    public record Pdf(String nombre, byte[] contenido) {
    }

    private final RequerimientoService requerimientoService;
    private final TrackingService trackingService;
    private final ComprasService comprasService;
    private final DespachoService despachoService;
    private final UsuarioRepository usuarioRepository;

    public DocumentosService(RequerimientoService requerimientoService, TrackingService trackingService,
                             ComprasService comprasService, DespachoService despachoService,
                             UsuarioRepository usuarioRepository) {
        this.requerimientoService = requerimientoService;
        this.trackingService = trackingService;
        this.comprasService = comprasService;
        this.despachoService = despachoService;
        this.usuarioRepository = usuarioRepository;
    }

    public Pdf requerimiento(UUID id, UsuarioPrincipal principal) {
        var d = requerimientoService.obtener(id, principal);
        return new Pdf(d.requerimiento().code() + ".pdf", DocumentosPdf.requerimiento(d, pie(principal)));
    }

    public Pdf tracking(UUID requestId, UsuarioPrincipal principal) {
        var t = trackingService.obtener(requestId, principal);
        return new Pdf("TRACKING_" + t.requerimiento().code() + ".pdf", DocumentosPdf.tracking(t, pie(principal)));
    }

    public Pdf rfq(UUID id, UsuarioPrincipal principal) {
        var d = comprasService.obtenerRfq(id, principal);
        return new Pdf(d.rfq().rfqCode() + ".pdf", DocumentosPdf.rfq(d, pie(principal)));
    }

    public Pdf orden(UUID id, UsuarioPrincipal principal) {
        var d = comprasService.obtenerOrden(id, principal);
        return new Pdf(d.orden().orderCode() + ".pdf", DocumentosPdf.orden(d, pie(principal)));
    }

    public Pdf notaInventario(UUID id, UsuarioPrincipal principal) {
        var d = despachoService.obtenerNota(id, principal);
        return new Pdf(d.nota().inventoryNoteCode() + ".pdf", DocumentosPdf.notaInventario(d, pie(principal)));
    }

    /** Trazabilidad del impreso: quien y cuando lo genero. */
    private String pie(UsuarioPrincipal principal) {
        String nombre = usuarioRepository.findById(principal.userId())
                .map(Usuario::getDisplayName)
                .orElse("usuario");
        return "Generado el " + DocumentosPdf.fechaHora(Instant.now()) + " por " + nombre
                + " · ERP Logístico Corporativo";
    }
}
