import type { EstadoOrden } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

const TONO: Record<EstadoOrden, BadgeTone> = {
  EMITIDA: "azul",
  EN_ATENCION: "azul",
  RECEPCION_PARCIAL: "amarillo",
  RECEPCION_COMPLETA: "verde",
  PEND_VERIFICACION: "amarillo",
  VERIFICADA: "verde",
  ANULADA: "rojo",
};

const ETIQUETA: Record<EstadoOrden, string> = {
  EMITIDA: "Emitida",
  EN_ATENCION: "En atención",
  RECEPCION_PARCIAL: "Recepción parcial",
  RECEPCION_COMPLETA: "Recepción completa",
  PEND_VERIFICACION: "Pend. verificación",
  VERIFICADA: "Verificada",
  ANULADA: "Anulada",
};

export default function OrdenEstadoBadge({ estado }: { estado: EstadoOrden }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA[estado]}</Badge>;
}
