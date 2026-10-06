package com.tdem.logistica.documentos;

import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** RF-038/082/083: descarga de PDFs corporativos, junto a la ruta de cada recurso. */
@RestController
public class DocumentosController {

    private final DocumentosService service;

    public DocumentosController(DocumentosService service) {
        this.service = service;
    }

    @GetMapping("/api/requerimientos/{id}/pdf")
    public ResponseEntity<byte[]> requerimiento(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descargar(service.requerimiento(id, principal));
    }

    @GetMapping("/api/requerimientos/{id}/tracking/pdf")
    public ResponseEntity<byte[]> tracking(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descargar(service.tracking(id, principal));
    }

    @GetMapping("/api/compras/rfq/{id}/pdf")
    public ResponseEntity<byte[]> rfq(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descargar(service.rfq(id, principal));
    }

    @GetMapping("/api/compras/ordenes/{id}/pdf")
    public ResponseEntity<byte[]> orden(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descargar(service.orden(id, principal));
    }

    @GetMapping("/api/almacen/notas/{id}/pdf")
    public ResponseEntity<byte[]> notaInventario(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return descargar(service.notaInventario(id, principal));
    }

    private static ResponseEntity<byte[]> descargar(DocumentosService.Pdf pdf) {
        return Descargas.archivo(pdf.contenido(), pdf.nombre(), Descargas.PDF);
    }
}
