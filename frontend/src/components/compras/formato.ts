import type { Necesidad } from "../../api/types";

export const ETIQUETA_SUGERENCIA: Record<NonNullable<Necesidad["suggestedReason"]>, string> = {
  BAJO_MONTO: "Bajo monto",
  MISMO_DIA: "Mismo día",
  FALLA_PROVEEDOR: "Falla de proveedor",
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
