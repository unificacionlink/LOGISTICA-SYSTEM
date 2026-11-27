import { API_URL, http } from "./http";

// Reportes de observaciones desde la pantalla (DEC-L104).

export type EstadoReporte = "PENDIENTE" | "EN_REVISION" | "RESUELTO" | "DESCARTADO";

export interface NuevoReporte {
  modulo: string | null;
  pantalla: string | null;
  ruta: string;
  /** Ruta de nombres de la zona senalada en el esqueleto; null = toda la pantalla. */
  zona: string | null;
  descripcion: string;
  viewport: string;
  captura: File | null;
}

export interface ReporteCreado {
  id: string;
  numero: number;
}

export interface ReportePropio {
  id: string;
  numero: number;
  en: string;
  pantalla: string | null;
  zona: string | null;
  descripcion: string;
  estado: EstadoReporte;
  respuesta: string | null;
}

export interface ReporteFila {
  id: string;
  numero: number;
  en: string;
  usuarioId: string;
  usuario: string;
  correo: string;
  roles: string | null;
  modulo: string | null;
  pantalla: string | null;
  ruta: string;
  zona: string | null;
  descripcion: string;
  tieneCaptura: boolean;
  navegador: string | null;
  viewport: string | null;
  estado: EstadoReporte;
  respuesta: string | null;
  revisadoPor: string | null;
  revisadoEn: string | null;
}

export interface PaginaReportes {
  filas: ReporteFila[];
  hayMas: boolean;
  conteo: { pendientes: number; enRevision: number; resueltos: number; descartados: number };
}

export const ESTADOS_REPORTE: Record<EstadoReporte, { label: string; tono: "amarillo" | "azul" | "verde" | "gris" }> = {
  PENDIENTE: { label: "Pendiente", tono: "amarillo" },
  EN_REVISION: { label: "En revisión", tono: "azul" },
  RESUELTO: { label: "Resuelto", tono: "verde" },
  DESCARTADO: { label: "Descartado", tono: "gris" },
};

export function codigoReporte(numero: number): string {
  return `OBS-${String(numero).padStart(4, "0")}`;
}

export function enviarReporte(r: NuevoReporte) {
  const form = new FormData();
  if (r.modulo) form.append("modulo", r.modulo);
  if (r.pantalla) form.append("pantalla", r.pantalla);
  form.append("ruta", r.ruta);
  if (r.zona) form.append("zona", r.zona);
  form.append("descripcion", r.descripcion);
  form.append("viewport", r.viewport);
  if (r.captura) form.append("captura", r.captura);
  return http.postForm<ReporteCreado>("/api/reportes", form);
}

export function misReportes() {
  return http.get<ReportePropio[]>("/api/reportes/mios");
}

function consulta(estado: string, q: string, pagina?: number): string {
  const p = new URLSearchParams();
  if (estado) p.set("estado", estado);
  if (q.trim()) p.set("q", q.trim());
  if (pagina !== undefined) p.set("pagina", String(pagina));
  return p.toString();
}

export function listarReportes(estado: string, q: string, pagina: number) {
  return http.get<PaginaReportes>(`/api/admin/reportes?${consulta(estado, q, pagina)}`);
}

export function revisarReporte(id: string, estado: EstadoReporte, respuesta: string) {
  return http.put<ReporteFila>(`/api/admin/reportes/${id}`, { estado, respuesta });
}

export function descargarReportesExcel(estado: string, q: string) {
  return http.descargar(`/api/admin/reportes/excel?${consulta(estado, q)}`, "observaciones.xlsx");
}

/** Captura del bucket privado: URL local del navegador, o null. */
export async function verCapturaReporte(id: string): Promise<string | null> {
  try {
    const res = await fetch(`${API_URL}/api/admin/reportes/${id}/captura`, { credentials: "include" });
    return res.ok ? URL.createObjectURL(await res.blob()) : null;
  } catch {
    return null;
  }
}
