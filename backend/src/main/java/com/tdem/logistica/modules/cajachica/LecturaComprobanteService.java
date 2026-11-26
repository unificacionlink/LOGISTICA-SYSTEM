package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.cajachica.dto.LecturaComprobanteDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.OcrExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OCR del comprobante de caja chica (DEC-L095): lee la foto o el PDF con el OCR
 * local (Tesseract, el archivo no sale del servidor) y devuelve lo que pudo
 * reconocer. No guarda nada. Si el comprobante no es legible, devuelve la
 * lectura vacia: el usuario completa a mano, nunca es un error.
 */
@Service
public class LecturaComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(LecturaComprobanteService.class);
    private static final Set<String> FORMATOS = Set.of("application/pdf", "image/png", "image/jpeg");
    private static final long MAXIMO_BYTES = 8L * 1024 * 1024;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final OcrExtractor ocr;
    private final JdbcTemplate jdbc;

    public LecturaComprobanteService(OcrExtractor ocr, JdbcTemplate jdbc) {
        this.ocr = ocr;
        this.jdbc = jdbc;
    }

    public LecturaComprobanteDto leer(MultipartFile archivo, UsuarioPrincipal principal) {
        if (principal == null) {
            throw ApiException.forbidden("Inicia sesion para leer el comprobante.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.badRequest("Adjunta la foto o el PDF del comprobante.");
        }
        if (!FORMATOS.contains(archivo.getContentType())) {
            throw ApiException.badRequest("El comprobante va como PDF o foto (PNG o JPG).");
        }
        if (archivo.getSize() > MAXIMO_BYTES) {
            throw ApiException.badRequest("El archivo supera los 8 MB.");
        }
        String texto;
        try {
            texto = ocr.extraerTexto(archivo.getBytes(), archivo.getContentType(), true);
        } catch (IOException | RuntimeException e) {
            // Foto borrosa, formato que el OCR no decodifica, PDF danado: se completa a mano.
            log.info("No se pudo leer el comprobante de caja chica: {}", e.getMessage());
            return LecturaComprobanteDto.vacia();
        }
        List<String> rucs = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        jdbc.query("select ruc, legal_name from empresa", rs -> {
            if (rs.getString(1) != null) rucs.add(rs.getString(1));
            if (rs.getString(2) != null) nombres.add(rs.getString(2));
        });
        return ComprobanteOcrParser.leer(texto, rucs, nombres, LocalDate.now(LIMA));
    }
}
