import type { Prioridad } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

const TONO: Record<Prioridad, BadgeTone> = {
  NORMAL: "gris",
  URGENTE: "amarillo",
  CRITICA: "rojo",
};

const ETIQUETA: Record<Prioridad, string> = {
  NORMAL: "Normal",
  URGENTE: "Urgente",
  CRITICA: "Crítica",
};

export default function PrioridadBadge({ prioridad }: { prioridad: Prioridad }) {
  return <Badge tone={TONO[prioridad]}>{ETIQUETA[prioridad]}</Badge>;
}
