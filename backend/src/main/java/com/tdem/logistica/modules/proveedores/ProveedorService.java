package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoProveedor;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.GuardarProveedorRequest;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.SolicitudesProveedor.CategoriaItem;
import com.tdem.logistica.modules.proveedores.dto.SolicitudesProveedor.ProductoDisponible;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.ProveedorRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.limpio;

/**
 * Ficha del proveedor (DEC-L069): alta como BORRADOR, edicion auditada,
 * categorias y productos que ofrece. {@link #listar} no cambia: Compras sigue
 * recibiendo solo los proveedores activos (que siempre estan habilitados).
 */
@Service
@Transactional(readOnly = true)
public class ProveedorService {

    private static final Map<String, String> NOMBRE_ESTADO = Map.of(
            EstadoProveedor.BORRADOR, "en borrador",
            EstadoProveedor.PENDIENTE_REVISION, "pendiente de revision",
            EstadoProveedor.HABILITADO, "habilitado",
            EstadoProveedor.OBSERVADO, "observado",
            EstadoProveedor.RECHAZADO, "rechazado");

    /** Empresa por defecto de un proveedor nuevo (DEC-L081). */
    static final String EMPRESA_POR_DEFECTO = "TDEMSRL";

    private final ProveedorRepository proveedorRepository;
    private final EmpresaRepository empresaRepository;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public ProveedorService(ProveedorRepository proveedorRepository, EmpresaRepository empresaRepository,
                            JdbcTemplate jdbc, Auditoria auditoria) {
        this.proveedorRepository = proveedorRepository;
        this.empresaRepository = empresaRepository;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Proveedores para elegir en Compras: solo activos (contrato de siempre). */
    public List<ProveedorDto> listar(UsuarioPrincipal principal) {
        exigir(principal);
        return proveedorRepository.findAllByActiveTrueOrderByLegalName().stream()
                .map(ProveedorMapper::toDto)
                .toList();
    }

    public ProveedorDto obtener(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        return ProveedorMapper.toDto(buscar(id));
    }

    @Transactional
    public ProveedorDto crear(GuardarProveedorRequest request, UsuarioPrincipal principal) {
        exigir(principal);
        validarRucLibre(request.ruc(), null);

        Proveedor p = new Proveedor();
        p.setSupplierId(UUID.randomUUID());
        aplicar(p, request);
        p.setEmpresa(empresa(request.empresaCode() != null ? request.empresaCode() : EMPRESA_POR_DEFECTO));
        // Nace como solicitud: no aparece en Compras hasta que se habilite.
        p.setStatus(EstadoProveedor.BORRADOR);
        p.setActive(false);
        p.setCreatedAt(OffsetDateTime.now());
        p.setCreatedBy(principal.userId());
        proveedorRepository.saveAndFlush(p);
        auditoria.registrar("PROVEEDOR", p.getSupplierId(), "CREAR", null,
                Map.of("estado", p.getStatus(), "ruc", p.getRuc(), "razonSocial", p.getLegalName()),
                principal.userId());
        return ProveedorMapper.toDto(p);
    }

    @Transactional
    public ProveedorDto actualizar(UUID id, GuardarProveedorRequest request, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = buscar(id);
        if (EstadoProveedor.RECHAZADO.equals(p.getStatus())) {
            throw ApiException.badRequest("El proveedor esta rechazado: reabre la solicitud para editarlo.");
        }
        if (!p.getRuc().equals(request.ruc())) {
            validarRucLibre(request.ruc(), id);
        }
        Map<String, Object> antes = foto(p);
        aplicar(p, request);
        if (request.empresaCode() != null) {
            p.setEmpresa(empresa(request.empresaCode()));
        }
        p.setUpdatedAt(OffsetDateTime.now());
        proveedorRepository.save(p);
        Map<String, Object> despues = foto(p);
        Map<String, Object> cambiosAntes = new HashMap<>();
        Map<String, Object> cambiosDespues = new HashMap<>();
        for (Map.Entry<String, Object> e : despues.entrySet()) {
            if (!Objects.equals(e.getValue(), antes.get(e.getKey()))) {
                cambiosAntes.put(e.getKey(), antes.get(e.getKey()));
                cambiosDespues.put(e.getKey(), e.getValue());
            }
        }
        if (!cambiosDespues.isEmpty()) {
            auditoria.registrar("PROVEEDOR", id, "EDITAR", cambiosAntes, cambiosDespues, principal.userId());
        }
        return ProveedorMapper.toDto(p);
    }

    /** Reemplaza las categorias que atiende el proveedor (reutiliza la clasificacion del catalogo). */
    @Transactional
    public void guardarCategorias(UUID id, List<CategoriaItem> items, UsuarioPrincipal principal) {
        exigir(principal);
        buscar(id);
        Set<String> vistas = new LinkedHashSet<>();
        List<CategoriaItem> unicas = new ArrayList<>();
        for (CategoriaItem item : items) {
            if (vistas.add(item.productTypeId() + "|" + item.familyId())) {
                unicas.add(item);
            }
        }
        for (CategoriaItem item : unicas) {
            Integer valida = item.familyId() == null
                    ? jdbc.queryForObject("select count(*) from tipo_producto where product_type_id = ?",
                            Integer.class, item.productTypeId())
                    : jdbc.queryForObject("""
                            select count(*) from familia_producto where family_id = ? and product_type_id = ?""",
                            Integer.class, item.familyId(), item.productTypeId());
            if (valida == null || valida == 0) {
                throw ApiException.badRequest("Una de las categorias no existe en el catalogo.");
            }
        }
        List<String> antes = jdbc.queryForList(
                "select product_type_id || ':' || coalesce(family_id::text, '') from proveedor_categoria where supplier_id = ?",
                String.class, id);
        jdbc.update("delete from proveedor_categoria where supplier_id = ?", id);
        for (CategoriaItem item : unicas) {
            jdbc.update("""
                    insert into proveedor_categoria (supplier_id, product_type_id, family_id, created_by)
                    values (?, ?, ?, ?)""", id, item.productTypeId(), item.familyId(), principal.userId());
        }
        auditoria.registrar("PROVEEDOR", id, "CATEGORIAS", Map.of("categorias", antes),
                Map.of("categorias", unicas.stream().map(i -> i.productTypeId() + ":" +
                        (i.familyId() == null ? "" : i.familyId())).toList()), principal.userId());
    }

    /**
     * Productos que ofrece el proveedor: es la misma habilitacion por producto
     * que usa Compras (proveedor_producto, DEC-L039), vista desde el proveedor.
     * Compras solo cotiza con los activos, asi que un borrador no afecta nada.
     */
    @Transactional
    public void guardarProductos(UUID id, List<UUID> productIds, UsuarioPrincipal principal) {
        exigir(principal);
        buscar(id);
        Set<UUID> nuevos = new LinkedHashSet<>(productIds);
        if (!nuevos.isEmpty()) {
            Integer existentes = jdbc.queryForObject(
                    "select count(*) from producto where product_id in (" + marcas(nuevos.size()) + ")",
                    Integer.class, nuevos.toArray());
            if (existentes == null || existentes != nuevos.size()) {
                throw ApiException.badRequest("Uno de los productos no existe en el catalogo.");
            }
        }
        List<UUID> actuales = jdbc.queryForList(
                "select product_id from proveedor_producto where supplier_id = ?", UUID.class, id);
        List<UUID> quitar = actuales.stream().filter(p -> !nuevos.contains(p)).toList();
        List<UUID> agregar = nuevos.stream().filter(p -> !actuales.contains(p)).toList();
        if (quitar.isEmpty() && agregar.isEmpty()) {
            return;
        }
        for (UUID productId : quitar) {
            jdbc.update("delete from proveedor_producto where supplier_id = ? and product_id = ?", id, productId);
        }
        for (UUID productId : agregar) {
            jdbc.update("""
                    insert into proveedor_producto (product_id, supplier_id, created_by) values (?, ?, ?)
                    on conflict do nothing""", productId, id, principal.userId());
        }
        auditoria.registrar("PROVEEDOR", id, "PRODUCTOS", Map.of("quitados", quitar),
                Map.of("agregados", agregar), principal.userId());
    }

    /** Catalogo activo para asignar productos a un proveedor. */
    public List<ProductoDisponible> productosDisponibles(UsuarioPrincipal principal) {
        exigir(principal);
        return jdbc.query("""
                select p.product_id, p.sku_base, p.name, t.name, f.name
                  from producto p
                  join tipo_producto t on t.product_type_id = p.product_type_id
                  join familia_producto f on f.family_id = p.family_id
                 where p.active
                 order by p.sku_base""",
                (rs, n) -> new ProductoDisponible(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)));
    }

    private static String marcas(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    Proveedor buscar(UUID id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Proveedor no encontrado."));
    }

    /** RUC unico global, tambien contra rechazados: se reabre la solicitud en vez de duplicarla. */
    private void validarRucLibre(String ruc, UUID propio) {
        List<Map<String, Object>> otros = jdbc.queryForList(
                "select supplier_id, legal_name, status from proveedor where ruc = ?", ruc);
        for (Map<String, Object> o : otros) {
            if (!o.get("supplier_id").equals(propio)) {
                throw ApiException.badRequest("Ya existe un proveedor con ese RUC: " + o.get("legal_name")
                        + " (" + NOMBRE_ESTADO.getOrDefault(String.valueOf(o.get("status")), "registrado") + ").");
            }
        }
    }

    private Empresa empresa(String code) {
        return empresaRepository.findByCode(code.trim())
                .filter(Empresa::isActive)
                .orElseThrow(() -> ApiException.badRequest("Empresa no valida."));
    }

    private static void aplicar(Proveedor p, GuardarProveedorRequest r) {
        p.setRuc(r.ruc().trim());
        p.setLegalName(r.legalName().trim());
        p.setTradeName(limpio(r.tradeName()));
        p.setContactName(limpio(r.contactName()));
        p.setContactRole(limpio(r.contactRole()));
        p.setEmail(limpio(r.email()));
        p.setWhatsappPhone(limpio(r.whatsappPhone()));
        p.setPhone(limpio(r.phone()));
        p.setWebsite(limpio(r.website()));
        p.setAddress(limpio(r.address()));
        p.setCity(limpio(r.city()));
        p.setSupplierType(r.supplierType() != null ? r.supplierType() : "NO_CRITICO");
        p.setPreferredDeliveryMode(limpio(r.preferredDeliveryMode()));
        p.setOfferingDescription(limpio(r.offeringDescription()));
        p.setPaymentCondition(limpio(r.paymentCondition()));
        p.setCreditDays(r.creditDays());
        p.setLeadDays(r.leadDays());
        p.setWarrantyTerms(limpio(r.warrantyTerms()));
    }

    /** Campos editables, para auditar solo lo que cambio. */
    private static Map<String, Object> foto(Proveedor p) {
        Map<String, Object> m = new HashMap<>();
        m.put("ruc", p.getRuc());
        m.put("empresa", p.getEmpresa() != null ? p.getEmpresa().getCode() : null);
        m.put("razonSocial", p.getLegalName());
        m.put("nombreComercial", p.getTradeName());
        m.put("contacto", p.getContactName());
        m.put("cargoContacto", p.getContactRole());
        m.put("correo", p.getEmail());
        m.put("whatsapp", p.getWhatsappPhone());
        m.put("telefono", p.getPhone());
        m.put("web", p.getWebsite());
        m.put("direccion", p.getAddress());
        m.put("ciudad", p.getCity());
        m.put("tipo", p.getSupplierType());
        m.put("entrega", p.getPreferredDeliveryMode());
        m.put("ofrece", p.getOfferingDescription());
        m.put("condicionPago", p.getPaymentCondition());
        m.put("diasCredito", p.getCreditDays());
        m.put("plazoEntrega", p.getLeadDays());
        m.put("garantia", p.getWarrantyTerms());
        return m;
    }
}
