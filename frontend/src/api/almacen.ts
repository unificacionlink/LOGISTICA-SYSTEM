import { http } from "./http";
import type {
  AccionObservado,
  AccionProveedor,
  CargaMasiva,
  CondicionRecepcion,
  DiferenciasAlmacen,
  DespachoRequerimiento,
  DocumentoAdjunto,
  EntregaDirecta,
  EstadoBien,
  EstadoIncidencia,
  Incidencia,
  ModalidadEntrega,
  NotaInventario,
  ResultadoRecepcion,
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

/** DEC-L088: anula una nota sin envio, con motivo. */
export function anularNota(noteId: string, motivo: string) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}/anular`, { motivo });
}

export function obtenerNota(noteId: string) {
  return http.get<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}`);
}

// ---------- Preparacion (ALM-02) ----------

export function confirmarPickingNota(noteId: string) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}/picking`, {});
}

/** Bultos y enlace de la evidencia del embalaje (V38.7). */
export function embalarNota(noteId: string, bultos: number, evidencia: string) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/notas/${noteId}/embalaje`, { bultos, evidencia });
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

// ---------- Recepcion en destino y diferencias (ALM-03) ----------

export interface RecepcionDestinoInput {
  clientToken: string;
  items: { issueItemId: string; qty: number }[];
  resultado: ResultadoRecepcion;
  observacion?: string;
  enlaceConformidad?: string;
}

// ---------- Entrega directa del proveedor al CECO (ALM-04, DEC-L084) ----------

export function obtenerEntregaDirecta(orderId: string) {
  return http.get<EntregaDirecta>(`/api/almacen/ordenes/${orderId}/entrega-directa`);
}

export function entregasDirectasDelRequerimiento(requestId: string) {
  return http.get<EntregaDirecta[]>(`/api/almacen/requerimientos/${requestId}/entregas-directas`);
}

export interface EntregaDirectaInput {
  cecoId: string;
  guia: { series: string; number: string; issueDate: string };
  enlaceConformidad?: string;
  observation?: string;
  lineas: {
    orderItemId: string;
    needId: string | null;
    requestItemId: string | null;
    qty: number;
    incidencia: {
      issueType: TipoIncidencia;
      qtyAffected: number | null;
      description: string;
      supplierAction: AccionProveedor | null;
    } | null;
  }[];
}

/** La constancia firmada va como archivo; si no hay archivo, su enlace. */
export function registrarEntregaDirecta(orderId: string, datos: EntregaDirectaInput, constancia: File | null) {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
  if (constancia) form.append("file", constancia);
  return http.postForm<EntregaDirecta>(`/api/almacen/ordenes/${orderId}/entrega-directa`, form);
}

/** La nota firmada va como archivo; si no hay archivo, el enlace de la conformidad firmada. */
export function recibirEnDestino(shipmentId: string, datos: RecepcionDestinoInput, notaFirmada: File | null) {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
  if (notaFirmada) form.append("file", notaFirmada);
  return http.postForm<NotaInventarioDetalle>(`/api/almacen/envios/${shipmentId}/recepcion`, form);
}

export function resolverObservado(
  shipmentId: string,
  input: { issueItemId: string; qty: number; accion: AccionObservado; evidencia: string },
) {
  return http.post<NotaInventarioDetalle>(`/api/almacen/envios/${shipmentId}/resolucion`, input);
}

export function listarDiferencias() {
  return http.get<DiferenciasAlmacen>("/api/almacen/diferencias");
}

export function recibirDevolucion(resolutionId: string, evidencia: string) {
  return http.post<DiferenciasAlmacen>(`/api/almacen/devoluciones/${resolutionId}/recibir`, { evidencia });
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
