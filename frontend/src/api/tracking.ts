import { http } from "./http";
import type { Tracking } from "./types";

export function obtenerTracking(requestId: string) {
  return http.get<Tracking>(`/api/requerimientos/${requestId}/tracking`);
}

export function descargarTrackingExcel(requestId: string, codigo: string) {
  return http.descargar(`/api/requerimientos/${requestId}/tracking/excel`, `TRACKING_${codigo}.xlsx`);
}
