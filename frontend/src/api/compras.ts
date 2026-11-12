import { enCache, MINUTO } from "./cache";
import { http } from "./http";
import type {
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
export function leerCotizacionComparativo(ids: string[], file: File) {
  const form = new FormData();
  form.append("ids", ids.join(","));
  form.append("file", file);
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
  items: { consolidationId: string; unitCost: number; qty?: number }[];
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

export function decidirRuta(needId: string, ruta: "COMPRA" | "CAJA_CHICA") {
  return http.post<Necesidad>(`/api/compras/necesidades/${needId}/ruta`, { ruta });
}

export function listarConsolidados() {
  return http.get<Consolidado[]>("/api/compras/consolidados");
}

export function obtenerConsolidado(id: string) {
  return http.get<ConsolidadoDetalle>(`/api/compras/consolidados/${id}`);
}

export function prepararSolicitudes(consolidationIds: string[]) {
  return http.post<Rfq[]>("/api/compras/consolidados/solicitudes", { consolidationIds });
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

export function anularOrden(orderId: string, motivo: string) {
  return http.post<Orden>(`/api/compras/ordenes/${orderId}/anular`, { motivo });
}

/** Monedas: casi nunca cambian; una vez por pestana (24 h). */
export function listarMonedas() {
  return enCache("monedas", 24 * 60 * MINUTO, () => http.get<Moneda[]>("/api/compras/monedas"));
}
