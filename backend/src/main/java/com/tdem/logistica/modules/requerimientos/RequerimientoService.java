package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.common.AppConstants.EntidadAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.PrioridadRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.aprobaciones.AmbitoAprobacion;
import com.tdem.logistica.modules.presupuestos.ControlPresupuestalService;
import com.tdem.logistica.modules.aprobaciones.dto.AprobacionPendienteDto;
import com.tdem.logistica.modules.requerimientos.dto.*;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logica de negocio de Requerimientos. Reemplaza getSessionUser()+tieneAcceso()
 * (Next.js) y las RLS policies de Postgres (DEC-L008): toda la autorizacion
 * por rol/ownership se valida aqui, al inicio de cada metodo.
 */
@Service
@Transactional(readOnly = true)
public class RequerimientoService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final Set<String> ESTADOS_PROGRAMABLES = Set.of(
            EstadoRequerimiento.VERIFICACION_STOCK, EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);

    // Sin image/svg+xml a proposito: un SVG puede llevar <script> embebido y
    // ejecutarlo si la URL de Supabase Storage se abre directo en el navegador.
    private static final Map<String, String> EXTENSIONES_IMAGEN_PERMITIDAS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final ProductoRepository productoRepository;
    private final FamiliaProductoRepository familiaProductoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final DocumentoRepository documentoRepository;
    private final CecoRepository cecoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AprobacionRepository aprobacionRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final StorageClient storageClient;
    private final Auditoria auditoria;
    private final AmbitoAprobacion ambitoAprobacion;
    private final ControlPresupuestalService controlPresupuestal;

    public RequerimientoService(
            RequerimientoRepository requerimientoRepository,
            RequerimientoItemRepository requerimientoItemRepository,
            ProductoVarianteRepository productoVarianteRepository,
            ProductoRepository productoRepository,
            FamiliaProductoRepository familiaProductoRepository,
            ProductoImagenRepository productoImagenRepository,
            DocumentoRepository documentoRepository,
            CecoRepository cecoRepository,
            UsuarioRepository usuarioRepository,
            AprobacionRepository aprobacionRepository,
            RealtimeNotifier realtimeNotifier,
            StorageClient storageClient,
            Auditoria auditoria,
            AmbitoAprobacion ambitoAprobacion,
            ControlPresupuestalService controlPresupuestal) {
        this.ambitoAprobacion = ambitoAprobacion;
        this.controlPresupuestal = controlPresupuestal;
        this.realtimeNotifier = realtimeNotifier;
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.productoVarianteRepository = productoVarianteRepository;
        this.productoRepository = productoRepository;
        this.familiaProductoRepository = familiaProductoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.documentoRepository = documentoRepository;
        this.cecoRepository = cecoRepository;
        this.usuarioRepository = usuarioRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.storageClient = storageClient;
        this.auditoria = auditoria;
    }

    private boolean esLogisticaOAdmin(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
    }

    public List<RequerimientoDto> listar(UsuarioPrincipal principal) {
        List<Requerimiento> filas = esLogisticaOAdmin(principal)
                ? requerimientoRepository.findAllConDetalle()
                : requerimientoRepository.findByRequester(principal.userId());
        return filas.stream().map(RequerimientoMapper::toDto).toList();
    }

    /**
     * Quien puede ver un REQ (detalle y Tracking): su dueno, LOGISTICA/ADMIN y,
     * ya enviado, el aprobador que le toca o le toco decidirlo (DEC-L030).
     */
    public void exigirAcceso(Requerimiento r, UsuarioPrincipal principal) {
        boolean esDueno = r.getRequester() != null && r.getRequester().getUserId().equals(principal.userId());
        if (!esDueno && !esLogisticaOAdmin(principal) && !ambitoAprobacion.puedeVerComoAprobador(r, principal)) {
            throw ApiException.forbidden("No tienes permiso para ver este requerimiento.");
        }
    }

    public RequerimientoDetalleDto obtener(UUID id, UsuarioPrincipal principal) {
        // CECO, empresa y solicitante en la misma consulta: con la BD lejos, cada ida y vuelta cuesta.
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        exigirAcceso(r, principal);

        List<RequerimientoItemDto> items = requerimientoItemRepository.findByRequerimiento(id).stream()
                .map(RequerimientoMapper::toDto)
                .toList();
        // Pendiente y observacion vigente salen de la misma lectura de aprobaciones.
        // pendingApprovalId solo viaja si este usuario puede decidirla (DEC-L030).
        List<Aprobacion> aprobaciones = aprobacionRepository.findPorEntidad(EntidadAprobacion.REQUERIMIENTO, id);
        UUID pendingApprovalId = aprobaciones.stream()
                .filter(a -> EstadoAprobacion.PENDIENTE.equals(a.getStatus()))
                .reduce((primera, ultima) -> ultima)
                .filter(a -> AmbitoAprobacion.esDecisor(principal) && ambitoAprobacion.puedeDecidir(a, r, principal))
                .map(Aprobacion::getApprovalId)
                .orElse(null);
        ObservacionDto observacion = null;
        if (EstadoRequerimiento.OBSERVADO.equals(r.getStatus())) {
            observacion = aprobaciones.stream()
                    .filter(a -> EstadoAprobacion.OBSERVADO.equals(a.getStatus()))
                    .reduce((primera, ultima) -> ultima)
                    .map(a -> new ObservacionDto(a.getDecisionReason(),
                            a.getDecidedBy() == null ? null : usuarioRepository.findById(a.getDecidedBy())
                                    .map(Usuario::getDisplayName).orElse(null),
                            a.getDecidedAt()))
                    .orElse(null);
        }
        return new RequerimientoDetalleDto(RequerimientoMapper.toDto(r), items, pendingApprovalId, observacion,
                controlPresupuestal.ultimo(id));
    }

    public CatalogoFormularioDto obtenerDatosFormulario(UsuarioPrincipal principal) {
        // PYC y GERENCIA leen el catalogo para registrar precios de venta (DEC-L035); no crean REQ.
        if (!principal.tieneAlgunRol(Roles.SOLICITANTE, Roles.LOGISTICA, Roles.ADMIN, Roles.PYC, Roles.GERENCIA)) {
            throw ApiException.forbidden("No tienes permiso para ver el catalogo.");
        }

        Map<UUID, String> imagenesPorProducto = productoImagenRepository.findPrincipalesActivas().stream()
                .collect(Collectors.toMap(
                        pi -> pi.getProducto().getProductId(),
                        pi -> pi.getDocumento().getFileUrl(),
                        (a, b) -> a));

        List<VarianteCatalogoDto> variantes = productoVarianteRepository.findCatalogoActivo().stream()
                .map(v -> RequerimientoMapper.toDto(v, imagenesPorProducto.get(v.getProducto().getProductId())))
                .toList();
        List<FamiliaDto> familias = familiaProductoRepository.findActivasConTipo().stream()
                .map(RequerimientoMapper::toDto)
                .toList();
        List<CecoDto> cecos = cecosPermitidos(principal).stream()
                .map(RequerimientoMapper::toDto)
                .toList();
        return new CatalogoFormularioDto(variantes, familias, cecos);
    }

    /**
     * CECOs para los que el usuario puede pedir (DEC-L029): el SOLICITANTE solo los
     * que ADMIN le asigno en {@code usuario_ceco}, de cualquier empresa; LOGISTICA/ADMIN
     * cualquiera activo. La empresa del REQ sale del CECO elegido.
     */
    private List<Ceco> cecosPermitidos(UsuarioPrincipal principal) {
        if (esLogisticaOAdmin(principal)) {
            return cecoRepository.findActivosConEmpresa();
        }
        List<UUID> ids = cecoRepository.findIdsAsignados(principal.userId());
        return ids.isEmpty() ? List.of() : cecoRepository.findActivosPorIds(ids);
    }

    private Ceco cecoPermitido(UUID cecoId, UsuarioPrincipal principal) {
        return cecosPermitidos(principal).stream()
                .filter(c -> c.getCecoId().equals(cecoId))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("No tienes asignado ese centro de costo."));
    }

    /** Sube/reemplaza la foto principal de un producto (aplica a todas sus variantes). */
    @Transactional
    public String subirImagenProducto(UUID productId, MultipartFile file, UsuarioPrincipal principal) {
        if (!esLogisticaOAdmin(principal)) {
            throw ApiException.forbidden("No tienes permiso para gestionar imagenes de productos.");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo de imagen.");
        }
        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSIONES_IMAGEN_PERMITIDAS.get(contentType) : null;
        if (extension == null) {
            throw ApiException.badRequest("Formato de imagen no soportado (usa JPG, PNG, WEBP o GIF).");
        }
        Producto producto = productoRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));

        // La extension sale del content-type validado arriba (no del nombre de
        // archivo del cliente), para que un filename manipulado no pueda inyectar
        // segmentos de ruta (../) en el path de Supabase Storage.
        String nombreOriginal = file.getOriginalFilename();
        String path = producto.getProductId() + "/" + UUID.randomUUID() + extension;

        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String url = storageClient.subir(path, contenido, contentType);

        // Solo una imagen principal activa por producto (indice parcial en BD): se
        // desactiva la anterior antes de insertar la nueva. El flush es obligatorio:
        // Hibernate ejecuta los INSERT antes que los UPDATE, y sin el la nueva
        // entraba con la anterior aun activa (uq_producto_imagen_principal, al
        // cambiar una foto existente).
        productoImagenRepository.findPrincipalesActivasPorProducto(productId)
                .forEach(pi -> {
                    pi.setActive(false);
                    productoImagenRepository.save(pi);
                });
        productoImagenRepository.flush();

        Documento documento = new Documento();
        documento.setDocumentId(UUID.randomUUID());
        documento.setDocumentType("IMAGEN_PRODUCTO");
        documento.setFileName(nombreOriginal != null ? nombreOriginal : path);
        documento.setFileUrl(url);
        documento.setMimeType(contentType);
        documento.setUploadedBy(principal.userId());
        documento.setUploadedAt(Instant.now());
        documento.setStatus("ACTIVO");
        documentoRepository.save(documento);

        ProductoImagen imagen = new ProductoImagen();
        imagen.setProductImageId(UUID.randomUUID());
        imagen.setProducto(producto);
        imagen.setDocumento(documento);
        imagen.setPrimary(true);
        imagen.setDisplayOrder(0);
        imagen.setActive(true);
        imagen.setCreatedAt(Instant.now());
        imagen.setCreatedBy(principal.userId());
        productoImagenRepository.save(imagen);

        return url;
    }

    @Transactional
    public RequerimientoDto crear(CrearRequerimientoRequest request, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.SOLICITANTE, Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para crear requerimientos.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Ceco ceco = cecoPermitido(request.cecoId(), principal);

        String prioridad = request.priority() != null && PrioridadRequerimiento.VALORES.contains(request.priority())
                ? request.priority()
                : PrioridadRequerimiento.NORMAL;

        List<UUID> variantIds = request.items().stream().map(ItemRequerimientoRequest::variantId).toList();
        List<ProductoVariante> variantes = productoVarianteRepository.findActivasPorId(variantIds);
        Map<UUID, ProductoVariante> porId = variantes.stream()
                .collect(Collectors.toMap(ProductoVariante::getVariantId, v -> v));
        for (UUID id : variantIds) {
            if (!porId.containsKey(id)) {
                throw ApiException.badRequest("Uno o mas productos seleccionados ya no estan disponibles.");
            }
        }

        Requerimiento r = new Requerimiento();
        r.setRequestId(UUID.randomUUID());
        r.setRequestCode(generarRequestCode(ceco.getEmpresa().getCode()));
        r.setEmpresa(ceco.getEmpresa());
        r.setCeco(ceco);
        r.setRequester(usuario);
        r.setRequesterType("INTERNO");
        r.setPriority(prioridad);
        r.setPurpose(request.purpose());
        r.setJustification(request.justification());
        r.setStatus(EstadoRequerimiento.BORRADOR);
        r.setRequiredDate(request.requiredDate());
        requerimientoRepository.save(r);

        for (ItemRequerimientoRequest itemReq : request.items()) {
            ProductoVariante variante = porId.get(itemReq.variantId());
            RequerimientoItem item = new RequerimientoItem();
            item.setRequestItemId(UUID.randomUUID());
            item.setRequerimiento(r);
            item.setProducto(variante.getProducto());
            item.setVariante(variante);
            item.setUom(variante.getProducto().getBaseUom());
            item.setQtyRequested(itemReq.qtyRequested());
            item.setTechnicalRequirement(itemReq.technicalRequirement());
            item.setObservation(itemReq.observation());
            requerimientoItemRepository.save(item);
        }

        return RequerimientoMapper.toDto(r);
    }

    @Transactional
    public RequerimientoDto actualizar(UUID id, CrearRequerimientoRequest request, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));

        boolean esDueno = r.getRequester() != null && r.getRequester().getUserId().equals(principal.userId());
        if (!esDueno && !esLogisticaOAdmin(principal)) {
            throw ApiException.forbidden("No tienes permiso para editar este requerimiento.");
        }
        if (!EstadoRequerimiento.EDITABLES.contains(r.getStatus())) {
            throw ApiException.badRequest("Solo se puede editar un requerimiento en borrador u observado.");
        }

        // Se conserva el CECO actual aunque ADMIN se lo haya quitado despues; para cambiarlo,
        // el nuevo debe estar asignado. La empresa no cambia: va en el codigo del REQ.
        Ceco ceco = request.cecoId().equals(r.getCeco().getCecoId())
                ? r.getCeco()
                : cecoPermitido(request.cecoId(), principal);
        if (!ceco.getEmpresa().getCompanyId().equals(r.getEmpresa().getCompanyId())) {
            throw ApiException.badRequest("El centro de costo es de otra empresa: crea un requerimiento nuevo para ella.");
        }

        String prioridad = request.priority() != null && PrioridadRequerimiento.VALORES.contains(request.priority())
                ? request.priority()
                : PrioridadRequerimiento.NORMAL;

        List<UUID> variantIds = request.items().stream().map(ItemRequerimientoRequest::variantId).toList();
        List<ProductoVariante> variantes = productoVarianteRepository.findActivasPorId(variantIds);
        Map<UUID, ProductoVariante> porId = variantes.stream()
                .collect(Collectors.toMap(ProductoVariante::getVariantId, v -> v));
        for (UUID variantId : variantIds) {
            if (!porId.containsKey(variantId)) {
                throw ApiException.badRequest("Uno o mas productos seleccionados ya no estan disponibles.");
            }
        }

        r.setCeco(ceco);
        r.setPriority(prioridad);
        r.setPurpose(request.purpose());
        r.setJustification(request.justification());
        r.setRequiredDate(request.requiredDate());
        requerimientoRepository.save(r);

        // Reemplazo completo de items: seguro porque solo se edita en BORRADOR u
        // OBSERVADO, estados en los que todavia no puede existir ninguna
        // ReservaStock/RfqItem/OrdenItem apuntando a estos request_item_id (esos
        // recien se crean al aprobar).
        requerimientoItemRepository.deleteAll(requerimientoItemRepository.findByRequerimiento(id));
        for (ItemRequerimientoRequest itemReq : request.items()) {
            ProductoVariante variante = porId.get(itemReq.variantId());
            RequerimientoItem item = new RequerimientoItem();
            item.setRequestItemId(UUID.randomUUID());
            item.setRequerimiento(r);
            item.setProducto(variante.getProducto());
            item.setVariante(variante);
            item.setUom(variante.getProducto().getBaseUom());
            item.setQtyRequested(itemReq.qtyRequested());
            item.setTechnicalRequirement(itemReq.technicalRequirement());
            item.setObservation(itemReq.observation());
            requerimientoItemRepository.save(item);
        }

        return RequerimientoMapper.toDto(r);
    }

    // Llamada interna (sin proxy): la transaccion de escritura debe abrirse aqui.
    @Transactional
    public RequerimientoDto enviar(UUID id, UsuarioPrincipal principal) {
        return enviar(id, null, principal);
    }

    /**
     * Envia a aprobacion. Si el periodo del CECO tiene control presupuestal y el
     * REQ se sale del presupuesto, exige {@code justificacionPresupuestal}
     * (DEC-L038, REQ-02).
     */
    @Transactional
    public RequerimientoDto enviar(UUID id, String justificacionPresupuestal, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));

        boolean esDueno = r.getRequester() != null && r.getRequester().getUserId().equals(principal.userId());
        if (!esDueno && !esLogisticaOAdmin(principal)) {
            throw ApiException.forbidden("No tienes permiso para enviar este requerimiento.");
        }
        if (!EstadoRequerimiento.EDITABLES.contains(r.getStatus())) {
            throw ApiException.badRequest("Solo se puede enviar un requerimiento en borrador u observado.");
        }
        controlPresupuestal.verificar(r.getRequestId(), justificacionPresupuestal, principal.userId());

        r.setStatus(EstadoRequerimiento.PENDIENTE_APROBACION);
        requerimientoRepository.save(r);

        Aprobacion aprobacion = new Aprobacion();
        aprobacion.setApprovalId(UUID.randomUUID());
        aprobacion.setEntityType(EntidadAprobacion.REQUERIMIENTO);
        aprobacion.setEntityId(r.getRequestId());
        aprobacion.setStatus(EstadoAprobacion.PENDIENTE);
        aprobacion.setAssignedTo(aprobadorAsignado(r));
        aprobacionRepository.save(aprobacion);

        RequerimientoDto dto = RequerimientoMapper.toDto(r);
        AprobacionPendienteDto pendiente = new AprobacionPendienteDto(aprobacion.getApprovalId(), dto);
        if (aprobacion.getAssignedTo() != null) {
            realtimeNotifier.aprobacionAsignada(aprobacion.getAssignedTo(), pendiente);
        } else {
            realtimeNotifier.nuevaAprobacionPendiente(pendiente);
        }
        return dto;
    }

    /**
     * DEC-L030: el aprobador del CECO al momento de enviar. Si es el propio
     * solicitante o ya no esta activo, queda sin asignar (decide cualquier
     * APROBADOR/GERENCIA distinto del solicitante, o ADMIN).
     */
    private UUID aprobadorAsignado(Requerimiento r) {
        UUID aprobador = r.getCeco().getApproverUserId();
        if (aprobador == null || (r.getRequester() != null && aprobador.equals(r.getRequester().getUserId()))) {
            return null;
        }
        return usuarioRepository.findById(aprobador).filter(Usuario::isActive).map(Usuario::getUserId).orElse(null);
    }

    /**
     * RF-068: Logistica fija la fecha comprometida de entrega. No reemplaza la
     * fecha requerida por el solicitante; se puede reprogramar mientras el REQ
     * siga en atencion y cada cambio queda auditado.
     */
    @Transactional
    public RequerimientoDto programarEntrega(UUID id, LocalDate fecha, UsuarioPrincipal principal) {
        if (!esLogisticaOAdmin(principal)) {
            throw ApiException.forbidden("No tienes permiso para programar entregas.");
        }
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        if (!ESTADOS_PROGRAMABLES.contains(r.getStatus())) {
            throw ApiException.badRequest("Solo se programa la entrega de un requerimiento aprobado y en atencion.");
        }
        if (fecha.isBefore(LocalDate.now(ZONA))) {
            throw ApiException.badRequest("La fecha programada no puede ser anterior a hoy.");
        }
        LocalDate anterior = r.getScheduledDeliveryDate();
        if (fecha.equals(anterior)) {
            return RequerimientoMapper.toDto(r);
        }
        r.setScheduledDeliveryDate(fecha);
        requerimientoRepository.save(r);

        Map<String, Object> antes = new HashMap<>();
        antes.put("fecha", anterior != null ? anterior.toString() : null);
        auditoria.registrar("REQUERIMIENTO", id, "PROGRAMAR_ENTREGA", antes,
                Map.of("fecha", fecha.toString()), principal.userId());
        return RequerimientoMapper.toDto(r);
    }

    /** Codigo legible unico por empresa: REQ-<empresa>-<yyyyMMdd>-<sufijo>. */
    private String generarRequestCode(String empresaCode) {
        String fecha = LocalDate.now().format(YYYYMMDD);
        String sufijo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "REQ-" + empresaCode + "-" + fecha + "-" + sufijo;
    }
}
