import type { Prioridad } from "../../api/types";
import type { BadgeTone } from "../ui/Badge";

/**
 * Prioridades del requerimiento y su tiempo de atencion en dias habiles
 * (DEC-L068). La fecha tentativa la calcula el servidor (feriados de Peru).
 */
export const PRIORIDADES: { id: Prioridad; label: string; dias: number; tono: BadgeTone; clase: string }[] = [
  { id: "URGENTE", label: "Urgente", dias: 2, tono: "rojo", clase: "bg-rose-50 text-rose-700" },
  { id: "APROVISIONAMIENTO", label: "Aprovisionamiento", dias: 10, tono: "gris", clase: "bg-slate-100 text-slate-700" },
  { id: "IMPLEMENTACION", label: "Implementación", dias: 20, tono: "azul", clase: "bg-sky-50 text-sky-700" },
];

export const PRIORIDAD_POR_DEFECTO: Prioridad = "APROVISIONAMIENTO";

export function prioridad(id: string) {
  return PRIORIDADES.find((p) => p.id === id) ?? PRIORIDADES[1];
}

/** "mié., 02 dic. 2026" a partir de una fecha ISO (sin corrimiento de zona). */
export function fechaLarga(iso: string): string {
  return new Date(`${iso}T12:00:00`).toLocaleDateString("es-PE", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export const diasHabiles = (n: number) => `${n} ${n === 1 ? "día hábil" : "días hábiles"}`;
