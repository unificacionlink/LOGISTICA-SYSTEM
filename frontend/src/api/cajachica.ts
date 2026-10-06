import { http } from "./http";
import type { CompraCajaChica, DocumentoSubido, EvaluacionCajaChica, MotivoCajaChica, ReglaCajaChica } from "./types";

// Caja Chica como ruta de compra del requerimiento (DEC-L026).

export function listarCajaChica() {
  return http.get<CompraCajaChica[]>("/api/caja-chica");
}

export function evaluarCajaChica(requestId: string) {
  return http.get<EvaluacionCajaChica>(`/api/caja-chica/requerimientos/${requestId}/evaluacion`);
}

export interface RegistrarCajaChicaInput {
  motivo: MotivoCajaChica;
  motivoDetalle?: string;
  fechaCompra: string;
  proveedorRuc?: string;
  proveedorNombre: string;
  tipoComprobante: string;
  serie?: string;
  numero: string;
  moneda: string;
  documentId?: string;
  codigoExterno?: string;
  items: { requestItemId: string; qty: number; costoUnitario: number }[];
}

export function registrarCajaChica(requestId: string, input: RegistrarCajaChicaInput) {
  return http.post<CompraCajaChica>(`/api/caja-chica/requerimientos/${requestId}`, input);
}

export function regularizarCajaChica(id: string, codigoExterno: string) {
  return http.post<CompraCajaChica>(`/api/caja-chica/${id}/regularizar`, { codigoExterno });
}

export function observarCajaChica(id: string, motivo: string) {
  return http.post<CompraCajaChica>(`/api/caja-chica/${id}/observar`, { motivo });
}

export function obtenerReglaCajaChica() {
  return http.get<ReglaCajaChica>("/api/caja-chica/regla");
}

export function guardarReglaCajaChica(regla: {
  umbral: number;
  bajoMonto: boolean;
  mismoDia: boolean;
  fallaProveedor: boolean;
  excepcion: boolean;
}) {
  return http.put<ReglaCajaChica>("/api/caja-chica/regla", regla);
}

export const ETIQUETA_MOTIVO: Record<MotivoCajaChica, string> = {
  BAJO_MONTO: "Bajo monto",
  MISMO_DIA: "Atención el mismo día",
  FALLA_PROVEEDOR: "Falla de proveedor",
  EXCEPCION_AUTORIZADA: "Excepción autorizada",
};

export function subirComprobanteCajaChica(file: File) {
  const form = new FormData();
  form.append("file", file);
  form.append("tipo", "COMPROBANTE_CAJA_CHICA");
  return http.postForm<DocumentoSubido>("/api/compras/documentos", form);
}
