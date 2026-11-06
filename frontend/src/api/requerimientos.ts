import { http } from "./http";
import type { AvanceItem, Bandeja, CatalogoFormulario, Requerimiento, RequerimientoDetalle } from "./types";

export interface ItemInput {
  variantId: string;
  qtyRequested: number;
  observation?: string;
  technicalRequirement?: string;
}

export interface CrearRequerimientoInput {
  cecoId: string;
  priority: string;
  purpose: string;
  justification: string;
  requiredDate?: string;
  items: ItemInput[];
}

export function listarRequerimientos() {
  return http.get<Requerimiento[]>("/api/requerimientos");
}

/** Bandeja operativa (DEC-L048): vista, empresa, busqueda y pagina se resuelven en el servidor. */
export function obtenerBandeja(p: { vista?: string; empresa?: string; q?: string; pagina?: number }) {
  const qs = new URLSearchParams();
  if (p.vista) qs.set("vista", p.vista);
  if (p.empresa) qs.set("empresa", p.empresa);
  if (p.q?.trim()) qs.set("q", p.q.trim());
  if (p.pagina) qs.set("pagina", String(p.pagina));
  return http.get<Bandeja>(`/api/requerimientos/bandeja?${qs}`);
}

export function obtenerAvance(id: string) {
  return http.get<{ items: AvanceItem[] }>(`/api/requerimientos/${id}/avance`);
}

export function obtenerRequerimiento(id: string) {
  return http.get<RequerimientoDetalle>(`/api/requerimientos/${id}`);
}

export function obtenerCatalogoFormulario() {
  return http.get<CatalogoFormulario>("/api/requerimientos/catalogo");
}

export function crearRequerimiento(input: CrearRequerimientoInput) {
  return http.post<Requerimiento>("/api/requerimientos", input);
}

export function actualizarRequerimiento(id: string, input: CrearRequerimientoInput) {
  return http.put<Requerimiento>(`/api/requerimientos/${id}`, input);
}

/** Con control presupuestal, una excepcion exige justificacion (code EXCEPCION_PRESUPUESTAL, DEC-L038). */
export function enviarRequerimiento(id: string, justificacionPresupuestal?: string) {
  return http.post<Requerimiento>(`/api/requerimientos/${id}/enviar`, { justificacionPresupuestal });
}

export function programarEntrega(id: string, fecha: string) {
  return http.put<Requerimiento>(`/api/requerimientos/${id}/programacion`, { fecha });
}

export function subirImagenProducto(productId: string, file: File, enLote = false) {
  const form = new FormData();
  form.append("file", file);
  const ruta = `/api/requerimientos/productos/${productId}/imagen`;
  return enLote ? http.postFormEnLote<string>(ruta, form) : http.postForm<string>(ruta, form);
}
