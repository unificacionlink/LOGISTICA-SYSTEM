import type { EstadoEnvio, EstadoIncidencia, EstadoNota, EstadoRecepcion } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

const RECEPCION: Record<EstadoRecepcion, [BadgeTone, string]> = {
  PARCIAL: ["amarillo", "Parcial"],
  COMPLETA: ["verde", "Completa"],
  OBSERVADA: ["rojo", "Observada"],
};

const NOTA: Record<EstadoNota, [BadgeTone, string]> = {
  BORRADOR: ["gris", "Borrador"],
  EMITIDA: ["azul", "Emitida"],
  DESPACHADA: ["amarillo", "Despachada"],
  RECEPCIONADA: ["verde", "Recepcionada"],
  ANULADA: ["rojo", "Anulada"],
};

const ENVIO: Record<EstadoEnvio, [BadgeTone, string]> = {
  PENDIENTE: ["gris", "Pendiente"],
  EN_TRANSITO: ["amarillo", "En tránsito"],
  RECEPCIONADO: ["verde", "Recepcionado"],
  OBSERVADO: ["rojo", "Observado"],
  CERRADO: ["verde", "Cerrado"],
};

const INCIDENCIA: Record<EstadoIncidencia, [BadgeTone, string]> = {
  ABIERTA: ["rojo", "Abierta"],
  EN_GESTION: ["amarillo", "En gestión"],
  CERRADA: ["verde", "Cerrada"],
};

export function RecepcionBadge({ estado }: { estado: EstadoRecepcion }) {
  const [tono, texto] = RECEPCION[estado];
  return <Badge tone={tono}>{texto}</Badge>;
}

export function NotaBadge({ estado }: { estado: EstadoNota }) {
  const [tono, texto] = NOTA[estado];
  return <Badge tone={tono}>{texto}</Badge>;
}

export function EnvioBadge({ estado }: { estado: EstadoEnvio }) {
  const [tono, texto] = ENVIO[estado];
  return <Badge tone={tono}>{texto}</Badge>;
}

export function IncidenciaBadge({ estado }: { estado: EstadoIncidencia }) {
  const [tono, texto] = INCIDENCIA[estado];
  return <Badge tone={tono}>{texto}</Badge>;
}
