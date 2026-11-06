import { createContext, useContext } from "react";
import type { AprobacionPendiente, Requerimiento } from "../api/types";

export type Listener<T> = (payload: T) => void;

export interface RealtimeContextValue {
  onNuevaAprobacion: (cb: Listener<AprobacionPendiente>) => () => void;
  onMiRequerimientoDecidido: (cb: Listener<Requerimiento>) => () => void;
}

export const RealtimeContext = createContext<RealtimeContextValue | undefined>(undefined);

export function useRealtime() {
  const ctx = useContext(RealtimeContext);
  if (!ctx) throw new Error("useRealtime debe usarse dentro de RealtimeProvider.");
  return ctx;
}
