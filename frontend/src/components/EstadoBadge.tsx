import type { EstadoRequerimiento } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

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

const ETIQUETA: Record<EstadoRequerimiento, string> = {
  BORRADOR: "Borrador",
  PENDIENTE_APROBACION: "Pendiente de aprobación",
  OBSERVADO: "Observado",
  VERIFICACION_STOCK: "Verificación de stock",
  EN_ATENCION: "En atención",
  ATENCION_PARCIAL: "Atención parcial",
  ATENCION_COMPLETA: "Atención completa",
  RECHAZADO: "Rechazado",
};

export default function EstadoBadge({ estado }: { estado: EstadoRequerimiento }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA[estado]}</Badge>;
}
