package com.tdem.logistica.modules.notificaciones;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.notificaciones.dto.BandejaNotificacionesDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Campana de la plataforma (DEC-L076): cada usuario ve y marca solo las suyas. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final AvisosPlataforma avisos;

    public NotificacionController(AvisosPlataforma avisos) {
        this.avisos = avisos;
    }

    @GetMapping
    public ApiResponse<BandejaNotificacionesDto> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(avisos.bandeja(principal));
    }

    @PostMapping("/{id}/leida")
    public ApiResponse<BandejaNotificacionesDto> leida(@PathVariable UUID id,
                                                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(avisos.marcarLeida(id, principal));
    }

    @PostMapping("/leidas")
    public ApiResponse<BandejaNotificacionesDto> todas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(avisos.marcarTodas(principal));
    }
}
