package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.identidad.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import java.util.UUID;

/** Administracion de identidad (solo ADMIN, DEC-L024). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final PersonalManualService personal;

    public AdminController(AdminService service, PersonalManualService personal) {
        this.service = service;
        this.personal = personal;
    }

    @GetMapping
    public ApiResponse<AdminDatosDto> datos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.datos(principal));
    }

    @PutMapping("/usuarios/{id}/roles")
    public ApiResponse<AdminDatosDto> asignarRoles(@PathVariable UUID id, @Valid @RequestBody AsignarRolesRequest request,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.asignarRoles(id, request.roles(), principal), "Roles actualizados.");
    }

    @PutMapping("/usuarios/{id}/cecos")
    public ApiResponse<AdminDatosDto> asignarCecos(@PathVariable UUID id, @Valid @RequestBody AsignarCecosRequest request,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.asignarCecos(id, request.cecoIds(), principal), "CECOs actualizados.");
    }

    @PostMapping("/usuarios/{id}/password-temporal")
    public ApiResponse<PasswordTemporalDto> passwordTemporal(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.passwordTemporal(id, principal), "Contrasena temporal generada.");
    }

    @PatchMapping("/usuarios/{id}/activo")
    public ApiResponse<AdminDatosDto> cambiarActivo(@PathVariable UUID id, @RequestBody CambiarActivoRequest request,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.cambiarActivo(id, request.activo(), principal),
                request.activo() ? "Usuario activado." : "Usuario desactivado.");
    }

    @PostMapping("/usuarios")
    public ApiResponse<AdminDatosDto> crearUsuario(@Valid @RequestBody CrearUsuarioRequest request,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.crearUsuario(request, principal), "Usuario creado.");
    }

    @PutMapping("/usuarios/{id}")
    public ApiResponse<AdminDatosDto> editarUsuario(@PathVariable UUID id, @Valid @RequestBody EditarUsuarioRequest request,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.editarUsuario(id, request, principal), "Datos actualizados.");
    }

    // ---------- Padron de personal manual (GOYDEL/METRIN, DEC-L028) ----------

    @GetMapping("/personal/plantilla")
    public ResponseEntity<byte[]> plantillaPersonal(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return Descargas.archivo(personal.plantilla(principal), "PERSONAL_GOYDEL_METRIN_" + LocalDate.now() + ".xlsx",
                Descargas.XLSX);
    }

    @PostMapping(value = "/personal/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PersonalCargaDto> validarPersonal(@RequestParam("file") MultipartFile file,
                                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(personal.validar(file, principal));
    }

    @PostMapping(value = "/personal/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PersonalCargaDto> confirmarPersonal(@RequestParam("file") MultipartFile file,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        PersonalCargaDto r = personal.confirmar(file, principal);
        return ApiResponse.ok(r, "Personal actualizado: " + r.nuevos() + " nuevos, " + r.actualizados()
                + " actualizados, " + r.desactivados() + " desactivados.");
    }

    @PostMapping("/cecos")
    public ApiResponse<AdminDatosDto> crearCeco(@Valid @RequestBody CrearCecoRequest request,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.crearCeco(request, principal), "CECO creado.");
    }

    @PutMapping("/cecos/{id}/aprobador")
    public ApiResponse<AdminDatosDto> asignarAprobadorCeco(@PathVariable UUID id,
                                                           @RequestBody AsignarAprobadorCecoRequest request,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.asignarAprobadorCeco(id, request.userId(), principal), "Aprobador actualizado.");
    }

    @PostMapping("/sincronizar-rrhh")
    public ApiResponse<AdminDatosDto> sincronizar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        AdminDatosDto d = service.sincronizar(principal);
        SyncResultadoDto r = d.ultimoResultado();
        return ApiResponse.ok(d, "Sincronizacion lista: " + r.creados() + " nuevos, " + r.actualizados()
                + " actualizados, " + r.desactivados() + " desactivados; CECOs: " + r.cecosCreados() + " nuevos, "
                + r.cecosDesactivados() + " desactivados.");
    }
}
