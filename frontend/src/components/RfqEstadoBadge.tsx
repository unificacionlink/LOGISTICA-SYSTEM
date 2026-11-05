import type { EstadoRfq } from "../api/types";
import Badge, { type BadgeTone } from "./ui/Badge";

const TONO: Record<EstadoRfq, BadgeTone> = {
  BORRADOR: "gris",
  ENVIADA: "azul",
  RESPONDIDA: "amarillo",
  CERRADA: "verde",
};

const ETIQUETA: Record<EstadoRfq, string> = {
  BORRADOR: "Por enviar",
  ENVIADA: "Enviada",
  RESPONDIDA: "Respondida",
  CERRADA: "Cerrada",
};

export default function RfqEstadoBadge({ estado }: { estado: EstadoRfq }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA[estado]}</Badge>;
}
