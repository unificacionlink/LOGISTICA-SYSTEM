package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.CodigoCeco;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.RrhhClient.CecoCatalogoRrhh;
import com.tdem.logistica.modules.identidad.RrhhClient.PersonalRrhh;
import com.tdem.logistica.modules.identidad.dto.SyncResultadoDto;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sincroniza el personal desde RRHH-SYSTEM (DEC-L002/DEC-L024/DEC-L041), en
 * solo lectura y siempre en este sentido (RRHH -> Logistica):
 * <ul>
 *   <li>Upsert del padron {@code personal} por empresa + DNI: todo el personal
 *       activo, tenga o no correo. <b>No crea cuentas</b>: la cuenta la crea
 *       ADMIN con "Dar acceso" (DEC-L041).</li>
 *   <li>Catalogo de CECO (DEC-L031): todos los CECO activos de RRHH se
 *       crean/actualizan por empresa + numero, tengan o no personal. En cada
 *       empresa que RRHH cubre, el CECO que ya no viene se desactiva (asi
 *       salen los de relleno ADM/OPS); no se borra, por el historial.</li>
 *   <li>Si la persona ya tiene cuenta (por DNI, o una alta MANUAL previa sin
 *       DNI con su mismo correo), se vincula y se le actualizan nombre, cargo,
 *       empresa y correo; su CECO se agrega a {@code usuario_ceco}. Los roles
 *       nunca se tocan.</li>
 *   <li>Quien deja de aparecer como activo en RRHH cesa en el padron y su
 *       cuenta se desactiva (RF-041). Si RRHH devuelve una lista vacia no se
 *       desactiva a nadie: es mas probable un error de configuracion que un
 *       cese masivo.</li>
 * </ul>
 * Nunca toca contrasenas ni personal MANUAL. Todo se lee de una vez y se
 * escribe por lotes: la BD esta lejos y RRHH trae cientos de personas.
 */
@Service
public class PersonalSyncService {

    private static final Logger log = LoggerFactory.getLogger(PersonalSyncService.class);
    public static final String FUENTE_RRHH = "RRHH_SYNC";

    private final RrhhClient rrhh;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final CecoRepository cecoRepository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReentrantLock enCurso = new ReentrantLock();
    private volatile SyncResultadoDto ultimo;

    public PersonalSyncService(RrhhClient rrhh, UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository,
                               CecoRepository cecoRepository, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.rrhh = rrhh;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.cecoRepository = cecoRepository;
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

    /** Fila del padron, leida una sola vez. */
    private record FilaPadron(UUID id, UUID companyId, String dni, String nombre, String email, String cargo,
                              UUID cecoId, String fuente, boolean activo, UUID userId) {
    }

    private SyncResultadoDto aplicar(List<PersonalRrhh> personal, List<CecoCatalogoRrhh> catalogo) {
        Instant ahora = Instant.now();
        Timestamp marca = Timestamp.from(ahora);
        Map<String, Empresa> empresas = empresaRepository.findAll().stream()
                .collect(Collectors.toMap(Empresa::getCode, Function.identity()));
        Map<String, Ceco> cecos = new HashMap<>();
        for (Ceco c : cecoRepository.findAllConEmpresa()) {
            cecos.put(clave(c.getEmpresa().getCompanyId(), c.getCode()), c);
        }

        List<String> avisos = new ArrayList<>();
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
                String clave = clave(empresa.getCompanyId(), codigo);
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
                upsertCeco(cecos, e.getValue(), codigo, nombreCatalogo.get(e.getKey()), cecosCreados);
                vigentesPorEmpresa.computeIfAbsent(e.getValue().getCompanyId(), k -> new HashSet<>()).add(codigo);
            }
        }

        // Padron y cuentas actuales, en dos consultas.
        Map<String, FilaPadron> padron = new HashMap<>();
        jdbc.query("""
                select personal_id, company_id, dni, full_name, email, position, ceco_id, fuente, active, user_id
                  from personal""", rs -> {
            FilaPadron f = new FilaPadron(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8),
                    rs.getBoolean(9), rs.getObject(10, UUID.class));
            padron.put(clave(f.companyId(), f.dni()), f);
        });
        List<Usuario> usuarios = usuarioRepository.findAllConEmpresa();
        Map<UUID, Usuario> usuarioPorId = new HashMap<>();
        Map<String, Usuario> usuarioPorDni = new HashMap<>();
        Map<String, Usuario> usuarioPorCorreo = new HashMap<>();
        for (Usuario u : usuarios) {
            usuarioPorId.put(u.getUserId(), u);
            if (u.getEmployeeExternalId() != null) {
                usuarioPorDni.put(u.getEmployeeExternalId(), u);
            }
            usuarioPorCorreo.put(u.getCorporateEmail().toLowerCase(Locale.ROOT), u);
        }

        Set<String> vigentes = new HashSet<>();
        Set<String> dnisActivos = new HashSet<>();
        List<Object[]> altas = new ArrayList<>();
        List<Object[]> cambios = new ArrayList<>();
        List<Object[]> cecosDeCuentas = new ArrayList<>();
        int creados = 0;
        int actualizados = 0;

        for (PersonalRrhh p : personal) {
            String dni = limpiar(p.dni());
            String correo = limpiar(p.correo()).toLowerCase(Locale.ROOT);
            String email = correo.isEmpty() ? null : correo;
            String nombre = limpiar(p.nombreCompleto());
            if (dni.isEmpty() || nombre.isEmpty()) {
                avisos.add("Registro sin DNI o nombre: omitido (" + (dni.isEmpty() ? nombre : dni) + ").");
                continue;
            }
            Empresa empresa = empresas.get(limpiar(p.empresa()));
            if (empresa == null) {
                avisos.add(nombre + ": la empresa \"" + p.empresa() + "\" no existe en Logistica.");
                continue;
            }
            String clave = clave(empresa.getCompanyId(), dni);
            if (!vigentes.add(clave)) {
                continue; // repetido en la respuesta de RR.HH.
            }
            dnisActivos.add(dni);

            Ceco ceco = null;
            if (p.ceco() != null && !limpiar(p.ceco().numero()).isEmpty()) {
                String codigo = CodigoCeco.normalizar(p.ceco().numero());
                String nombreCeco = nombreCatalogo.getOrDefault(clave(empresa.getCompanyId(), codigo), p.ceco().nombre());
                ceco = upsertCeco(cecos, empresa, codigo, nombreCeco, cecosCreados);
                if (catalogoValido) {
                    vigentesPorEmpresa.computeIfAbsent(empresa.getCompanyId(), k -> new HashSet<>()).add(codigo);
                }
            }
            UUID cecoId = ceco != null ? ceco.getCecoId() : null;
            String cargo = p.cargo() != null && !p.cargo().isBlank() ? p.cargo().trim() : null;

            // Cuenta de la persona, si ya tiene: la vinculada, por DNI o una alta MANUAL previa sin DNI con su correo.
            FilaPadron f = padron.get(clave);
            Usuario u = f != null && f.userId() != null ? usuarioPorId.get(f.userId()) : null;
            if (u == null) {
                u = usuarioPorDni.get(dni);
            }
            if (u == null && email != null) {
                Usuario porCorreo = usuarioPorCorreo.get(email);
                if (porCorreo != null && porCorreo.getEmployeeExternalId() == null) {
                    u = porCorreo;
                } else if (porCorreo != null) {
                    avisos.add(nombre + ": el correo " + email + " ya pertenece a otro DNI en Logistica.");
                }
            }
            UUID userId = u != null ? u.getUserId() : null;

            if (f == null) {
                altas.add(new Object[] {UUID.randomUUID(), empresa.getCompanyId(), dni, nombre, email, cargo, cecoId,
                        userId, marca});
                creados++;
            } else {
                cambios.add(new Object[] {nombre, email, cargo, cecoId, userId, marca, f.id()});
                boolean cambia = !nombre.equals(f.nombre()) || !Objects.equals(email, f.email())
                        || !Objects.equals(cargo, f.cargo()) || !Objects.equals(cecoId, f.cecoId()) || !f.activo()
                        || !Objects.equals(userId, f.userId()) || !FUENTE_RRHH.equals(f.fuente());
                if (cambia) {
                    actualizados++;
                }
            }

            if (u != null) {
                actualizarCuenta(u, dni, nombre, email, empresa, cargo, ahora, usuarioPorCorreo, avisos);
                if (cecoId != null) {
                    // Solo agrega el CECO de planilla: no quita los asignados a mano (DEC-L029).
                    cecosDeCuentas.add(new Object[] {u.getUserId(), cecoId});
                }
            }
        }

        // CECOs nuevos y cuentas actualizadas, escritos antes de las referencias por JDBC.
        usuarioRepository.flush();
        jdbc.batchUpdate("""
                insert into personal (personal_id, company_id, dni, full_name, email, position, ceco_id, fuente, active,
                                      user_id, source_synced_at)
                values (?, ?, ?, ?, ?, ?, ?, 'RRHH_SYNC', true, ?, ?)""", altas);
        jdbc.batchUpdate("""
                update personal
                   set full_name = ?, email = ?, position = ?, ceco_id = ?, user_id = ?, fuente = 'RRHH_SYNC',
                       active = true, source_synced_at = ?
                 where personal_id = ?""", cambios);
        jdbc.batchUpdate("insert into usuario_ceco (user_id, ceco_id) values (?, ?) on conflict do nothing", cecosDeCuentas);

        int desactivados = 0;
        if (dnisActivos.isEmpty()) {
            avisos.add("RR.HH. no devolvio personal activo: no se desactivo a nadie.");
        } else {
            List<Object[]> ceses = new ArrayList<>();
            Set<UUID> cuentasDeCesados = new HashSet<>();
            for (Map.Entry<String, FilaPadron> e : padron.entrySet()) {
                FilaPadron f = e.getValue();
                if (FUENTE_RRHH.equals(f.fuente()) && f.activo() && !vigentes.contains(e.getKey())) {
                    ceses.add(new Object[] {marca, f.id()});
                    if (f.userId() != null) {
                        cuentasDeCesados.add(f.userId());
                    }
                    desactivados++;
                }
            }
            jdbc.batchUpdate("update personal set active = false, source_synced_at = ? where personal_id = ?", ceses);
            for (Usuario u : usuarios) {
                if (FUENTE_RRHH.equals(u.getFuente()) && u.isActive()
                        && (u.getEmployeeExternalId() == null || !dnisActivos.contains(u.getEmployeeExternalId()))) {
                    u.setActive(false);
                    u.setSourceSyncedAt(ahora);
                    if (!cuentasDeCesados.contains(u.getUserId())) {
                        desactivados++;
                    }
                }
            }
            usuarioRepository.flush();
        }

        // Solo en empresas que RRHH cubre (vinieron en el catalogo); nunca las de alta manual.
        Set<UUID> manuales = new HashSet<>(jdbc.queryForList(
                "select company_id from identidad_manual_empresa_habilitada", UUID.class));
        int cecosDesactivados = 0;
        for (Map.Entry<UUID, Set<String>> e : vigentesPorEmpresa.entrySet()) {
            if (manuales.contains(e.getKey())) {
                continue;
            }
            for (Ceco c : cecos.values()) {
                if (c.isActive() && c.getEmpresa().getCompanyId().equals(e.getKey()) && !e.getValue().contains(c.getCode())) {
                    c.setActive(false);
                    cecoRepository.save(c);
                    cecosDesactivados++;
                }
            }
        }
        return new SyncResultadoDto(ahora, personal.size(), creados, actualizados, desactivados,
                cecosCreados[0], cecosDesactivados, avisos);
    }

    /** Datos de RR.HH. en la cuenta ya existente de la persona; nunca toca roles ni contrasena. */
    private static void actualizarCuenta(Usuario u, String dni, String nombre, String email, Empresa empresa, String cargo,
                                         Instant ahora, Map<String, Usuario> porCorreo, List<String> avisos) {
        if (email != null && !u.getCorporateEmail().equalsIgnoreCase(email)) {
            Usuario dueno = porCorreo.get(email);
            if (dueno != null && !dueno.getUserId().equals(u.getUserId())) {
                avisos.add(nombre + ": no se cambio el correo a " + email + " (ya lo usa otro usuario).");
            } else {
                porCorreo.remove(u.getCorporateEmail().toLowerCase(Locale.ROOT));
                u.setCorporateEmail(email);
                porCorreo.put(email, u);
            }
        }
        u.setEmployeeExternalId(dni);
        u.setDisplayName(nombre);
        u.setEmpresa(empresa);
        u.setCargo(cargo);
        u.setFuente(FUENTE_RRHH);
        u.setActive(true);
        u.setSourceSyncedAt(ahora);
    }

    /** Crea o actualiza (y reactiva) el CECO por empresa + numero, sobre el mapa ya leido. */
    private Ceco upsertCeco(Map<String, Ceco> cecos, Empresa empresa, String codigo, String nombre, int[] creados) {
        String nombreCeco = limpiar(nombre).isEmpty() ? codigo : CodigoCeco.nombre(nombre); // DEC-L033
        String clave = clave(empresa.getCompanyId(), codigo);
        Ceco ceco = cecos.get(clave);
        if (ceco == null) {
            ceco = new Ceco();
            ceco.setCecoId(UUID.randomUUID());
            ceco.setEmpresa(empresa);
            ceco.setCode(codigo);
            ceco.setName(nombreCeco);
            ceco.setActive(true);
            cecoRepository.save(ceco);
            cecos.put(clave, ceco);
            creados[0]++;
        } else if (!ceco.getName().equals(nombreCeco) || !ceco.isActive()) {
            ceco.setName(nombreCeco);
            ceco.setActive(true);
            cecoRepository.save(ceco);
        }
        return ceco;
    }

    private static String clave(UUID companyId, String codigo) {
        return companyId + "|" + codigo;
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }
}
