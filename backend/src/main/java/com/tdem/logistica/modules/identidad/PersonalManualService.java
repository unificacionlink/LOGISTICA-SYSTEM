package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.CodigoCeco;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Rol;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.dto.PersonalCargaDto;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.RolRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.AccesoVigente;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Padron de personal de las empresas que RR.HH. aun no cubre (DEC-L028):
 * GOYDEL y METRIN ({@code identidad_manual_empresa_habilitada}). Un mismo
 * Excel da altas, edita datos y registra ceses (Activo = NO). Igual que la
 * carga masiva de almacen: validar no escribe nada y confirmar vuelve a
 * validar y aplica todo en una transaccion (o todas las filas o ninguna).
 *
 * El DNI identifica a la persona: si mas adelante RR.HH. la incorpora, el
 * sync la encuentra por DNI y la convierte a RRHH_SYNC sin duplicarla.
 */
@Service
@Transactional(readOnly = true)
public class PersonalManualService {

    static final String HOJA = "PERSONAL";
    static final String COL_EMPRESA = "Empresa";
    static final String COL_DNI = "DNI";
    static final String COL_NOMBRE = "Nombre completo";
    static final String COL_CORREO = "Correo";
    static final String COL_CARGO = "Cargo";
    static final String COL_CECO = "CECO codigo";
    static final String COL_CECO_NOMBRE = "CECO nombre";
    static final String COL_ROL = "Rol inicial";
    static final String COL_ACTIVO = "Activo";
    static final List<String> COLUMNAS = List.of(COL_EMPRESA, COL_DNI, COL_NOMBRE, COL_CORREO, COL_CARGO, COL_CECO,
            COL_CECO_NOMBRE, COL_ROL, COL_ACTIVO);

    /** ADMIN no se da por Excel: se asigna a mano en la ficha del usuario. */
    static final List<String> ROLES_INICIALES = List.of(Roles.SOLICITANTE, Roles.APROBADOR, Roles.LOGISTICA);
    private static final int MAX_FILAS = 2000;
    private static final Pattern DNI = Pattern.compile("^[0-9A-Z]{8,12}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final CecoRepository cecoRepository;
    private final RolRepository rolRepository;
    private final AccesoVigente accesoVigente;
    private final JdbcTemplate jdbc;

    public PersonalManualService(UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository,
                                 CecoRepository cecoRepository, RolRepository rolRepository,
                                 AccesoVigente accesoVigente, JdbcTemplate jdbc) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.cecoRepository = cecoRepository;
        this.rolRepository = rolRepository;
        this.accesoVigente = accesoVigente;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- plantilla

    /** Plantilla con el personal manual actual: se edita y se vuelve a subir. */
    public byte[] plantilla(UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Contexto ctx = contexto();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet(HOJA);
            CellStyle cabecera = wb.createCellStyle();
            Font negrita = wb.createFont();
            negrita.setBold(true);
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setBorderBottom(BorderStyle.THIN);
            // DNI y CECO como texto: si Excel los toma como numero se pierden los ceros a la izquierda.
            CellStyle texto = wb.createCellStyle();
            texto.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("@"));

            Row h = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(COLUMNAS.get(c));
                cell.setCellStyle(cabecera);
            }
            hoja.setDefaultColumnStyle(COLUMNAS.indexOf(COL_DNI), texto);
            hoja.setDefaultColumnStyle(COLUMNAS.indexOf(COL_CECO), texto);

            int fila = 1;
            List<Usuario> manuales = ctx.usuarios.stream()
                    .filter(u -> "MANUAL".equals(u.getFuente()) && u.getEmpresa() != null
                            && ctx.empresas.containsKey(u.getEmpresa().getCode()))
                    .sorted(Comparator.comparing((Usuario u) -> u.getEmpresa().getCode())
                            .thenComparing(Usuario::getDisplayName))
                    .toList();
            for (Usuario u : manuales) {
                Ceco ceco = ctx.cecoPlanilla(u);
                Row row = hoja.createRow(fila++);
                String[] valores = {u.getEmpresa().getCode(), nvl(u.getEmployeeExternalId()), u.getDisplayName(),
                        u.getCorporateEmail(), nvl(u.getCargo()), ceco != null ? ceco.getCode() : "",
                        ceco != null ? ceco.getName() : "", "", u.isActive() ? "SI" : "NO"};
                for (int c = 0; c < valores.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(valores[c]);
                    if (c == COLUMNAS.indexOf(COL_DNI) || c == COLUMNAS.indexOf(COL_CECO)) {
                        cell.setCellStyle(texto);
                    }
                }
            }

            DataValidationHelper dv = hoja.getDataValidationHelper();
            lista(hoja, dv, COL_EMPRESA, ctx.empresas.keySet().stream().sorted().toArray(String[]::new));
            lista(hoja, dv, COL_ROL, ROLES_INICIALES.toArray(String[]::new));
            lista(hoja, dv, COL_ACTIVO, new String[] {"SI", "NO"});
            hoja.createFreezePane(0, 1);
            int[] anchos = {13, 12, 36, 32, 26, 13, 30, 14, 8};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void lista(Sheet hoja, DataValidationHelper dv, String columna, String[] valores) {
        if (valores.length == 0) {
            return;
        }
        int c = COLUMNAS.indexOf(columna);
        DataValidation v = dv.createValidation(dv.createExplicitListConstraint(valores),
                new CellRangeAddressList(1, MAX_FILAS, c, c));
        v.setShowErrorBox(true);
        hoja.addValidationData(v);
    }

    // ---------------------------------------------------------------- validar / aplicar

    public PersonalCargaDto validar(MultipartFile archivo, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        return analizar(archivo, principal.userId()).resultado(false);
    }

    @Transactional
    public PersonalCargaDto confirmar(MultipartFile archivo, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Analisis a = analizar(archivo, principal.userId());
        if (a.conError() > 0) {
            throw ApiException.badRequest("El archivo tiene " + a.conError()
                    + " fila(s) con error. Corrigelas y vuelve a validar; no se aplico ningun cambio.");
        }
        if (a.planes.stream().allMatch(p -> "SIN_CAMBIOS".equals(p.accion))) {
            throw ApiException.badRequest("El archivo no tiene cambios que aplicar.");
        }
        aplicar(a);
        accesoVigente.invalidarTodo();
        return a.resultado(true);
    }

    private void aplicar(Analisis a) {
        Map<String, Rol> roles = new HashMap<>();
        rolRepository.findAll().stream().filter(Rol::isActive).forEach(r -> roles.put(r.getCode(), r));
        Map<String, Ceco> cecos = new HashMap<>(a.ctx.cecos);

        for (Plan p : a.planes) {
            if ("SIN_CAMBIOS".equals(p.accion)) {
                continue;
            }
            Ceco ceco = null;
            if (p.cecoCodigo != null) {
                ceco = cecos.computeIfAbsent(clave(p.empresa, p.cecoCodigo), k -> {
                    Ceco c = new Ceco();
                    c.setCecoId(UUID.randomUUID());
                    c.setEmpresa(p.empresa);
                    c.setCode(p.cecoCodigo);
                    c.setName(p.cecoNombre);
                    c.setActive(true);
                    return cecoRepository.saveAndFlush(c);
                });
            }

            Usuario u = p.existente;
            boolean nuevo = u == null;
            if (nuevo) {
                u = new Usuario();
                u.setUserId(UUID.randomUUID());
                u.setEmpresa(p.empresa);
                u.setFuente("MANUAL");
            }
            u.setDisplayName(p.nombre);
            u.setCorporateEmail(p.correo);
            u.setEmployeeExternalId(p.dni);
            if (p.cargo != null) {
                u.setCargo(p.cargo);
            }
            u.setActive(p.activo);
            usuarioRepository.saveAndFlush(u);

            if (nuevo) {
                jdbc.update("insert into usuario_rol (user_id, role_id, company_id) values (?, ?, ?)",
                        u.getUserId(), roles.get(p.rol).getRoleId(), p.empresa.getCompanyId());
            }
            // Solo agrega el CECO de planilla: no quita los que ADMIN asigno a mano (DEC-L029).
            if (ceco != null && !a.ctx.tieneCeco(u.getUserId(), ceco.getCecoId())) {
                jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?) on conflict do nothing",
                        u.getUserId(), ceco.getCecoId());
            }
        }
    }

    /** Lo que se hara con una fila valida. cargo/cecoCodigo null = no se cambia. */
    private record Plan(Usuario existente, Empresa empresa, String dni, String nombre, String correo, String cargo,
                        String cecoCodigo, String cecoNombre, String rol, boolean activo, String accion) {
    }

    /** Datos actuales, leidos una sola vez (la BD esta lejos). */
    private static final class Contexto {
        final Map<String, Empresa> empresas = new HashMap<>();
        final List<Usuario> usuarios = new ArrayList<>();
        final Map<String, Usuario> porDni = new HashMap<>();
        final Map<String, Usuario> porCorreo = new HashMap<>();
        final Map<String, Ceco> cecos = new HashMap<>();
        /** CECOs asignados por usuario (DEC-L029: puede tener varios, de cualquier empresa). */
        final Map<UUID, List<Ceco>> cecosDe = new HashMap<>();

        /** El CECO "de planilla": el primero asignado de su propia empresa. */
        Ceco cecoPlanilla(Usuario u) {
            List<Ceco> asignados = cecosDe.getOrDefault(u.getUserId(), List.of());
            return asignados.stream()
                    .filter(c -> u.getEmpresa() != null && c.getEmpresa().getCompanyId().equals(u.getEmpresa().getCompanyId()))
                    .findFirst().orElse(null);
        }

        boolean tieneCeco(UUID userId, UUID cecoId) {
            return cecosDe.getOrDefault(userId, List.of()).stream().anyMatch(c -> c.getCecoId().equals(cecoId));
        }
    }

    private Contexto contexto() {
        Contexto ctx = new Contexto();
        Set<UUID> manuales = new HashSet<>(jdbc.queryForList(
                "select company_id from identidad_manual_empresa_habilitada", UUID.class));
        empresaRepository.findAll().stream()
                .filter(e -> manuales.contains(e.getCompanyId()))
                .forEach(e -> ctx.empresas.put(e.getCode().toUpperCase(Locale.ROOT), e));
        for (Usuario u : usuarioRepository.findAllConEmpresa()) {
            ctx.usuarios.add(u);
            if (u.getEmployeeExternalId() != null) {
                ctx.porDni.put(u.getEmployeeExternalId().toUpperCase(Locale.ROOT), u);
            }
            ctx.porCorreo.put(u.getCorporateEmail().toLowerCase(Locale.ROOT), u);
        }
        Map<UUID, Ceco> porId = new HashMap<>();
        for (Ceco c : cecoRepository.findAllConEmpresa()) {
            ctx.cecos.put(clave(c.getEmpresa(), c.getCode()), c);
            porId.put(c.getCecoId(), c);
        }
        jdbc.query("select user_id, ceco_id from usuario_ceco", rs -> {
            ctx.cecosDe.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(porId.get(rs.getObject(2, UUID.class)));
        });
        return ctx;
    }

    private final class Analisis {
        final String archivo;
        final Contexto ctx;
        final List<PersonalCargaDto.Fila> filas = new ArrayList<>();
        final List<Plan> planes = new ArrayList<>();

        Analisis(String archivo, Contexto ctx) {
            this.archivo = archivo;
            this.ctx = ctx;
        }

        int conError() {
            return (int) filas.stream().filter(f -> f.error() != null).count();
        }

        PersonalCargaDto resultado(boolean aplicada) {
            return new PersonalCargaDto(archivo, contar("NUEVO"), contar("ACTUALIZADO"), contar("DESACTIVADO"),
                    contar("SIN_CAMBIOS"), conError(), aplicada, filas);
        }

        private int contar(String accion) {
            return (int) planes.stream().filter(p -> accion.equals(p.accion)).count();
        }
    }

    private Analisis analizar(MultipartFile archivo, UUID yo) {
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "personal.xlsx";
        Analisis a = new Analisis(nombre, contexto());
        try (InputStream in = archivo.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet hoja = wb.getSheet(HOJA) != null ? wb.getSheet(HOJA) : wb.getSheetAt(0);
            Map<String, Integer> col = columnas(hoja.getRow(hoja.getFirstRowNum()));
            for (String requerida : List.of(COL_EMPRESA, COL_DNI, COL_NOMBRE, COL_CORREO)) {
                if (!col.containsKey(norm(requerida))) {
                    throw ApiException.badRequest("El archivo no tiene el formato de la plantilla (falta la columna \""
                            + requerida + "\"). Descarga la plantilla desde el sistema.");
                }
            }
            if (hoja.getLastRowNum() > MAX_FILAS) {
                throw ApiException.badRequest("El archivo supera el maximo de " + MAX_FILAS + " filas.");
            }
            DataFormatter formato = new DataFormatter(Locale.US);
            FormulaEvaluator evaluador = wb.getCreationHelper().createFormulaEvaluator();
            Set<String> dnis = new HashSet<>();
            Set<String> correos = new HashSet<>();
            Map<String, String> cecosNuevos = new HashMap<>();
            for (int r = hoja.getFirstRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
                Row row = hoja.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> v = new HashMap<>();
                for (String c : COLUMNAS) {
                    Integer i = col.get(norm(c));
                    Cell cell = i == null ? null : row.getCell(i);
                    v.put(c, cell == null ? "" : formato.formatCellValue(cell, evaluador).trim());
                }
                if (v.values().stream().allMatch(String::isEmpty)) {
                    continue;
                }
                validarFila(r + 1, v, a, yo, dnis, correos, cecosNuevos);
            }
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw ApiException.badRequest("No se pudo leer el archivo. Sube el Excel (.xlsx) de la plantilla.");
        }
        if (a.filas.isEmpty()) {
            throw ApiException.badRequest("El archivo no tiene filas de personal.");
        }
        return a;
    }

    private void validarFila(int numero, Map<String, String> v, Analisis a, UUID yo, Set<String> dnis,
                             Set<String> correos, Map<String, String> cecosNuevos) {
        Contexto ctx = a.ctx;
        String empresaCode = v.get(COL_EMPRESA).toUpperCase(Locale.ROOT);
        String dni = v.get(COL_DNI).toUpperCase(Locale.ROOT).replace(" ", "");
        if (dni.matches("\\d{1,7}")) {
            dni = "0".repeat(8 - dni.length()) + dni; // Excel quito los ceros a la izquierda
        }
        String nombre = v.get(COL_NOMBRE).replaceAll("\\s+", " ");
        String correo = v.get(COL_CORREO).toLowerCase(Locale.ROOT);
        String cargo = v.get(COL_CARGO).isEmpty() ? null : v.get(COL_CARGO);
        String cecoCodigo = v.get(COL_CECO).isEmpty() ? null : CodigoCeco.normalizar(v.get(COL_CECO)); // DEC-L032
        String cecoNombre = CodigoCeco.nombre(v.get(COL_CECO_NOMBRE)); // DEC-L033
        String rol = v.get(COL_ROL).toUpperCase(Locale.ROOT);
        String textoActivo = v.get(COL_ACTIVO).toUpperCase(Locale.ROOT).replace("Í", "I");

        Empresa empresa = ctx.empresas.get(empresaCode);
        Usuario porDni = ctx.porDni.get(dni);
        Usuario porCorreo = ctx.porCorreo.get(correo);
        Usuario existente = porDni != null ? porDni
                : porCorreo != null && porCorreo.getEmployeeExternalId() == null ? porCorreo : null;

        String error = null;
        if (empresaCode.isEmpty()) {
            error = "Falta la empresa.";
        } else if (empresa == null) {
            error = "La empresa " + empresaCode + " no admite personal manual (se sincroniza desde RR.HH. o no existe).";
        } else if (!DNI.matcher(dni).matches()) {
            error = dni.isEmpty() ? "Falta el DNI." : "DNI invalido (8 a 12 digitos o letras).";
        } else if (!dnis.add(dni)) {
            error = "El DNI " + dni + " esta repetido en el archivo.";
        } else if (nombre.length() < 3) {
            error = "Falta el nombre completo.";
        } else if (!CORREO.matcher(correo).matches()) {
            error = correo.isEmpty() ? "Falta el correo." : "Correo invalido.";
        } else if (!correos.add(correo)) {
            error = "El correo " + correo + " esta repetido en el archivo.";
        } else if (!rol.isEmpty() && !ROLES_INICIALES.contains(rol)) {
            error = "Rol inicial invalido (SOLICITANTE, APROBADOR o LOGISTICA).";
        } else if (!textoActivo.isEmpty() && !textoActivo.equals("SI") && !textoActivo.equals("NO")) {
            error = "Activo debe ser SI o NO.";
        } else if (existente != null && !"MANUAL".equals(existente.getFuente())) {
            error = "Esta persona viene de RR.HH.: sus datos se mantienen alla.";
        } else if (existente != null && (existente.getEmpresa() == null
                || !existente.getEmpresa().getCompanyId().equals(empresa.getCompanyId()))) {
            error = "Esta persona ya esta registrada en otra empresa ("
                    + (existente.getEmpresa() != null ? existente.getEmpresa().getCode() : "sin empresa") + ").";
        } else if (porCorreo != null && porCorreo != existente) {
            error = "El correo ya lo usa otro usuario (" + porCorreo.getDisplayName() + ").";
        } else if (existente == null && "NO".equals(textoActivo)) {
            error = "No se da de alta a una persona inactiva.";
        } else if (existente != null && existente.getUserId().equals(yo) && "NO".equals(textoActivo)) {
            error = "No puedes desactivarte a ti mismo.";
        } else if (cecoCodigo != null && !ctx.cecos.containsKey(clave(empresa, cecoCodigo))) {
            String previo = cecosNuevos.get(clave(empresa, cecoCodigo));
            if (previo == null && cecoNombre.isEmpty()) {
                error = "El CECO " + cecoCodigo + " no existe en " + empresa.getCode()
                        + ": escribe su nombre en \"" + COL_CECO_NOMBRE + "\" para crearlo.";
            } else if (previo == null) {
                cecosNuevos.put(clave(empresa, cecoCodigo), cecoNombre);
            } else {
                cecoNombre = previo;
            }
        }

        String accion = null;
        if (error == null) {
            boolean activo = !"NO".equals(textoActivo);
            if (existente == null) {
                accion = "NUEVO";
            } else if (existente.isActive() && !activo) {
                accion = "DESACTIVADO";
            } else {
                Ceco delExcel = cecoCodigo != null ? ctx.cecos.get(clave(empresa, cecoCodigo)) : null;
                boolean cambia = !nombre.equals(existente.getDisplayName())
                        || !correo.equalsIgnoreCase(existente.getCorporateEmail())
                        || !dni.equals(existente.getEmployeeExternalId())
                        || (cargo != null && !cargo.equals(existente.getCargo()))
                        || (cecoCodigo != null && (delExcel == null || !ctx.tieneCeco(existente.getUserId(), delExcel.getCecoId())))
                        || activo != existente.isActive();
                accion = cambia ? "ACTUALIZADO" : "SIN_CAMBIOS";
            }
            a.planes.add(new Plan(existente, empresa, dni, nombre, correo, cargo, cecoCodigo, cecoNombre,
                    rol.isEmpty() ? Roles.SOLICITANTE : rol, activo, accion));
        }
        a.filas.add(new PersonalCargaDto.Fila(numero, empresaCode, dni, nombre, correo, accion, error));
    }

    // ---------------------------------------------------------------- utilidades

    private static String clave(Empresa e, String codigoCeco) {
        return e.getCompanyId() + "|" + codigoCeco.toUpperCase(Locale.ROOT);
    }

    private static Map<String, Integer> columnas(Row cabecera) {
        Map<String, Integer> col = new HashMap<>();
        if (cabecera == null) {
            return col;
        }
        for (Cell cell : cabecera) {
            if (cell.getCellType() == CellType.STRING) {
                col.putIfAbsent(norm(cell.getStringCellValue()), cell.getColumnIndex());
            }
        }
        return col;
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
