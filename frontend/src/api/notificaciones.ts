import { http } from "./http";

/** Aviso de la campana (DEC-L076): cambios de estado del requerimiento de quien lo pidió. */
export interface Notificacion {
  id: string;
  requestId: string | null;
  requestCode: string | null;
  tipo:
    | "REQ_APROBADO"
    | "REQ_OBSERVADO"
    | "REQ_RECHAZADO"
    | "REQ_PARCIAL"
    | "REQ_COMPLETO"
    | "DESPACHO_ENVIADO"
    | "RECEPCION_REGISTRADA";
  titulo: string;
  detalle: string | null;
  creadaEn: string;
  leida: boolean;
}

export interface BandejaNotificaciones {
  noLeidas: number;
  items: Notificacion[];
}

export function listarNotificaciones() {
  return http.get<BandejaNotificaciones>("/api/notificaciones");
}

export function marcarNotificacionLeida(id: string) {
  return http.post<BandejaNotificaciones>(`/api/notificaciones/${id}/leida`);
}

export function marcarTodasLeidas() {
  return http.post<BandejaNotificaciones>("/api/notificaciones/leidas");
}
