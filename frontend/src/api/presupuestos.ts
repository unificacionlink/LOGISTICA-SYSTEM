import { http } from "./http";
import type {
  EstadoPresupuesto,
  PeriodoPresupuestal,
  PeriodosPresupuestales,
  PresupuestoDetalle,
  PresupuestoResumen,
  ProductoPresupuesto,
} from "./types";

// Presupuestos semestrales por CECO (Lote 14, DEC-L036).

export function listarPeriodos() {
  return http.get<PeriodosPresupuestales>("/api/presupuestos/periodos");
}

export function crearPeriodo(cecoId: string, anio: number, semestre: number) {
  return http.post<PeriodoPresupuestal>("/api/presupuestos/periodos", { cecoId, anio, semestre });
}

export function cerrarPeriodo(periodId: string, cerrado: boolean) {
  return http.put<PeriodoPresupuestal>(`/api/presupuestos/periodos/${periodId}/cierre`, { cerrado });
}

export function listarPresupuestos(filtros: { anio?: number; semestre?: number; estado?: EstadoPresupuesto }) {
  const q = new URLSearchParams();
  if (filtros.anio) q.set("anio", String(filtros.anio));
  if (filtros.semestre) q.set("semestre", String(filtros.semestre));
  if (filtros.estado) q.set("estado", filtros.estado);
  const qs = q.toString();
  return http.get<PresupuestoResumen[]>(`/api/presupuestos${qs ? `?${qs}` : ""}`);
}

export function crearPresupuesto(periodId: string, observaciones: string) {
  return http.post<PresupuestoDetalle>("/api/presupuestos", { periodId, observaciones });
}

export function obtenerPresupuesto(id: string) {
  return http.get<PresupuestoDetalle>(`/api/presupuestos/${id}`);
}

export function catalogoPresupuesto(id: string) {
  return http.get<ProductoPresupuesto[]>(`/api/presupuestos/${id}/catalogo`);
}

export function guardarLineas(id: string, lineas: { productId: string; meses: number[] }[]) {
  return http.put<PresupuestoDetalle>(`/api/presupuestos/${id}/lineas`, { lineas });
}

export function emitirPresupuesto(id: string) {
  return http.post<PresupuestoDetalle>(`/api/presupuestos/${id}/emitir`, {});
}

export function decidirPresupuesto(id: string, decision: "APROBADO" | "OBSERVADO", motivo?: string) {
  return http.post<PresupuestoDetalle>(`/api/presupuestos/${id}/decidir`, { decision, motivo });
}

export function modificarPresupuesto(id: string) {
  return http.post<PresupuestoDetalle>(`/api/presupuestos/${id}/modificar`, {});
}

export function anularPresupuesto(id: string, motivo: string) {
  return http.post<PresupuestoDetalle>(`/api/presupuestos/${id}/anular`, { motivo });
}
