import type { EstadoPresupuesto } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";
import { ETIQUETA_PRESUPUESTO } from "./presupuestos/formato";

const TONO: Record<EstadoPresupuesto, BadgeTone> = {
  BORRADOR: "gris",
  EMITIDO: "amarillo",
  APROBADO: "verde",
  OBSERVADO: "rojo",
  MODIFICADO: "azul",
  ANULADO: "gris",
};

export default function PresupuestoEstadoBadge({ estado }: { estado: EstadoPresupuesto }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA_PRESUPUESTO[estado]}</Badge>;
}
