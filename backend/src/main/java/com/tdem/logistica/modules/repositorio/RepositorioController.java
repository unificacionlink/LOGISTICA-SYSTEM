package com.tdem.logistica.modules.repositorio;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.repositorio.dto.DocumentoRepositorioDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Repositorio de documentos (DEC-L091). */
@RestController
@RequestMapping("/api/repositorio")
public class RepositorioController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final RepositorioService service;

    public RepositorioController(RepositorioService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DocumentoRepositorioDto>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String texto,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(tipo, empresa, periodo, texto, principal));
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        RepositorioService.Archivo a = service.descargar(id, principal);
        return Descargas.archivo(a.contenido(), a.nombre(), Descargas.PDF);
    }

    @GetMapping("/zip")
    public ResponseEntity<byte[]> zip(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String periodo,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RepositorioService.Archivo a = service.zip(tipo, empresa, periodo, principal);
        return Descargas.archivo(a.contenido(), a.nombre(), ZIP);
    }
}
