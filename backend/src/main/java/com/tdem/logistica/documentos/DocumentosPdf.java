package com.tdem.logistica.documentos;

import com.tdem.logistica.documentos.PdfCorporativo.Campo;
import com.tdem.logistica.documentos.PdfCorporativo.Columna;
import com.tdem.logistica.modules.almacen.dto.EnvioDto;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDto;
import com.tdem.logistica.modules.almacen.dto.NotaItemDto;
import com.tdem.logistica.modules.compras.dto.OrdenDetalleDto;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.modules.compras.dto.OrdenItemDto;
import com.tdem.logistica.modules.compras.dto.RfqDetalleDto;
import com.tdem.logistica.modules.compras.dto.RfqDto;
import com.tdem.logistica.modules.compras.dto.RfqItemDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDetalleDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoItemDto;
import com.tdem.logistica.modules.tracking.dto.EntregaDto;
import com.tdem.logistica.modules.tracking.dto.EventoDto;
import com.tdem.logistica.modules.tracking.dto.HitoDto;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RF-038/082/083: contenido de cada PDF corporativo, armado desde los mismos
 * DTOs que alimentan las pantallas (asi el PDF nunca dice algo distinto).
 * Cada plantilla lleva su version; si cambia la maqueta de un documento, se
 * sube su version aqui.
 */
final class DocumentosPdf {

    static final String PLANTILLA_REQ = "PLT-REQ v1";
    static final String PLANTILLA_RFQ = "PLT-RFQ v1";
    static final String PLANTILLA_OC = "PLT-OC v1";
    static final String PLANTILLA_NI = "PLT-NI v1";
    static final String PLANTILLA_TRACKING = "PLT-TRK v1";

    static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DocumentosPdf() {
    }

    static byte[] requerimiento(RequerimientoDetalleDto d, String pie) {
        RequerimientoDto r = d.requerimiento();
        PdfCorporativo pdf = new PdfCorporativo("REQUERIMIENTO", r.code(), r.empresaCode(), PLANTILLA_REQ, pie);
        pdf.seccion("Datos del requerimiento");
        pdf.campos(List.of(
                Campo.de("Solicitante", r.requesterNombre()),
                Campo.de("Centro de costos", r.cecoNombre()),
                Campo.de("Estado", etiqueta(r.status())),
                Campo.de("Prioridad", etiqueta(r.priority())),
                Campo.de("Fecha requerida", fecha(r.requiredDate())),
                Campo.de("Creado", fechaHora(r.createdAt())),
                Campo.ancho("Propósito", r.purpose()),
                Campo.ancho("Justificación", r.justification())));

        pdf.seccion("Ítems solicitados");
        List<List<String>> filas = new ArrayList<>();
        int n = 1;
        for (RequerimientoItemDto i : d.items()) {
            filas.add(List.of(String.valueOf(n++), sku(i.skuBase(), i.codigoVariante()),
                    producto(i.nombreProducto(), i.atributo()), texto(i.unidadMedida()), cantidad(i.cantidad()),
                    unir(i.requisitoTecnico(), i.observacion())));
        }
        pdf.tabla(List.of(
                Columna.numero("#", 0.5f),
                Columna.texto("SKU", 1.6f),
                Columna.texto("Producto", 4f),
                Columna.texto("Unidad", 1f),
                Columna.numero("Cantidad", 1.1f),
                Columna.texto("Requisito / observación", 3f)), filas);
        return pdf.terminar();
    }

    static byte[] rfq(RfqDetalleDto d, String pie) {
        return rfq(d, pie, null);
    }

    /** @param urlEnlace enlace del proveedor para cotizar en linea (DEC-L052), o null. */
    static byte[] rfq(RfqDetalleDto d, String pie, String urlEnlace) {
        RfqDto q = d.rfq();
        PdfCorporativo pdf = new PdfCorporativo("SOLICITUD DE COTIZACIÓN", q.rfqCode(), d.empresaCode(), PLANTILLA_RFQ, pie);
        pdf.seccion("Proveedor y condiciones");
        pdf.campos(List.of(
                Campo.de("Proveedor", q.proveedorNombre()),
                Campo.de("RUC", d.proveedorRuc()),
                Campo.de("Fecha de emisión", fecha(q.issueDate())),
                Campo.de("Moneda solicitada", q.currencyRequested()),
                Campo.de("Plazo solicitado", q.requestedLeadDays() != null ? q.requestedLeadDays() + " días" : null),
                Campo.de("Condición de pago", etiqueta(q.paymentCondition())),
                Campo.de("Modalidad de entrega", etiqueta(q.deliveryMode())),
                Campo.de("Lugar de entrega", q.deliveryLocation())));

        pdf.seccion("Ítems a cotizar");
        List<List<String>> filas = new ArrayList<>();
        int n = 1;
        for (RfqItemDto i : d.items()) {
            filas.add(List.of(String.valueOf(n++), sku(i.skuBase(), i.codigoVariante()),
                    producto(i.nombreProducto(), i.atributo()), texto(i.unidadMedida()), cantidad(i.qty()),
                    texto(i.technicalSpecification())));
        }
        pdf.tabla(List.of(
                Columna.numero("#", 0.5f),
                Columna.texto("SKU", 1.6f),
                Columna.texto("Producto", 4f),
                Columna.texto("Unidad", 1f),
                Columna.numero("Cantidad", 1.1f),
                Columna.texto("Especificación técnica", 3f)), filas);
        if (urlEnlace != null) {
            pdf.enlaceConQr("Cotice en línea", "Escanee el código o abra el enlace para poner sus precios sobre "
                    + "estas mismas líneas. No necesita usuario ni contraseña.", urlEnlace);
        }
        pdf.parrafo("La presente solicitud no constituye una Orden de Compra. Indicar en la cotización precio "
                + "unitario, moneda, plazo de entrega, condición de pago y vigencia de la oferta. Si envía su propio "
                + "formato, cite en cada línea el SKU de esta solicitud.");
        return pdf.terminar();
    }

    static byte[] orden(OrdenDetalleDto d, String pie) {
        OrdenDto o = d.orden();
        PdfCorporativo pdf = new PdfCorporativo("ORDEN DE COMPRA", o.orderCode(), d.empresaCode(), PLANTILLA_OC, pie);
        pdf.seccion("Proveedor y condiciones");
        pdf.campos(List.of(
                Campo.de("Proveedor", o.proveedorNombre()),
                Campo.de("RUC", d.proveedorRuc()),
                Campo.de("Fecha de emisión", fecha(o.issueDate())),
                Campo.de("Requerimiento", o.requestCode()),
                Campo.de("Estado", etiqueta(o.status())),
                Campo.de("Moneda", o.currencyCode()),
                Campo.de("Condición de pago", etiqueta(o.paymentCondition())),
                Campo.de("Modalidad de entrega", etiqueta(o.deliveryMode())),
                Campo.de("Lugar de entrega", o.deliveryLocation())));

        pdf.seccion("Detalle");
        List<List<String>> filas = new ArrayList<>();
        int n = 1;
        for (OrdenItemDto i : d.items()) {
            String descripcion = i.nombreProducto() != null ? producto(i.nombreProducto(), i.atributo()) : texto(i.description());
            if (i.referenciaProveedor() != null && !i.referenciaProveedor().isBlank()) {
                descripcion += "\nRef. proveedor: " + i.referenciaProveedor();
            }
            filas.add(List.of(String.valueOf(n++), sku(i.skuBase(), i.codigoVariante()), descripcion,
                    texto(i.unidadMedida()), cantidad(i.qtyOrdered()), monto(i.unitCost()), monto(i.lineTotal())));
        }
        pdf.tabla(List.of(
                Columna.numero("#", 0.5f),
                Columna.texto("SKU", 1.6f),
                Columna.texto("Descripción", 4.4f),
                Columna.texto("Unidad", 1f),
                Columna.numero("Cantidad", 1.1f),
                Columna.numero("Costo unit.", 1.4f),
                Columna.numero("Total", 1.5f)), filas);
        pdf.totales(List.<String[]>of(new String[]{"Total " + texto(o.currencyCode()), monto(o.total())}));
        return pdf.terminar();
    }

    static byte[] notaInventario(NotaInventarioDetalleDto d, String pie) {
        NotaInventarioDto n = d.nota();
        PdfCorporativo pdf = new PdfCorporativo("NOTA DE INVENTARIO", n.inventoryNoteCode(), n.empresaCode(), PLANTILLA_NI, pie);
        pdf.seccion("Datos de la nota");
        pdf.campos(List.of(
                Campo.de("Requerimiento", n.requestCode()),
                Campo.de("Solicitante", n.solicitante()),
                Campo.de("Estado", etiqueta(n.status())),
                Campo.de("Tipo de movimiento", etiqueta(n.movementType())),
                Campo.de("Fecha de picking", fecha(n.pickingDate())),
                Campo.de("Responsable de picking", n.pickingUserName()),
                Campo.de("Origen", n.almacenOrigen()),
                Campo.de("Destino (CECO)", ceco(n.cecoCodigo(), n.cecoNombre())),
                Campo.de("Estado de artículos", etiqueta(n.itemState())),
                Campo.de("Total de ítems", cantidad(n.totalItemQty())),
                Campo.de("Total de bultos", n.packageCount() != null ? n.packageCount().toString() : null),
                Campo.de("Valor total", n.totalValueSnapshot() != null ? "PEN " + monto(n.totalValueSnapshot()) : null)));

        pdf.seccion("Productos despachados");
        List<List<String>> filas = new ArrayList<>();
        int pos = 1;
        for (NotaItemDto i : d.items()) {
            BigDecimal total = i.purchaseCostReference() != null && i.qty() != null
                    ? i.purchaseCostReference().multiply(i.qty()) : null;
            filas.add(List.of(String.valueOf(pos++), sku(i.skuBase(), i.codigoVariante()),
                    producto(i.nombreProducto(), i.atributo()), cantidad(i.qty()),
                    monto(i.purchaseCostReference()), monto(total)));
        }
        pdf.tabla(List.of(
                Columna.numero("#", 0.5f),
                Columna.texto("SKU", 1.6f),
                Columna.texto("Producto", 5f),
                Columna.numero("Cantidad", 1.1f),
                Columna.numero("Valor unit.", 1.4f),
                Columna.numero("Valor total", 1.5f)), filas);

        EnvioDto e = n.envio();
        if (e != null) {
            pdf.seccion("Envío");
            pdf.campos(List.of(
                    Campo.de("Modalidad", etiqueta(e.deliveryMode())),
                    Campo.de("Transportista", e.carrierName()),
                    Campo.de("Código de seguimiento", e.trackingCode()),
                    Campo.de("Fecha de envío", fecha(e.shipDate())),
                    Campo.de("Fecha estimada", fecha(e.expectedDate())),
                    Campo.de("Estado", etiqueta(e.status())),
                    Campo.de("Bultos", e.packageCount() != null ? e.packageCount().toString() : null),
                    Campo.de("Peso declarado", e.declaredWeight() != null ? cantidad(e.declaredWeight()) + " kg" : null),
                    Campo.de("Costo de envío (sin IGV)", e.shippingCostNet() != null ? texto(e.currencyCode()) + " " + monto(e.shippingCostNet()) : null),
                    Campo.de("Recepcionado", fechaHora(e.receivedAt())),
                    Campo.de("Recepcionado por", e.receivedByName()),
                    Campo.ancho("Observación de entrega", e.deliveryObservation())));
        }
        if (n.comments() != null && !n.comments().isBlank()) {
            pdf.seccion("Comentarios");
            pdf.parrafo(n.comments());
        }
        return pdf.terminar();
    }

    static byte[] tracking(TrackingDto t, String pie) {
        RequerimientoDto r = t.requerimiento();
        PdfCorporativo pdf = new PdfCorporativo("TRACKING DE REQUERIMIENTO", r.code(), r.empresaCode(), PLANTILLA_TRACKING, pie);
        pdf.seccion("Cabecera");
        pdf.campos(List.of(
                Campo.de("ID de seguimiento", r.code()),
                Campo.de("Fecha de entrega programada", fecha(t.entregaProgramada())),
                Campo.de("Nivel de urgencia", etiqueta(r.priority())),
                Campo.de("Centro de costos que recepciona", ceco(t.cecoCodigo(), r.cecoNombre())),
                Campo.de("Solicitante", r.requesterNombre()),
                Campo.de("Valor del pedido", t.valorPedido() != null ? "PEN " + monto(t.valorPedido()) : null),
                Campo.de("Estado", etiqueta(r.status())),
                Campo.de("Avance", cantidad(t.unidadesEntregadas()) + " de " + cantidad(t.unidadesSolicitadas())
                        + " unidades (" + t.porcentajeAtencion() + "%)"),
                Campo.de("Dirección de envío", t.direccionEnvio())));

        pdf.seccion("Hitos");
        List<List<String>> hitos = new ArrayList<>();
        for (HitoDto h : t.hitos()) {
            hitos.add(List.of(texto(h.etiqueta()), h.cumplido() ? "Cumplido" : "Pendiente",
                    fechaHora(h.fecha()), texto(h.detalle())));
        }
        pdf.tabla(List.of(
                Columna.texto("Hito", 2f),
                Columna.texto("Estado", 1.2f),
                Columna.texto("Fecha", 1.6f),
                Columna.texto("Detalle", 4.5f)), hitos);

        pdf.seccion("Notas de inventario y envíos");
        List<List<String>> entregas = new ArrayList<>();
        for (EntregaDto e : t.entregas()) {
            entregas.add(List.of(String.valueOf(e.posicion()), texto(e.notaCodigo()), fecha(e.fechaEnvio()),
                    monto(e.valorNota()), e.costoEnvioNeto() != null ? texto(e.moneda()) + " " + monto(e.costoEnvioNeto()) : "",
                    texto(e.itemsDespachados()), etiqueta(e.estadoEntrega())));
        }
        pdf.tabla(List.of(
                Columna.numero("Pos.", 0.6f),
                Columna.texto("Nota de inventario", 1.8f),
                Columna.texto("Fecha envío", 1.2f),
                Columna.numero("Valor NI", 1.2f),
                Columna.numero("Costo envío (sin IGV)", 1.4f),
                Columna.texto("Ítems despachados", 3.2f),
                Columna.texto("Estado de entrega", 1.4f)), entregas);

        pdf.seccion("Historial");
        List<List<String>> eventos = new ArrayList<>();
        for (EventoDto ev : t.eventos()) {
            eventos.add(List.of(fechaHora(ev.fecha()), texto(ev.etapa()), texto(ev.accion()),
                    texto(ev.usuario()), texto(ev.referencia())));
        }
        pdf.tabla(List.of(
                Columna.texto("Fecha", 1.4f),
                Columna.texto("Etapa", 1.35f),
                Columna.texto("Acción", 2.2f),
                Columna.texto("Usuario", 1.7f),
                Columna.texto("Referencia", 2.7f)), eventos);
        return pdf.terminar();
    }

    // ---------------------------------------------------------------- formato

    static String fechaHora(Instant i) {
        return i == null ? "" : FECHA_HORA.format(i.atZone(ZONA));
    }

    private static String fechaHora(OffsetDateTime d) {
        return d == null ? "" : FECHA_HORA.format(d.atZoneSameInstant(ZONA));
    }

    private static String fecha(LocalDate d) {
        return d == null ? "" : FECHA.format(d);
    }

    private static String cantidad(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private static String monto(BigDecimal n) {
        if (n == null) {
            return "";
        }
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(n.setScale(2, RoundingMode.HALF_UP));
    }

    /** Codigos de estado/catalogo (EN_ATENCION) como texto legible (En atencion). */
    private static String etiqueta(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "";
        }
        String t = codigo.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String sku(String skuBase, String codigoVariante) {
        return codigoVariante != null && !codigoVariante.isBlank() ? codigoVariante : texto(skuBase);
    }

    private static String producto(String nombre, String atributo) {
        return atributo != null && !atributo.isBlank() ? texto(nombre) + " (" + atributo + ")" : texto(nombre);
    }

    /**
     * "(ADM) ADMINISTRACION"; sin repetir el codigo si el nombre ya lo trae
     * como palabra ("0102 LIMA"), no como letras iniciales ("ADM" en "ADMINISTRACION").
     */
    private static String ceco(String codigo, String nombre) {
        boolean nombreTraeCodigo = nombre != null && (nombre.equals(codigo)
                || nombre.startsWith(codigo + " ") || nombre.startsWith(codigo + "-"));
        if (codigo == null || codigo.isBlank() || nombreTraeCodigo) {
            return texto(nombre);
        }
        return "(" + codigo + ") " + texto(nombre);
    }

    private static String unir(String a, String b) {
        boolean hayA = a != null && !a.isBlank();
        boolean hayB = b != null && !b.isBlank();
        if (hayA && hayB) {
            return a + "\n" + b;
        }
        return hayA ? a : (hayB ? b : "");
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }
}
