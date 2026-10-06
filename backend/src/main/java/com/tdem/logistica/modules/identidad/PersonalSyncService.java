package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.RrhhClient.PersonalRrhh;
import com.tdem.logistica.modules.identidad.dto.SyncResultadoDto;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.RolRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sincroniza la identidad desde RRHH-SYSTEM (DEC-L002/DEC-L024), en solo
 * lectura y siempre en este sentido (RRHH -> Logistica):
 * <ul>
 *   <li>Upsert de {@code usuario} por DNI ({@code employee_external_id}). Si
 *       no existe por DNI pero si por correo (alta MANUAL previa), esa fila
 *       pasa a RRHH_SYNC sin duplicarse.</li>
 *   <li>CECO del trabajador: se crea/actualiza por empresa + numero y se
 *       agrega a {@code usuario_ceco}.</li>
 *   <li>Un usuario nuevo nace con rol SOLICITANTE en su empresa; los demas
 *       roles los asigna ADMIN (RRHH no sabe de roles de Logistica).</li>
 *   <li>Quien deja de aparecer como activo en RRHH se desactiva (RF-041). Si
 *       RRHH devuelve una lista vacia no se desactiva a nadie: es mas probable
 *       un error de configuracion que un cese masivo.</li>
 * </ul>
 * Nunca toca contrasenas ni usuarios MANUAL que RRHH no conoce.
 */
@Service
public class PersonalSyncService {

    private static final Logger log = LoggerFactory.getLogger(PersonalSyncService.class);
    public static final String FUENTE_RRHH = "RRHH_SYNC";

    private final RrhhClient rrhh;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final CecoRepository cecoRepository;
    private final RolRepository rolRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReentrantLock enCurso = new ReentrantLock();
    private volatile SyncResultadoDto ultimo;

    public PersonalSyncService(RrhhClient rrhh, UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository,
                               CecoRepository cecoRepository, RolRepository rolRepository, JdbcTemplate jdbc,
                               TransactionTemplate tx) {
        this.rrhh = rrhh;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.cecoRepository = cecoRepository;
        this.rolRepository = rolRepository;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public boolean configurado() {
        return rrhh.configurado();
    }

    public SyncResultadoDto ultimoResultado() {
        return ultimo;
    }

    /**
     * La llamada HTTP a RRHH va fuera de la transaccion; la escritura, en una
     * sola transaccion (o se aplica todo o nada).
     */
    public SyncResultadoDto sincronizar() {
        if (!enCurso.tryLock()) {
            throw ApiException.badRequest("Ya hay una sincronizacion en curso.");
        }
        try {
            List<PersonalRrhh> personal = rrhh.personalActivo();
            SyncResultadoDto r = tx.execute(estado -> aplicar(personal));
            ultimo = r;
            log.info("Sync RRHH: {}", r);
            return r;
        } finally {
            enCurso.unlock();
        }
    }

    private SyncResultadoDto aplicar(List<PersonalRrhh> personal) {
        Instant ahora = Instant.now();
        Map<String, Empresa> empresas = empresaRepository.findAll().stream()
                .collect(Collectors.toMap(Empresa::getCode, Function.identity()));
        UUID rolSolicitante = rolRepository.findByCode(Roles.SOLICITANTE)
                .orElseThrow(() -> new IllegalStateException("Falta el rol SOLICITANTE")).getRoleId();

        List<String> avisos = new ArrayList<>();
        Set<String> dnisActivos = new HashSet<>();
        int creados = 0;
        int actualizados = 0;
        int cecosCreados = 0;

        for (PersonalRrhh p : personal) {
            String dni = limpiar(p.dni());
            String correo = limpiar(p.correo()).toLowerCase(Locale.ROOT);
            String nombre = limpiar(p.nombreCompleto());
            if (dni.isEmpty() || correo.isEmpty() || nombre.isEmpty()) {
                avisos.add("Registro sin DNI, correo o nombre: omitido (" + (dni.isEmpty() ? correo : dni) + ").");
                continue;
            }
            dnisActivos.add(dni);
            Empresa empresa = empresas.get(limpiar(p.empresa()));
            if (empresa == null) {
                avisos.add(nombre + ": la empresa \"" + p.empresa() + "\" no existe en Logistica.");
                continue;
            }

            Ceco ceco = null;
            if (p.ceco() != null && !limpiar(p.ceco().numero()).isEmpty()) {
                String codigo = limpiar(p.ceco().numero());
                String nombreCeco = limpiar(p.ceco().nombre()).isEmpty() ? codigo : limpiar(p.ceco().nombre());
                ceco = cecoRepository.findByEmpresa_CompanyIdAndCode(empresa.getCompanyId(), codigo).orElse(null);
                if (ceco == null) {
                    ceco = new Ceco();
                    ceco.setCecoId(UUID.randomUUID());
                    ceco.setEmpresa(empresa);
                    ceco.setCode(codigo);
                    ceco.setName(nombreCeco);
                    ceco.setActive(true);
                    cecoRepository.save(ceco);
                    cecosCreados++;
                } else if (!ceco.getName().equals(nombreCeco) || !ceco.isActive()) {
                    ceco.setName(nombreCeco);
                    ceco.setActive(true);
                    cecoRepository.save(ceco);
                }
            }

            Usuario u = usuarioRepository.findByEmployeeExternalId(dni).orElse(null);
            boolean nuevo = false;
            if (u == null) {
                u = usuarioRepository.findByCorporateEmailIgnoreCase(correo).orElse(null);
                if (u != null && u.getEmployeeExternalId() != null && !u.getEmployeeExternalId().equals(dni)) {
                    avisos.add(nombre + ": el correo " + correo + " ya pertenece a otro DNI en Logistica; omitido.");
                    continue;
                }
            }
            if (u == null) {
                u = new Usuario();
                u.setUserId(UUID.randomUUID());
                u.setCorporateEmail(correo);
                nuevo = true;
            } else if (!u.getCorporateEmail().equalsIgnoreCase(correo)) {
                Optional<Usuario> duenoCorreo = usuarioRepository.findByCorporateEmailIgnoreCase(correo);
                if (duenoCorreo.isPresent() && !duenoCorreo.get().getUserId().equals(u.getUserId())) {
                    avisos.add(nombre + ": no se cambio el correo a " + correo + " (ya lo usa otro usuario).");
                } else {
                    u.setCorporateEmail(correo);
                }
            }
            u.setEmployeeExternalId(dni);
            u.setDisplayName(nombre);
            u.setEmpresa(empresa);
            u.setCargo(p.cargo() != null && !p.cargo().isBlank() ? p.cargo().trim() : null);
            u.setFuente(FUENTE_RRHH);
            u.setActive(true);
            u.setSourceSyncedAt(ahora);
            usuarioRepository.saveAndFlush(u);
            if (nuevo) {
                creados++;
            } else {
                actualizados++;
            }

            if (ceco != null) {
                jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?) on conflict do nothing",
                        u.getUserId(), ceco.getCecoId());
            }
            Integer roles = jdbc.queryForObject("select count(*) from usuario_rol where user_id = ?", Integer.class, u.getUserId());
            if (roles == null || roles == 0) {
                jdbc.update("insert into usuario_rol (user_id, role_id, company_id) values (?, ?, ?) on conflict do nothing",
                        u.getUserId(), rolSolicitante, empresa.getCompanyId());
            }
        }

        int desactivados = 0;
        if (dnisActivos.isEmpty()) {
            avisos.add("RR.HH. no devolvio personal activo: no se desactivo a nadie.");
        } else {
            for (Usuario u : usuarioRepository.findByFuenteAndActiveTrue(FUENTE_RRHH)) {
                if (u.getEmployeeExternalId() == null || !dnisActivos.contains(u.getEmployeeExternalId())) {
                    u.setActive(false);
                    u.setSourceSyncedAt(ahora);
                    usuarioRepository.save(u);
                    desactivados++;
                }
            }
        }
        return new SyncResultadoDto(ahora, personal.size(), creados, actualizados, desactivados, cecosCreados, avisos);
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }
}
