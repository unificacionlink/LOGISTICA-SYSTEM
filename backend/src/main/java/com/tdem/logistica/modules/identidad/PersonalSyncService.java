package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.CodigoCeco;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.RrhhClient.CecoCatalogoRrhh;
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
 *   <li>Catalogo de CECO (DEC-L031): todos los CECO activos de RRHH se
 *       crean/actualizan por empresa + numero, tengan o no personal. En cada
 *       empresa que RRHH cubre, el CECO que ya no viene se desactiva (asi
 *       salen los de relleno ADM/OPS); no se borra, por el historial.</li>
 *   <li>CECO del trabajador: se agrega a {@code usuario_ceco}.</li>
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
            List<CecoCatalogoRrhh> cecos = rrhh.cecosActivos();
            SyncResultadoDto r = tx.execute(estado -> aplicar(personal, cecos));
            ultimo = r;
            log.info("Sync RRHH: {}", r);
            return r;
        } finally {
            enCurso.unlock();
        }
    }

    private SyncResultadoDto aplicar(List<PersonalRrhh> personal, List<CecoCatalogoRrhh> catalogo) {
        Instant ahora = Instant.now();
        Map<String, Empresa> empresas = empresaRepository.findAll().stream()
                .collect(Collectors.toMap(Empresa::getCode, Function.identity()));
        UUID rolSolicitante = rolRepository.findByCode(Roles.SOLICITANTE)
                .orElseThrow(() -> new IllegalStateException("Falta el rol SOLICITANTE")).getRoleId();

        List<String> avisos = new ArrayList<>();
        Set<String> dnisActivos = new HashSet<>();
        int creados = 0;
        int actualizados = 0;
        int[] cecosCreados = {0};

        // Catalogo completo de CECO (DEC-L031): codigos vigentes por empresa.
        // DEC-L032: 0102 y 102 son el mismo CECO; manda el nombre del que ya venia con 4 digitos.
        Map<UUID, Set<String>> vigentesPorEmpresa = new HashMap<>();
        Map<String, String> nombreCatalogo = new HashMap<>();
        // Catalogo vacio: igual que con el personal, se asume un error y no se desactiva ningun CECO.
        boolean catalogoValido = catalogo != null && !catalogo.isEmpty();
        if (catalogo == null) {
            avisos.add("RR.HH. aun no publica el catalogo de CECO: solo se traen los CECO del personal.");
        } else if (catalogo.isEmpty()) {
            avisos.add("RR.HH. no devolvio CECOs: no se desactivo ningun CECO.");
        } else {
            Map<String, Empresa> empresaDe = new LinkedHashMap<>();
            Set<String> nombreCanonico = new HashSet<>();
            for (CecoCatalogoRrhh c : catalogo) {
                String codigo = CodigoCeco.normalizar(c.numero());
                if (codigo.isEmpty()) {
                    continue;
                }
                Empresa empresa = empresas.get(limpiar(c.empresa()));
                if (empresa == null) {
                    avisos.add("CECO " + codigo + ": la empresa \"" + c.empresa() + "\" no existe en Logistica.");
                    continue;
                }
                String clave = empresa.getCompanyId() + "|" + codigo;
                empresaDe.put(clave, empresa);
                boolean canonico = CodigoCeco.esCanonico(c.numero());
                if (!nombreCatalogo.containsKey(clave) || (canonico && !nombreCanonico.contains(clave))) {
                    nombreCatalogo.put(clave, c.nombre());
                }
                if (canonico) {
                    nombreCanonico.add(clave);
                }
            }
            for (Map.Entry<String, Empresa> e : empresaDe.entrySet()) {
                String codigo = e.getKey().substring(e.getKey().indexOf('|') + 1);
                upsertCeco(e.getValue(), codigo, nombreCatalogo.get(e.getKey()), cecosCreados);
                vigentesPorEmpresa.computeIfAbsent(e.getValue().getCompanyId(), k -> new HashSet<>()).add(codigo);
            }
        }

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
                String codigo = CodigoCeco.normalizar(p.ceco().numero());
                String nombreCeco = nombreCatalogo.getOrDefault(empresa.getCompanyId() + "|" + codigo, p.ceco().nombre());
                ceco = upsertCeco(empresa, codigo, nombreCeco, cecosCreados);
                if (catalogoValido) {
                    vigentesPorEmpresa.computeIfAbsent(empresa.getCompanyId(), k -> new HashSet<>()).add(codigo);
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

        // Solo en empresas que RRHH cubre (vinieron en el catalogo); nunca las de alta manual.
        Set<UUID> manuales = new HashSet<>(jdbc.queryForList(
                "select company_id from identidad_manual_empresa_habilitada", UUID.class));
        int cecosDesactivados = 0;
        for (Map.Entry<UUID, Set<String>> e : vigentesPorEmpresa.entrySet()) {
            if (manuales.contains(e.getKey())) {
                continue;
            }
            for (Ceco c : cecoRepository.findByEmpresa_CompanyIdAndActiveTrue(e.getKey())) {
                if (!e.getValue().contains(c.getCode())) {
                    c.setActive(false);
                    cecoRepository.save(c);
                    cecosDesactivados++;
                }
            }
        }
        return new SyncResultadoDto(ahora, personal.size(), creados, actualizados, desactivados,
                cecosCreados[0], cecosDesactivados, avisos);
    }

    /** Crea o actualiza (y reactiva) el CECO por empresa + numero. */
    private Ceco upsertCeco(Empresa empresa, String codigo, String nombre, int[] creados) {
        String nombreCeco = limpiar(nombre).isEmpty() ? codigo : CodigoCeco.nombre(nombre); // DEC-L033
        Ceco ceco = cecoRepository.findByEmpresa_CompanyIdAndCode(empresa.getCompanyId(), codigo).orElse(null);
        if (ceco == null) {
            ceco = new Ceco();
            ceco.setCecoId(UUID.randomUUID());
            ceco.setEmpresa(empresa);
            ceco.setCode(codigo);
            ceco.setName(nombreCeco);
            ceco.setActive(true);
            cecoRepository.save(ceco);
            creados[0]++;
        } else if (!ceco.getName().equals(nombreCeco) || !ceco.isActive()) {
            ceco.setName(nombreCeco);
            ceco.setActive(true);
            cecoRepository.save(ceco);
        }
        return ceco;
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }
}
