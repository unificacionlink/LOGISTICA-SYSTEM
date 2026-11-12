import { enCache, luegoInvalidar, MINUTO } from "./cache";
import { http } from "./http";
import type { AvanceItem, Bandeja, CatalogoFormulario, Requerimiento, RequerimientoDetalle } from "./types";

export interface ItemInput {
  variantId: string;
  qtyRequested: number;
  observation?: string;
  technicalRequirement?: string;
  /** DEC-L054: unidades que se sugiere comprar con la caja chica del proyecto. */
  cantidadCajaChica?: number;
}

export interface CrearRequerimientoInput {
  cecoId: string;
  priority: string;
  purpose: string;
  justification: string;
  requiredDate?: string;
  items: ItemInput[];
  /** DEC-L054: nota para Logistica sobre los productos marcados para caja chica. */
  cajaChicaNota?: string;
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

/**
 * Catalogo de productos + familias + CECOs del usuario: en la pestana 10 min
 * (unos 300 KB); se invalida al crear o editar productos, variantes, plazos o
 * fotos. El stock no viene aqui: ver obtenerStockDisponible.
 */
export function obtenerCatalogoFormulario() {
  return enCache("catalogo", 10 * MINUTO, () => http.get<CatalogoFormulario>("/api/requerimientos/catalogo"));
}

/** Disponible del almacen central por variante (solo las que tienen); siempre fresco. */
export function obtenerStockDisponible() {
  return http.get<Record<string, number>>("/api/requerimientos/stock-disponible");
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
  return luegoInvalidar(enLote ? http.postFormEnLote<string>(ruta, form) : http.postForm<string>(ruta, form), "catalogo");
}
