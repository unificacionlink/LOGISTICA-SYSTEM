import type { Necesidad } from "../../api/types";

export const ETIQUETA_SUGERENCIA: Record<NonNullable<Necesidad["suggestedReason"]>, string> = {
  BAJO_MONTO: "Bajo monto",
  MISMO_DIA: "Mismo día",
  FALLA_PROVEEDOR: "Falla de proveedor",
  SOLICITANTE: "Lo sugiere el solicitante",
};

export function cantidad(n: number): string {
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: 3 });
}

export function dinero(n: number | null | undefined, moneda?: string | null): string {
  if (n == null) return "—";
  return `${moneda ?? ""} ${Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`.trim();
}

/** "Requerimiento X" o "Requerimientos X, Y" segun cuantos codigos traiga. */
export function etiquetaRequerimientos(codigos: string | null): string {
  if (!codigos) return "Sin requerimiento";
  return codigos.includes(",") ? `Requerimientos ${codigos}` : `Requerimiento ${codigos}`;
}

/**
 * Precio unitario de una linea (misma regla que CotizacionOcrParser.unitarioDeLinea):
 * el importe que multiplicado por la cantidad da otro importe de la linea; si no,
 * el menor que no sea la propia cantidad.
 */
export function unitarioDeLinea(importes: number[], qty: number): number | null {
  if (importes.length === 0) return null;
  if (qty > 0 && qty !== 1) {
    for (const u of importes) {
      const esperado = u * qty;
      if (importes.some((t) => t !== u && esperado > 0 && Math.abs(esperado - t) <= esperado * 0.01)) return u;
    }
  }
  const candidatos = importes.filter((m) => m !== qty);
  return (candidatos.length ? candidatos : importes).reduce((a, b) => Math.min(a, b));
}
