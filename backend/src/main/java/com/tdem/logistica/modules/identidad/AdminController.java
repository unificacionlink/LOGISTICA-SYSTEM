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

import java.util.List;
import java.util.UUID;

/** Administracion de identidad (solo ADMIN, DEC-L024). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final PersonalManualService personal;
    private final PadronPersonalService padron;

    public AdminController(AdminService service, PersonalManualService personal, PadronPersonalService padron) {
        this.service = service;
        this.personal = personal;
        this.padron = padron;
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

    /** CECOs que aprueba el usuario (DEC-L037). */
    @PutMapping("/usuarios/{id}/cecos-aprobados")
    public ApiResponse<AdminDatosDto> asignarCecosAprobados(@PathVariable UUID id,
                                                            @Valid @RequestBody AsignarCecosRequest request,
                                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.asignarCecosAprobados(id, request.cecoIds(), principal),
                "CECOs que aprueba actualizados.");
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

    // ---------- Padron de personal (DEC-L041) ----------

    @GetMapping("/personal")
    public ApiResponse<List<PersonalAdminDto>> personal(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.personal(principal));
    }

    /** Alta individual (empresas sin padron en RR.HH., DEC-L064). */
    @PostMapping("/personal")
    public ApiResponse<List<PersonalAdminDto>> crearPersona(@Valid @RequestBody PersonaRequest request,
                                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(padron.crear(request, principal), "Persona agregada al padron.");
    }

    @PutMapping("/personal/{id}")
    public ApiResponse<List<PersonalAdminDto>> editarPersona(@PathVariable UUID id,
                                                             @Valid @RequestBody PersonaRequest request,
                                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(padron.editar(id, request, principal), "Datos actualizados.");
    }

    /** No se elimina a nadie: se inactiva con fecha y solo ADMIN lo reactiva (DEC-L064). */
    @PatchMapping("/personal/{id}/activo")
    public ApiResponse<List<PersonalAdminDto>> cambiarActivoPersona(@PathVariable UUID id,
                                                                    @RequestBody CambiarActivoRequest request,
                                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(padron.cambiarActivo(id, request.activo(), principal),
                request.activo() ? "Persona reactivada." : "Persona inactivada.");
    }

    @PostMapping("/personal/{id}/acceso")
    public ApiResponse<DarAccesoDto> darAcceso(@PathVariable UUID id, @Valid @RequestBody DarAccesoRequest request,
                                               @AuthenticationPrincipal UsuarioPrincipal principal) {
        DarAccesoDto r = service.darAcceso(id, request, principal);
        return ApiResponse.ok(r, r.acceso() != null ? "Cuenta creada."
                : "Ya tenia cuenta: quedo vinculada. Revisa sus roles en Usuarios.");
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


    @PostMapping("/sincronizar-rrhh")
    public ApiResponse<AdminDatosDto> sincronizar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        AdminDatosDto d = service.sincronizar(principal);
        SyncResultadoDto r = d.ultimoResultado();
        return ApiResponse.ok(d, "Sincronizacion lista: " + r.recibidos() + " personas recibidas, " + r.creados()
                + " nuevas, " + r.actualizados() + " con cambios, " + r.desactivados() + " ceses; CECOs: "
                + r.cecosCreados() + " nuevos, " + r.cecosDesactivados() + " desactivados.");
    }
}
