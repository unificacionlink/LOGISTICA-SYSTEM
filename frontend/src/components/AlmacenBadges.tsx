import type {
  AccionProveedor,
  EstadoEnvio,
  EstadoIncidencia,
  EstadoNota,
  EstadoRecepcion,
  ModalidadEntrega,
  TipoAdjuntoEnvio,
  TipoIncidencia,
} from "../api/types";
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

export const ETIQUETA_TIPO_INCIDENCIA: Record<TipoIncidencia, string> = {
  FALTANTE: "Faltante",
  DANO: "Daño",
  PRODUCTO_ERRADO: "Producto errado",
  OTRO: "Otro",
};

export const ETIQUETA_ACCION_PROVEEDOR: Record<AccionProveedor, string> = {
  REPONER: "Reponer",
  CAMBIO: "Cambio",
  NOTA_CREDITO: "Nota de crédito",
};

export const ETIQUETA_MODALIDAD: Record<ModalidadEntrega, string> = {
  AGENCIA: "Agencia",
  PROVEEDOR_ENTREGA: "Entrega del proveedor",
  RECOJO: "Recojo",
  TERRESTRE_NORMAL: "Terrestre",
  COURIER: "Courier",
  PROPIO: "Movilidad propia",
};

export const ETIQUETA_ADJUNTO: Record<TipoAdjuntoEnvio, string> = {
  GUIA: "Guía de remisión",
  TICKET_ENVIO: "Ticket de envío",
  CONFORMIDAD_ENTREGA: "Conformidad de entrega",
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

/** Cantidades sin ceros de relleno (12.000 -> 12). */
export function fmtCantidad(n: number | null | undefined): string {
  if (n === null || n === undefined) return "—";
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: 3 });
}

export function fmtFecha(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

/** Fecha sin hora ("2026-09-24"): se arma a mano porque new Date() la tomaria en UTC y en Lima mostraria el dia anterior. */
export function fmtDia(fecha: string | null | undefined): string {
  if (!fecha) return "—";
  const [a, m, d] = fecha.split("-");
  return `${d}/${m}/${a}`;
}

/** Hoy en formato yyyy-mm-dd segun la hora local (para inputs type="date"). */
export function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
