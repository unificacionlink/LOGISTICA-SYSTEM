import { createContext, useContext } from "react";
import type { AprobacionPendiente, Requerimiento } from "../api/types";

export type Listener<T> = (payload: T) => void;

/** Un proveedor respondio por su enlace (DEC-L052); llega a quien genero el enlace. */
export interface CotizacionPorEnlace {
  rfqId: string;
  rfqCode: string;
  proveedor: string;
}

/** DEC-L054: novedad de la compra con caja chica del proyecto; llega al solicitante. */
export interface AvisoCajaChica {
  requestId: string;
  requestCode: string;
  evento: "AUTORIZADA" | "OBSERVADA" | "REGULARIZADA";
  detalle: string | null;
}

export interface RealtimeContextValue {
  onNuevaAprobacion: (cb: Listener<AprobacionPendiente>) => () => void;
  onMiRequerimientoDecidido: (cb: Listener<Requerimiento>) => () => void;
  onCotizacionPorEnlace: (cb: Listener<CotizacionPorEnlace>) => () => void;
  onCajaChica: (cb: Listener<AvisoCajaChica>) => () => void;
}

export const RealtimeContext = createContext<RealtimeContextValue | undefined>(undefined);

export function useRealtime() {
  const ctx = useContext(RealtimeContext);
  if (!ctx) throw new Error("useRealtime debe usarse dentro de RealtimeProvider.");
  return ctx;
}
