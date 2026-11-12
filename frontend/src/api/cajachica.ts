import { http } from "./http";
import type {
  CompraCajaChica,
  DocumentoSubido,
  EvaluacionCajaChica,
  FormularioCajaChica,
  MotivoCajaChica,
  ReglaCajaChica,
} from "./types";

// Caja chica del proyecto (DEC-L054): el solicitante registra la compra y Logistica la regulariza.

export function listarCajaChica() {
  return http.get<CompraCajaChica[]>("/api/caja-chica");
}

export function evaluarCajaChica(requestId: string) {
  return http.get<EvaluacionCajaChica>(`/api/caja-chica/requerimientos/${requestId}/evaluacion`);
}

export function obtenerFormularioCajaChica(requestId: string) {
  return http.get<FormularioCajaChica>(`/api/caja-chica/requerimientos/${requestId}`);
}

export interface RegistrarCajaChicaInput {
  fechaCompra: string;
  proveedorRuc?: string;
  proveedorNombre: string;
  tipoComprobante: string;
  serie?: string;
  numero: string;
  moneda: string;
  /** Al corregir, sin archivo nuevo se conserva el anterior. */
  documentId?: string;
  items: { requestItemId: string; qty: number; costoUnitario: number }[];
}

export function registrarCajaChica(requestId: string, input: RegistrarCajaChicaInput) {
  return http.post<CompraCajaChica>(`/api/caja-chica/requerimientos/${requestId}`, input);
}

/** Corrige una compra observada: vuelve a revision de Logistica. */
export function corregirCajaChica(id: string, input: RegistrarCajaChicaInput) {
  return http.put<CompraCajaChica>(`/api/caja-chica/${id}`, input);
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
  EXCEPCION_AUTORIZADA: "Autorizada por Logística",
  SOLICITANTE: "Lo sugiere el solicitante",
};

export function subirComprobanteCajaChica(requestId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  return http.postForm<DocumentoSubido>(`/api/caja-chica/requerimientos/${requestId}/comprobante`, form);
}
