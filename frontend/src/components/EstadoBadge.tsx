import type { EstadoRequerimiento } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";
import { ETIQUETA_ESTADO } from "./formato";

const TONO: Record<EstadoRequerimiento, BadgeTone> = {
  BORRADOR: "gris",
  PENDIENTE_APROBACION: "amarillo",
  OBSERVADO: "rojo",
  VERIFICACION_STOCK: "azul",
  EN_ATENCION: "azul",
  ATENCION_PARCIAL: "amarillo",
  ATENCION_COMPLETA: "verde",
  RECHAZADO: "rojo",
};

export default function EstadoBadge({ estado }: { estado: EstadoRequerimiento }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA_ESTADO[estado]}</Badge>;
}
