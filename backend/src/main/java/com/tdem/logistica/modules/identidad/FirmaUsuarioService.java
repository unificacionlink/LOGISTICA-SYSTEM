package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Firma escaneada de cada usuario (DEC-L102): la sube ADMIN desde la ficha del
 * usuario y se estampa en los V°B° del F-LOG-08. Va al bucket privado; la base
 * solo guarda la clave ({@code usuario.firma_key}).
 */
@Service
public class FirmaUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(FirmaUsuarioService.class);
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/png", "image/jpeg");

    private final JdbcTemplate jdbc;
    private final StorageClient storage;
    private final Auditoria auditoria;
    private final AdminService admin;

    public FirmaUsuarioService(JdbcTemplate jdbc, StorageClient storage, Auditoria auditoria, AdminService admin) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.auditoria = auditoria;
        this.admin = admin;
    }

    @Transactional
    public com.tdem.logistica.modules.identidad.dto.AdminDatosDto subir(UUID userId, MultipartFile file,
                                                                        UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        existe(userId);
        byte[] contenido = validar(file);
        // Clave fija por usuario: subir otra reemplaza la anterior.
        String clave = storage.subirPrivado("usuarios/" + userId + "/firma", contenido, file.getContentType());
        jdbc.update("update usuario set firma_key = ?, updated_at = now() where user_id = ?", clave, userId);
        auditoria.registrar("USUARIO", userId, "SUBIR_FIRMA", null, Map.of("clave", clave), principal.userId());
        return admin.datos(principal);
    }

    @Transactional
    public com.tdem.logistica.modules.identidad.dto.AdminDatosDto quitar(UUID userId, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        existe(userId);
        jdbc.update("update usuario set firma_key = null, updated_at = now() where user_id = ?", userId);
        auditoria.registrar("USUARIO", userId, "QUITAR_FIRMA", null, null, principal.userId());
        return admin.datos(principal);
    }

    /** Vista previa para el administrador. */
    public EmpresaAdminService.Imagen ver(UUID userId, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        byte[] contenido = leer(userId);
        if (contenido == null) {
            throw ApiException.notFound("El usuario no tiene firma.");
        }
        boolean png = contenido.length > 3 && (contenido[0] & 0xff) == 0x89 && contenido[1] == 'P';
        return new EmpresaAdminService.Imagen(contenido, png ? "image/png" : "image/jpeg");
    }

    /** La firma para un documento; si no hay o no se puede leer, null (el documento sale sin ella). */
    public byte[] leer(UUID userId) {
        if (userId == null) {
            return null;
        }
        String clave = jdbc.query("select firma_key from usuario where user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        if (clave == null) {
            return null;
        }
        try {
            return storage.leerPrivado(clave);
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer la firma del usuario {}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private void existe(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from usuario where user_id = ?", Integer.class, userId);
        if (n == null || n == 0) {
            throw ApiException.notFound("Usuario no encontrado.");
        }
    }

    private static byte[] validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona la imagen de la firma.");
        }
        if (file.getContentType() == null || !TIPOS.contains(file.getContentType())) {
            throw ApiException.badRequest("La firma debe ser PNG o JPG.");
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
}
