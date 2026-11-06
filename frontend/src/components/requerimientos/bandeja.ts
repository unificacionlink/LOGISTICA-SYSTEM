import type { EstadoRequerimiento } from "../../api/types";

/**
 * Bandeja de Requerimientos (DEC-L048): vistas, conteos, busqueda y orden se
 * calculan en el servidor (BandejaService). Aqui solo queda la presentacion.
 */

const CERRADOS: EstadoRequerimiento[] = ["ATENCION_COMPLETA", "RECHAZADO"];

function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Dias que faltan para la fecha requerida (negativo = vencido); null si no tiene o ya se cerro. */
export function diasParaVencer(r: { requiredDate: string | null; status: EstadoRequerimiento }): number | null {
  if (!r.requiredDate || CERRADOS.includes(r.status)) return null;
  const [a, m, d] = r.requiredDate.split("-").map(Number);
  const [ha, hm, hd] = hoy().split("-").map(Number);
  return Math.round((Date.UTC(a, m - 1, d) - Date.UTC(ha, hm - 1, hd)) / 86_400_000);
}

/** "Vence mañana", "Vencido hace 2 días"... y su tono; null si no hay que avisar. */
export function textoVencimiento(dias: number | null): { texto: string; tono: string } | null {
  if (dias == null || dias > 7) return null;
  if (dias < 0) return { texto: `Vencido hace ${-dias} ${dias === -1 ? "día" : "días"}`, tono: "text-rose-700" };
  if (dias === 0) return { texto: "Vence hoy", tono: "text-amber-700" };
  if (dias === 1) return { texto: "Vence mañana", tono: "text-amber-700" };
  return { texto: `En ${dias} días`, tono: dias <= 2 ? "text-amber-700" : "text-slate-500" };
}

/** Colores del avance (DEC-L048), del mas cerca al mas lejos de la entrega. */
export const TRAMOS = [
  { clave: "entregado", label: "Entregado", color: "bg-emerald-600" },
  { clave: "enCamino", label: "En camino", color: "bg-blue-600" },
  { clave: "listo", label: "Listo en almacén", color: "bg-sky-400" },
  { clave: "enCompra", label: "En compra", color: "bg-amber-400" },
] as const;
