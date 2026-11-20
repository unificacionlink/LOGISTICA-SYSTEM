import { enCache, MINUTO } from "./cache";
import { http } from "./http";
import type {
  CandidatoRfq,
  Comparativo,
  EnlaceCotizacion,
  ComprasDetalle,
  Consolidado,
  ConsolidadoDetalle,
  Cotizacion,
  DocumentoSubido,
  Moneda,
  Necesidad,
  OcrComparativo,
  OcrCotizacion,
  ModoIgv,
  Orden,
  OrdenDetalle,
  PorEmitir,
  ProveedorProducto,
  Rfq,
  RfqDetalle,
} from "./types";

// ---------------------------------------------------------------- Comparativo (DEC-L047)

export function obtenerComparativo(ids: string[]) {
  return http.get<Comparativo>(`/api/compras/comparativo?ids=${ids.map(encodeURIComponent).join(",")}`);
}

/** Lectura OCR de una cotizacion: propone proveedor y precios, no guarda nada. Sin capa: cada tarjeta muestra su avance. */
/** supplierId: el proveedor ya elegido o el de la columna, para usar lo aprendido de el (DEC-L053). */
export function leerCotizacionComparativo(ids: string[], file: File, supplierId?: string | null) {
  const form = new FormData();
  form.append("ids", ids.join(","));
  form.append("file", file);
  if (supplierId) form.append("supplierId", supplierId);
  return http.postFormEnLote<OcrComparativo>("/api/compras/comparativo/ocr", form);
}

export interface RegistrarComparativoInput {
  supplierId: string;
  consolidationIds: string[];
  supplierQuoteNumber?: string;
  issueDate?: string;
  currencyCode: string;
  paymentCondition: string;
  deliveryMode: string;
  leadDays?: number;
  documentId?: string;
  reemplazaQuotationId?: string;
  leidaPorOcr: boolean;
  items: {
    consolidationId: string;
    unitCost: number;
    qty?: number;
    /** Linea del documento del proveedor que se confirmo para el producto (DEC-L053). */
    supplierDescription?: string;
    supplierCode?: string;
  }[];
}

export function registrarEnComparativo(input: RegistrarComparativoInput) {
  return http.post<Comparativo>("/api/compras/comparativo/cotizaciones", input);
}

export function adjudicarComparativo(
  consolidationIds: string[],
  elecciones: { consolidationId: string; quotationItemId: string }[],
) {
  return http.post<Comparativo>("/api/compras/comparativo/adjudicar", { consolidationIds, elecciones });
}

// ---------------------------------------------------------------- Lote 16 (DEC-L039)

export function listarNecesidades() {
  return http.get<Necesidad[]>("/api/compras/necesidades");
}

/** DEC-L054: separa unidades de una necesidad para darle otra ruta. */
export function dividirNecesidad(needId: string, cantidad: number) {
  return http.post<Necesidad[]>(`/api/compras/necesidades/${needId}/dividir`, { cantidad });
}

export function decidirRuta(needId: string, ruta: "COMPRA" | "CAJA_CHICA" | "STOCK") {
  return http.post<Necesidad>(`/api/compras/necesidades/${needId}/ruta`, { ruta });
}

export function listarConsolidados() {
  return http.get<Consolidado[]>("/api/compras/consolidados");
}

export function obtenerConsolidado(id: string) {
  return http.get<ConsolidadoDetalle>(`/api/compras/consolidados/${id}`);
}

/** supplierIds: a quienes pedirla (DEC-L082); sin ellos, a todos los habilitados. */
export function prepararSolicitudes(consolidationIds: string[], supplierIds?: string[]) {
  return http.post<Rfq[]>("/api/compras/consolidados/solicitudes", { consolidationIds, supplierIds });
}

/** Proveedores habilitados para los consolidados elegidos, primero los que cubren mas. */
export function candidatosRfq(consolidationIds: string[]) {
  return http.post<CandidatoRfq[]>("/api/compras/consolidados/candidatos", { consolidationIds });
}

export function adjudicar(consolidationId: string, quotationItemId: string) {
  return http.post<ConsolidadoDetalle>(`/api/compras/consolidados/${consolidationId}/adjudicar`, { quotationItemId });
}

export function anularConsolidado(consolidationId: string, motivo: string) {
  return http.post<ConsolidadoDetalle>(`/api/compras/consolidados/${consolidationId}/anular`, { motivo });
}

export function listarPorEmitir() {
  return http.get<PorEmitir[]>("/api/compras/por-emitir");
}

export function emitirOrden(quotationId: string) {
  return http.post<Orden>(`/api/compras/cotizaciones/${quotationId}/emitir-oc`);
}

export function confirmarEnvioRfq(rfqId: string, canal: "CORREO" | "WHATSAPP" | "OTRO") {
  return http.post<Rfq>(`/api/compras/rfq/${rfqId}/enviar`, { canal });
}

export function listarProveedoresProducto() {
  return http.get<ProveedorProducto[]>("/api/compras/proveedores-producto");
}

export function guardarProveedoresProducto(productId: string, supplierIds: string[]) {
  return http.put<ProveedorProducto[]>(`/api/compras/productos/${productId}/proveedores`, { supplierIds });
}

// ---------------------------------------------------------------- RFQ, cotizaciones y OC

export function listarRfqsAbiertas() {
  return http.get<Rfq[]>("/api/compras/rfqs-abiertas");
}

export function obtenerDetalleCompra(requestId: string) {
  return http.get<ComprasDetalle>(`/api/compras/requerimientos/${requestId}`);
}

export function obtenerRfq(rfqId: string) {
  return http.get<RfqDetalle>(`/api/compras/rfq/${rfqId}`);
}

// Enlace para que el proveedor cotice en linea (DEC-L052).
export function obtenerEnlaceRfq(rfqId: string) {
  return http.get<EnlaceCotizacion | null>(`/api/compras/rfq/${rfqId}/enlace`);
}

export function generarEnlaceRfq(rfqId: string) {
  return http.post<EnlaceCotizacion>(`/api/compras/rfq/${rfqId}/enlace`);
}

export function anularEnlaceRfq(rfqId: string) {
  return http.delete<null>(`/api/compras/rfq/${rfqId}/enlace`);
}

export function cerrarRfq(rfqId: string, motivo: string) {
  return http.post<Rfq>(`/api/compras/rfq/${rfqId}/cerrar`, { motivo });
}

export function subirDocumentoCotizacion(file: File) {
  const form = new FormData();
  form.append("file", file);
  return http.postForm<DocumentoSubido>("/api/compras/documentos", form);
}

/** OCR gratuito y local (Tesseract): solo propone datos, no reemplaza revisar el documento. */
export function extraerOcrCotizacion(rfqId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  return http.postForm<OcrCotizacion>(`/api/compras/rfq/${rfqId}/cotizaciones/ocr`, form);
}

export interface CotizacionItemInput {
  rfqItemId: string;
  qty: number;
  unitCost: number;
  offeredBrand?: string;
  offeredModel?: string;
}

export interface RegistrarCotizacionInput {
  supplierQuoteNumber?: string;
  issueDate?: string;
  expirationDate?: string;
  currencyCode: string;
  paymentCondition: string;
  deliveryMode: string;
  leadDays?: number;
  documentId?: string;
  items: CotizacionItemInput[];
}

export function registrarCotizacion(rfqId: string, input: RegistrarCotizacionInput) {
  return http.post<Cotizacion>(`/api/compras/rfq/${rfqId}/cotizaciones`, input);
}

export function descartarCotizacion(cotizacionId: string) {
  return http.post<Cotizacion>(`/api/compras/cotizaciones/${cotizacionId}/descartar`);
}

export function listarOrdenes() {
  return http.get<Orden[]>("/api/compras/ordenes");
}

export function obtenerOrden(orderId: string) {
  return http.get<OrdenDetalle>(`/api/compras/ordenes/${orderId}`);
}

// ---------------------------------------------------------------- Aprobacion, pago y liberacion (DEC-L061)

export function listarOrdenesPorAprobar() {
  return http.get<Orden[]>("/api/compras/ordenes/por-aprobar");
}

export function decidirOrden(orderId: string, decision: "APROBADO" | "RECHAZADO", motivo?: string) {
  return http.post<Orden>(`/api/compras/ordenes/${orderId}/decision`, { decision, motivo });
}

export function subirConstanciaPago(file: File) {
  const form = new FormData();
  form.append("file", file);
  form.append("tipo", "CONSTANCIA_PAGO");
  return http.postForm<DocumentoSubido>("/api/compras/documentos", form);
}

export function registrarPagoOrden(orderId: string, estado: "ANTICIPO" | "PAGADO", documentId: string) {
  return http.post<Orden>(`/api/compras/ordenes/${orderId}/pago`, { estado, documentId });
}

export function liberarOrden(orderId: string, nota?: string) {
  return http.post<Orden>(`/api/compras/ordenes/${orderId}/liberar`, { nota: nota || null });
}

export interface OrdenDirectaInput {
  empresaCode: string;
  supplierId: string;
  currencyCode: string;
  paymentCondition: string;
  deliveryMode: string;
  deliveryLocation: string | null;
  deliveryTerm: string | null;
  taxMode: ModoIgv;
  additionalSpecs: string | null;
  cecoId: string | null;
  requestCode: string | null;
  /** variantId = producto del catalogo; sin variantId, descripcion = servicio. */
  lineas: { variantId: string | null; descripcion: string | null; cantidad: number; precioUnitario: number }[];
}

/** OC directa sin cotizacion (DEC-L080): sale aprobada. */
export function crearOrdenDirecta(input: OrdenDirectaInput) {
  return http.post<Orden>("/api/compras/ordenes/directa", input);
}

export interface CondicionesOrden {
  taxMode: ModoIgv;
  deliveryTerm: string | null;
  deliveryLocation: string | null;
  additionalSpecs: string | null;
}

/** Solo mientras la OC espera aprobacion (DEC-L079). */
export function editarCondicionesOrden(orderId: string, c: CondicionesOrden) {
  return http.put<Orden>(`/api/compras/ordenes/${orderId}/condiciones`, c);
}

export function anularOrden(orderId: string, motivo: string) {
  return http.post<Orden>(`/api/compras/ordenes/${orderId}/anular`, { motivo });
}

/** Monedas: casi nunca cambian; una vez por pestana (24 h). */
export function listarMonedas() {
  return enCache("monedas", 24 * 60 * MINUTO, () => http.get<Moneda[]>("/api/compras/monedas"));
}
