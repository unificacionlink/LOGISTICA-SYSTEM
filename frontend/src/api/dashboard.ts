import { http } from "./http";
import type { Dashboard } from "./types";

export function obtenerDashboard(dias: number, empresa: string | null) {
  const params = new URLSearchParams({ dias: String(dias) });
  if (empresa) params.set("empresa", empresa);
  return http.get<Dashboard>(`/api/dashboard?${params}`);
}
