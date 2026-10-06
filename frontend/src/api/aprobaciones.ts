import { http } from "./http";
import type { AprobacionPendiente, Requerimiento } from "./types";

export function listarPendientes() {
  return http.get<AprobacionPendiente[]>("/api/aprobaciones");
}

export function decidirAprobacion(
  approvalId: string,
  decision: "APROBADO" | "OBSERVADO" | "RECHAZADO",
  opts?: { comment?: string; decisionReason?: string },
) {
  return http.post<Requerimiento>(`/api/aprobaciones/${approvalId}/decidir`, {
    decision,
    comment: opts?.comment,
    decisionReason: opts?.decisionReason,
  });
}
