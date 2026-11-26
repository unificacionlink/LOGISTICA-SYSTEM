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

// ------------------------------------------------------------------
// Caja chica como modulo (DEC-L092): el solicitante registra lo que su proyecto
// compro con caja chica, con la factura obligatoria, sin requerimiento ni revision.

export type TipoComprobanteCajaChica = "FACTURA" | "BOLETA" | "TICKET" | "RECIBO" | "OTRO";

export const TIPOS_COMPROBANTE: { id: TipoComprobanteCajaChica; label: string }[] = [
  { id: "FACTURA", label: "Factura" },
  { id: "BOLETA", label: "Boleta" },
  { id: "TICKET", label: "Ticket" },
  { id: "RECIBO", label: "Recibo" },
  { id: "OTRO", label: "Otro" },
];

export interface LineaCompraCajaChica {
  sku: string | null;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  total: number;
}

export interface CompraModuloCajaChica {
  id: string;
  codigo: string;
  empresaCode: string;
  cecoCode: string;
  cecoNombre: string;
  solicitante: string;
  solicitanteId: string;
  fecha: string;
  proveedorRuc: string | null;
  proveedorNombre: string;
  tipoComprobante: TipoComprobanteCajaChica;
  serie: string | null;
  numero: string;
  total: number;
  facturaArchivoUrl: string | null;
  facturaUrl: string | null;
  nota: string | null;
  estado: "REGISTRADA" | "ANULADA";
  motivoAnulacion: string | null;
  registrada: string;
  lineas: LineaCompraCajaChica[];
}

export interface MontoCajaChica {
  clave: string;
  detalle: string | null;
  monto: number;
  compras: number;
}

export interface ConsolidadoCajaChica {
  total: number;
  compras: number;
  anuladas: number;
  meses: string[];
  porCeco: MontoCajaChica[];
  porMes: MontoCajaChica[];
  porSolicitante: MontoCajaChica[];
  detalle: CompraModuloCajaChica[];
}

export interface RegistrarCompraInput {
  cecoId: string;
  fecha: string;
  proveedorRuc: string | null;
  proveedorNombre: string;
  tipoComprobante: TipoComprobanteCajaChica;
  serie: string | null;
  numero: string;
  facturaUrl: string | null;
  nota: string | null;
  lineas: { variantId: string | null; descripcion: string | null; cantidad: number; precioUnitario: number }[];
}

/** La factura va como archivo (foto o PDF) o como enlace en `facturaUrl`: una de las dos es obligatoria. */
export function registrarCompraCajaChica(datos: RegistrarCompraInput, factura: File | null) {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
  if (factura) form.append("file", factura);
  return http.postForm<CompraModuloCajaChica>("/api/caja-chica/compras", form);
}

export function misComprasCajaChica() {
  return http.get<CompraModuloCajaChica[]>("/api/caja-chica/compras/mias");
}

export function anularCompraCajaChica(id: string, motivo: string) {
  return http.post<CompraModuloCajaChica>(`/api/caja-chica/compras/${id}/anular`, { motivo });
}

export interface FiltrosConsolidadoCajaChica {
  desde: string;
  hasta: string;
  empresa: string;
}

function consultaConsolidado(f: FiltrosConsolidadoCajaChica) {
  const p = new URLSearchParams({ desde: f.desde, hasta: f.hasta });
  if (f.empresa) p.set("empresa", f.empresa);
  return p.toString();
}

export function consolidadoCajaChica(f: FiltrosConsolidadoCajaChica) {
  return http.get<ConsolidadoCajaChica>(`/api/caja-chica/consolidado?${consultaConsolidado(f)}`);
}

export function descargarConsolidadoCajaChica(f: FiltrosConsolidadoCajaChica) {
  return http.descargar(`/api/caja-chica/consolidado/excel?${consultaConsolidado(f)}`, `CAJA_CHICA_${f.desde}_${f.hasta}.xlsx`);
}
