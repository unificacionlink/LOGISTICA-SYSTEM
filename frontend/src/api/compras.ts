import { http } from "./http";
import type {
  ComprasDetalle,
  Cotizacion,
  DocumentoSubido,
  Moneda,
  OcrCotizacion,
  Orden,
  OrdenDetalle,
  ProveedorHabitual,
  Requerimiento,
  Rfq,
  RfqDetalle,
} from "./types";

export function listarPendientesCompra() {
  return http.get<Requerimiento[]>("/api/compras/pendientes");
}

export function listarRfqsAbiertas() {
  return http.get<Rfq[]>("/api/compras/rfqs-abiertas");
}

export function listarProveedoresHabituales() {
  return http.get<ProveedorHabitual[]>("/api/compras/proveedores-habituales");
}

export function asignarProveedorHabitual(productId: string, supplierId: string | null) {
  return http.put<ProveedorHabitual>(`/api/compras/productos/${productId}/proveedor-habitual`, { supplierId });
}

export function obtenerDetalleCompra(requestId: string) {
  return http.get<ComprasDetalle>(`/api/compras/requerimientos/${requestId}`);
}

export interface RfqItemInput {
  requestItemId: string;
  qty: number;
  technicalSpecification?: string;
}

export interface CrearRfqInput {
  supplierId: string;
  paymentCondition?: string;
  deliveryMode?: string;
  deliveryLocation?: string;
  requestedLeadDays?: number;
  currencyRequested?: string;
  items: RfqItemInput[];
}

export function crearRfq(requestId: string, input: CrearRfqInput) {
  return http.post<Rfq>(`/api/compras/requerimientos/${requestId}/rfq`, input);
}

export function obtenerRfq(rfqId: string) {
  return http.get<RfqDetalle>(`/api/compras/rfq/${rfqId}`);
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

export function seleccionarCotizacion(cotizacionId: string) {
  return http.post<Orden>(`/api/compras/cotizaciones/${cotizacionId}/seleccionar`);
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

export function listarMonedas() {
  return http.get<Moneda[]>("/api/compras/monedas");
}
