import { createContext, useContext, type ReactNode } from "react";

/**
 * Ventana de confirmacion corporativa (RF-047) que reemplaza window.confirm.
 * Con `motivo`, la operacion es irreversible (RF-064): no se confirma sin
 * escribirlo y el texto vuelve al llamador para enviarlo al backend.
 *
 *   const confirmar = useConfirmar();
 *   const r = await confirmar({ titulo: "¿Anular la orden?", peligro: true, motivo: "Motivo de la anulación" });
 *   if (!r) return;           // cancelado
 *   await anularOrden(id, r.motivo);
 */
export interface OpcionesConfirmacion {
  titulo: string;
  mensaje?: ReactNode;
  /** Texto del boton principal (por defecto "Confirmar"). */
  confirmar?: string;
  peligro?: boolean;
  /** Etiqueta del motivo obligatorio; si se omite, no se pide motivo. */
  motivo?: string;
}

export type ResultadoConfirmacion = { motivo: string } | null;

export type Confirmar = (opciones: OpcionesConfirmacion) => Promise<ResultadoConfirmacion>;

export const ConfirmacionContext = createContext<Confirmar | null>(null);

export function useConfirmar(): Confirmar {
  const ctx = useContext(ConfirmacionContext);
  if (!ctx) throw new Error("useConfirmar debe usarse dentro de ConfirmacionProvider");
  return ctx;
}
