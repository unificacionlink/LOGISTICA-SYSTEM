import { http } from "./http";
import type {
  AccionProveedor,
  CargaMasiva,
  CondicionRecepcion,
  DespachoRequerimiento,
  DocumentoAdjunto,
  EstadoBien,
  EstadoIncidencia,
  Incidencia,
  ModalidadEntrega,
  NotaInventario,
  NotaInventarioDetalle,
  Orden,
  RecepcionOrden,
  Requerimiento,
  TipoAdjuntoEnvio,
  TipoIncidencia,
} from "./types";

// ---------- Recepcion de OC ----------

export function listarOrdenesPorRecibir() {
  return http.get<Orden[]>("/api/almacen/ordenes-por-recibir");
}

export function obtenerRecepcionOrden(orderId: string) {
  return http.get<RecepcionOrden>(`/api/almacen/ordenes/${orderId}/recepcion`);
}

export interface IncidenciaInput {
  issueType: TipoIncidencia;
  qtyAffected?: number;
  description: string;
  supplierAction?: AccionProveedor;
}

export interface RecepcionItemInput {
  orderItemId: string;
  qtyReceived: number;
  conditionStatus: CondicionRecepcion;
  observation?: string;
  incidencia?: IncidenciaInput;
}

export interface RegistrarRecepcionInput {
  items: RecepcionItemInput[];
  observation?: string;
  guia?: { series: string; number: string; issueDate: string };
}

export function registrarRecepcion(orderId: string, input: RegistrarRecepcionInput) {
  return http.post<RecepcionOrden>(`/api/almacen/ordenes/${orderId}/recepciones`, input);
}

export function actualizarIncidencia(id: string, status: EstadoIncidencia, supplierAction?: AccionProveedor | "") {
  return http.patch<Incidencia>(`/api/almacen/incidencias/${id}`, { status, supplierAction });
}

// ---------- Despacho / Nota de Inventario ----------

export function listarPorDespachar() {
  return http.get<Requerimiento[]>("/api/almacen/requerimientos-por-despachar");
}

export function obtenerDespacho(requestId: string) {
  return http.get<DespachoRequerimiento>(`/api/almacen/requerimientos/${requestId}/despacho`);
}

export interface CrearNotaInput {
  items: { requestItemId: string; qty: number }[];
  packageCount?: number;
  itemState?: EstadoBien;
  comments?: string;
}

export function crearNotaInventario(requestId: string, input: CrearNotaInput) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/requerimientos/${requestId}/notas`, input);
}

export function listarNotas() {
  return http.get<NotaInventario[]>("/api/almacen/notas");
}

export function obtenerNota(noteId: string) {
  return http.get<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}`);
}

// ---------- Envio ----------

export interface RegistrarEnvioInput {
  deliveryMode: ModalidadEntrega;
  carrierName?: string;
  trackingCode?: string;
  packageCount?: number;
  declaredWeight?: number;
  currencyCode?: string;
  shippingCostNet?: number;
  shippingTaxAmount?: number;
  shipDate?: string;
  expectedDate?: string;
}

export function registrarEnvio(noteId: string, input: RegistrarEnvioInput) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}/envio`, input);
}

export function adjuntarDocumentoEnvio(shipmentId: string, tipo: TipoAdjuntoEnvio, file: File) {
  const form = new FormData();
  form.append("tipo", tipo);
  form.append("file", file);
  return http.postForm<DocumentoAdjunto>(`/api/almacen/envios/${shipmentId}/documentos`, form);
}

export function confirmarRecepcionEnvio(shipmentId: string, observacion?: string) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/envios/${shipmentId}/confirmar-recepcion`, { observacion });
}

// ---------- Carga masiva de ingresos (RF-031) ----------

export function descargarPlantillaIngresos() {
  const hoy = new Date().toISOString().slice(0, 10);
  return http.descargar("/api/almacen/carga-masiva/plantilla", `PLANTILLA_INGRESO_ALMACEN_${hoy}.xlsx`);
}

function formArchivo(file: File) {
  const form = new FormData();
  form.append("file", file);
  return form;
}

export function validarCargaMasiva(file: File) {
  return http.postForm<CargaMasiva>("/api/almacen/carga-masiva/validar", formArchivo(file));
}

export function confirmarCargaMasiva(file: File) {
  return http.postForm<CargaMasiva>("/api/almacen/carga-masiva/confirmar", formArchivo(file));
}
