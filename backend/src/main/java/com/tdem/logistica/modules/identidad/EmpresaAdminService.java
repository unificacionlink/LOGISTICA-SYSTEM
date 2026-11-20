package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.modules.identidad.dto.EditarEmpresaRequest;
import com.tdem.logistica.modules.identidad.dto.EmpresaDatosDto;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de cada empresa para el formato F-LOG-09 (DEC-L079): razon social, RUC,
 * direccion, telefono, logo y las firmas escaneadas del V°B° Gerencia y del
 * V°B° Logistica. Las imagenes van al bucket privado (DEC-L071): una firma
 * escaneada no debe tener URL publica. Solo ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class EmpresaAdminService {

    /** Imagen de la empresa: el logo o una de las dos firmas del formato. */
    public enum TipoImagen {
        LOGO("logo"), FIRMA_GERENCIA("firma-gerencia"), FIRMA_LOGISTICA("firma-logistica");

        private final String ruta;

        TipoImagen(String ruta) {
            this.ruta = ruta;
        }

        public static TipoImagen de(String ruta) {
            for (TipoImagen t : values()) {
                if (t.ruta.equals(ruta)) {
                    return t;
                }
            }
            throw ApiException.badRequest("Imagen invalida: usa logo, firma-gerencia o firma-logistica.");
        }
    }

    public record Imagen(byte[] contenido, String tipo) {
    }

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/png", "image/jpeg");
    private static final String ENTIDAD = "EMPRESA";

    private final EmpresaRepository empresaRepository;
    private final StorageClient storage;
    private final Auditoria auditoria;

    public EmpresaAdminService(EmpresaRepository empresaRepository, StorageClient storage, Auditoria auditoria) {
        this.empresaRepository = empresaRepository;
        this.storage = storage;
        this.auditoria = auditoria;
    }

    public List<EmpresaDatosDto> listar(UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        return empresaRepository.findAllByActiveTrueOrderByCode().stream().map(EmpresaAdminService::toDto).toList();
    }

    @Transactional
    public List<EmpresaDatosDto> editar(UUID id, EditarEmpresaRequest r, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Empresa e = empresa(id);
        Map<String, Object> antes = datos(e);
        e.setLegalName(r.nombre().trim());
        e.setRuc(limpio(r.ruc()));
        e.setAddress(limpio(r.direccion()));
        e.setPhone(limpio(r.telefono()));
        empresaRepository.save(e);
        auditoria.registrar(ENTIDAD, id, "EDITAR", antes, datos(e), principal.userId());
        return listar(principal);
    }

    @Transactional
    public List<EmpresaDatosDto> subirImagen(UUID id, String ruta, MultipartFile file, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        TipoImagen tipo = TipoImagen.de(ruta);
        Empresa e = empresa(id);
        byte[] contenido = validarImagen(file);
        // Clave fija por empresa y tipo: subir otra reemplaza la anterior.
        String clave = storage.subirPrivado("empresas/" + id + "/" + tipo.ruta, contenido, file.getContentType());
        asignar(e, tipo, clave);
        empresaRepository.save(e);
        auditoria.registrar(ENTIDAD, id, "SUBIR_" + tipo.name(), null, Map.of("clave", clave), principal.userId());
        return listar(principal);
    }

    @Transactional
    public List<EmpresaDatosDto> quitarImagen(UUID id, String ruta, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        TipoImagen tipo = TipoImagen.de(ruta);
        Empresa e = empresa(id);
        asignar(e, tipo, null);
        empresaRepository.save(e);
        auditoria.registrar(ENTIDAD, id, "QUITAR_" + tipo.name(), null, null, principal.userId());
        return listar(principal);
    }

    /** Vista previa para el administrador. */
    public Imagen leerImagen(UUID id, String ruta, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        String clave = clave(empresa(id), TipoImagen.de(ruta));
        if (clave == null) {
            throw ApiException.notFound("La empresa no tiene esa imagen.");
        }
        byte[] contenido = storage.leerPrivado(clave);
        return new Imagen(contenido, esPng(contenido) ? "image/png" : "image/jpeg");
    }

    public static String clave(Empresa e, TipoImagen tipo) {
        return switch (tipo) {
            case LOGO -> e.getLogoKey();
            case FIRMA_GERENCIA -> e.getFirmaGerenciaKey();
            case FIRMA_LOGISTICA -> e.getFirmaLogisticaKey();
        };
    }

    private static byte[] validarImagen(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona la imagen.");
        }
        if (file.getContentType() == null || !TIPOS.contains(file.getContentType())) {
            throw ApiException.badRequest("La imagen debe ser PNG o JPG.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("La imagen supera los 2 MB.");
        }
        try {
            byte[] contenido = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(contenido)) == null) {
                throw ApiException.badRequest("El archivo no es una imagen valida.");
            }
            return contenido;
        } catch (IOException ex) {
            throw ApiException.badRequest("No se pudo leer la imagen.");
        }
    }

    private static void asignar(Empresa e, TipoImagen tipo, String clave) {
        switch (tipo) {
            case LOGO -> e.setLogoKey(clave);
            case FIRMA_GERENCIA -> e.setFirmaGerenciaKey(clave);
            case FIRMA_LOGISTICA -> e.setFirmaLogisticaKey(clave);
        }
    }

    private Empresa empresa(UUID id) {
        return empresaRepository.findById(id).orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
    }

    private static EmpresaDatosDto toDto(Empresa e) {
        return new EmpresaDatosDto(e.getCompanyId(), e.getCode(), e.getLegalName(), e.getRuc(), e.getAddress(),
                e.getPhone(), e.getLogoKey() != null, e.getFirmaGerenciaKey() != null, e.getFirmaLogisticaKey() != null);
    }

    private static Map<String, Object> datos(Empresa e) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", e.getLegalName());
        m.put("ruc", e.getRuc());
        m.put("direccion", e.getAddress());
        m.put("telefono", e.getPhone());
        return m;
    }

    static boolean esPng(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
