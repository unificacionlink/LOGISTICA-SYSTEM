import { http } from "./http";
import type { CatalogoFormulario, Requerimiento, RequerimientoDetalle } from "./types";

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

export function enviarRequerimiento(id: string) {
  return http.post<Requerimiento>(`/api/requerimientos/${id}/enviar`);
}

export function programarEntrega(id: string, fecha: string) {
  return http.put<Requerimiento>(`/api/requerimientos/${id}/programacion`, { fecha });
}

export function subirImagenProducto(productId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  return http.postForm<string>(`/api/requerimientos/productos/${productId}/imagen`, form);
}
