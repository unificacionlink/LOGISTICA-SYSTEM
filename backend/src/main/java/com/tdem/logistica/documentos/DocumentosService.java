package com.tdem.logistica.documentos;

import com.tdem.logistica.config.UrlPublica;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.modules.compras.VerificacionOcService;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto;
import com.tdem.logistica.modules.identidad.FirmaUsuarioService;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.EnlaceCotizacionService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.tracking.TrackingService;
import com.tdem.logistica.modules.identidad.EmpresaAdminService.TipoImagen;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RF-038/082/083: PDFs corporativos. La autorizacion es la misma de la
 * pantalla de cada documento: se obtiene el DTO por el servicio del modulo
 * (que valida rol/ownership) y recien entonces se dibuja.
 */
@Service
@Transactional(readOnly = true)
public class DocumentosService {

    public record Pdf(String nombre, byte[] contenido) {
    }

    private final RequerimientoService requerimientoService;
    private final TrackingService trackingService;
    private final ComprasService comprasService;
    private final DespachoService despachoService;
    private final UsuarioRepository usuarioRepository;
    private final EnlaceCotizacionService enlaces;
    private final EmpresaRepository empresaRepository;
    private final StorageClient storage;
    private final JdbcTemplate jdbc;
    private final VerificacionOcService verificaciones;
    private final FirmaUsuarioService firmas;
    private final UrlPublica url;

    private static final Logger log = LoggerFactory.getLogger(DocumentosService.class);

    public DocumentosService(RequerimientoService requerimientoService, TrackingService trackingService,
                             ComprasService comprasService, DespachoService despachoService,
                             UsuarioRepository usuarioRepository, EnlaceCotizacionService enlaces,
                             EmpresaRepository empresaRepository, StorageClient storage, JdbcTemplate jdbc,
                             VerificacionOcService verificaciones, FirmaUsuarioService firmas, UrlPublica url) {
        this.verificaciones = verificaciones;
        this.firmas = firmas;
        this.url = url;
        this.enlaces = enlaces;
        this.empresaRepository = empresaRepository;
        this.storage = storage;
        this.jdbc = jdbc;
        this.requerimientoService = requerimientoService;
        this.trackingService = trackingService;
        this.comprasService = comprasService;
        this.despachoService = despachoService;
        this.usuarioRepository = usuarioRepository;
    }

    public Pdf requerimiento(UUID id, UsuarioPrincipal principal) {
        var d = requerimientoService.obtener(id, principal);
        return new Pdf(d.requerimiento().code() + ".pdf", DocumentosPdf.requerimiento(d, pie(principal)));
    }

    public Pdf tracking(UUID requestId, UsuarioPrincipal principal) {
        var t = trackingService.obtener(requestId, principal);
        return new Pdf("TRACKING_" + t.requerimiento().code() + ".pdf", DocumentosPdf.tracking(t, pie(principal)));
    }

    /** @param tokenEnlace token recien generado (DEC-L052): si es el vigente, el PDF lleva el enlace y su QR. */
    public Pdf rfq(UUID id, String tokenEnlace, UsuarioPrincipal principal) {
        var d = comprasService.obtenerRfq(id, principal);
        String url = tokenEnlace != null ? enlaces.urlSiVigente(id, tokenEnlace, principal) : null;
        return new Pdf(d.rfq().rfqCode() + ".pdf", DocumentosPdf.rfq(d, pie(principal), url));
    }

    /** Formato F-LOG-09 (DEC-L079); obtenerOrden valida antes quien puede verla. */
    public Pdf orden(UUID id, UsuarioPrincipal principal) {
        var d = comprasService.obtenerOrden(id, principal);
        return new Pdf(d.orden().orderCode() + ".pdf", DocumentosPdf.orden(d, extrasOrden(id, d)));
    }

    private DocumentosPdf.ExtrasOrden extrasOrden(UUID orderId, com.tdem.logistica.modules.compras.dto.OrdenDetalleDto d) {
        Empresa e = empresaRepository.findByCode(d.empresaCode()).orElseThrow();
        String[] contacto = jdbc.query("""
                select p.contact_name, coalesce(nullif(p.phone, ''), p.whatsapp_phone)
                  from orden_abastecimiento o join proveedor p on p.supplier_id = o.supplier_id
                 where o.order_id = ?""",
                rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : new String[2], orderId);
        List<String> deposito = new ArrayList<>();
        List<String> detraccion = new ArrayList<>();
        jdbc.query("""
                select c.bank_name, c.account_type, c.currency_code, c.account_number, c.cci
                  from proveedor_cuenta_bancaria c
                  join orden_abastecimiento o on o.supplier_id = c.supplier_id
                 where o.order_id = ?
                 order by c.is_primary desc, c.bank_name""",
                rs -> {
                    String tipo = rs.getString(2);
                    String cci = rs.getString(5);
                    String texto = rs.getString(1) + " · " + etiquetaCuenta(tipo) + " " + rs.getString(3)
                            + " · N° " + rs.getString(4) + (cci != null && !cci.isBlank() ? " · CCI " + cci : "");
                    ("DETRACCION".equals(tipo) ? detraccion : deposito).add(texto);
                }, orderId);
        boolean aprobada = d.orden().approvedAt() != null && !"ANULADA".equals(d.orden().status());
        return new DocumentosPdf.ExtrasOrden(nombreCorto(e.getCode()), e.getRuc(), e.getAddress(), e.getPhone(),
                logo(e), contacto[0], contacto[1], deposito, detraccion,
                aprobada ? imagenPrivada(e, TipoImagen.FIRMA_GERENCIA) : null,
                aprobada ? imagenPrivada(e, TipoImagen.FIRMA_LOGISTICA) : null);
    }

    private static String etiquetaCuenta(String tipo) {
        return switch (tipo) {
            case "AHORROS" -> "Cta. ahorros";
            case "CORRIENTE" -> "Cta. corriente";
            case "DETRACCION" -> "Cta. detracción";
            default -> "Cuenta";
        };
    }

    /** "TDEMSRL" -> "TDEM", "GOYDELSAC" -> "GOYDEL": como la nombra el formato. */
    static String nombreCorto(String code) {
        return code.replaceFirst("(SRL|SAC|SA)$", "");
    }

    /** El logo subido en Administracion; TDEM tiene ademas el del formato incluido en la aplicacion. */
    private byte[] logo(Empresa e) {
        byte[] subido = imagenPrivada(e, TipoImagen.LOGO);
        if (subido != null || !"TDEMSRL".equals(e.getCode())) {
            return subido;
        }
        try (InputStream in = DocumentosService.class.getResourceAsStream("/documentos/logo-tdem.png")) {
            return in != null ? in.readAllBytes() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    /** Una imagen que no se puede leer no impide el PDF: sale sin ella. */
    private byte[] imagenPrivada(Empresa e, TipoImagen tipo) {
        String clave = com.tdem.logistica.modules.identidad.EmpresaAdminService.clave(e, tipo);
        if (clave == null) {
            return null;
        }
        try {
            return storage.leerPrivado(clave);
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer la imagen {} de {}: {}", tipo, e.getCode(), ex.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- F-LOG-08 (DEC-L102)

    public Pdf verificacionOc(UUID orderId, UsuarioPrincipal principal) {
        return verificacion(verificaciones.obtener(orderId, principal));
    }

    /** Quien ve la Nota de Inventario descarga el F-LOG-08 de las OC que despacha. */
    public Pdf verificacionDeNota(UUID noteId, UUID orderId, UsuarioPrincipal principal) {
        despachoService.obtenerNota(noteId, principal);
        boolean relacionada = verificaciones.deNota(noteId).stream().anyMatch(v -> v.orderId().equals(orderId));
        if (!relacionada) {
            throw com.tdem.logistica.common.ApiException.notFound("Esa verificación no corresponde a la nota.");
        }
        return verificacion(verificaciones.armar(orderId, false));
    }

    /** Arma el PDF desde lo que ve la pantalla, ya autorizado. */
    Pdf verificacion(VerificacionOcDto v) {
        if (!v.registrada()) {
            throw com.tdem.logistica.common.ApiException.badRequest("La orden de compra aún no tiene su verificación.");
        }
        VerificacionOcService.Cabecera c = verificaciones.cabecera(v.orderId());
        Empresa e = empresaRepository.findByCode(c.empresaCode()).orElseThrow();
        List<PdfVerificacionOc.Linea> lineas = v.items().stream()
                .map(i -> new PdfVerificacionOc.Linea(i.descripcion(), i.um(), cantidad(i.cantidad()),
                        cantidad(i.recibida()), i.funcionalidad(), i.estandares(), i.especificaciones(),
                        i.observacion()))
                .toList();
        String fecha = v.verificadaEn().atZone(java.time.ZoneId.of("America/Lima"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        byte[] contenido = PdfVerificacionOc.generar(new PdfVerificacionOc.Datos(
                nombreCorto(e.getCode()), logo(e), c.codigo(), c.proveedor(), c.proveedorTelefono(), c.proveedorRuc(),
                c.codigo(), c.lugarEntrega(), fecha, url.de("/compras/ordenes/" + v.orderId()), lineas,
                v.conclusion(), firma(v.verificadaPor()), firma(v.especialista())));
        return new Pdf("F-LOG-08_" + c.codigo() + ".pdf", contenido);
    }

    private PdfVerificacionOc.Firma firma(VerificacionOcDto.Persona p) {
        return p == null ? null : new PdfVerificacionOc.Firma(p.nombre(), p.cargo(), firmas.leer(p.id()));
    }

    private static String cantidad(java.math.BigDecimal n) {
        return n == null ? "" : n.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public Pdf notaInventario(UUID id, UsuarioPrincipal principal) {
        var d = despachoService.obtenerNota(id, principal);
        return new Pdf(d.nota().inventoryNoteCode() + ".pdf", DocumentosPdf.notaInventario(d, pie(principal)));
    }

    /** Trazabilidad del impreso: quien y cuando lo genero. */
    private String pie(UsuarioPrincipal principal) {
        String nombre = usuarioRepository.findById(principal.userId())
                .map(Usuario::getDisplayName)
                .orElse("usuario");
        return "Generado el " + DocumentosPdf.fechaHora(Instant.now()) + " por " + nombre
                + " · ERP Logístico Corporativo";
    }
}
