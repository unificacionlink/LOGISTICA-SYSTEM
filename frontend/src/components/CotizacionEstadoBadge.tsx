import type { EstadoCotizacion } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

const TONO: Record<EstadoCotizacion, BadgeTone> = {
  RECIBIDA: "azul",
  VALIDADA: "azul",
  DESCARTADA: "gris",
  SELECCIONADA: "verde",
};

const ETIQUETA: Record<EstadoCotizacion, string> = {
  RECIBIDA: "Recibida",
  VALIDADA: "Validada",
  DESCARTADA: "Descartada",
  SELECCIONADA: "Seleccionada",
};

export default function CotizacionEstadoBadge({ estado }: { estado: EstadoCotizacion }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA[estado]}</Badge>;
}
