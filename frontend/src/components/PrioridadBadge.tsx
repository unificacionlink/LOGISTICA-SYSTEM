import type { Prioridad } from "../api/types";
import Badge from "./ui/Badge";
import { prioridad as datosPrioridad } from "./requerimientos/prioridades";

export default function PrioridadBadge({ prioridad }: { prioridad: Prioridad }) {
  const p = datosPrioridad(prioridad);
  return <Badge tone={p.tono}>{p.label}</Badge>;
}
