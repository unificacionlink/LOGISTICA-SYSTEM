import { http } from "./http";
import type { Dashboard, DemandaDashboard, GastoDashboard, VentasDashboard } from "./types";

/** Filtros comunes de las pestañas del dashboard (DEC-L083). */
export interface FiltrosDashboard {
  dias: number;
  empresa: string | null;
  /** Solo Demanda: "t:<tipoId>" (familia) o "f:<familyId>" (subfamilia). */
  familia?: string;
}

function consulta({ dias, empresa, familia }: FiltrosDashboard): string {
  const params = new URLSearchParams({ dias: String(dias) });
  if (empresa) params.set("empresa", empresa);
  if (familia) params.set("familia", familia);
  return params.toString();
}

export function obtenerDashboard(dias: number, empresa: string | null) {
  return http.get<Dashboard>(`/api/dashboard?${consulta({ dias, empresa })}`);
}

export function obtenerDemanda(filtros: FiltrosDashboard) {
  return http.get<DemandaDashboard>(`/api/dashboard/demanda?${consulta(filtros)}`);
}

export function descargarDemanda(filtros: FiltrosDashboard) {
  const hoy = new Date().toISOString().slice(0, 10);
  return http.descargar(`/api/dashboard/demanda/excel?${consulta(filtros)}`, `DEMANDA_${hoy}.xlsx`);
}

export function obtenerGasto(dias: number, empresa: string | null) {
  return http.get<GastoDashboard>(`/api/dashboard/gasto?${consulta({ dias, empresa })}`);
}

/** DEC-L086: lo que TDEM vendio a GOYDEL y METRIN (Logistica, Gerencia y Admin). */
export function obtenerVentas(dias: number, empresa: string | null) {
  return http.get<VentasDashboard>(`/api/dashboard/ventas?${consulta({ dias, empresa })}`);
}

export function descargarVentas(dias: number, empresa: string | null) {
  const hoy = new Date().toISOString().slice(0, 10);
  return http.descargar(`/api/dashboard/ventas/excel?${consulta({ dias, empresa })}`, `VENTAS_EMPRESAS_${hoy}.xlsx`);
}
