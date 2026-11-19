import type { EstadoProveedor, ResultadoEvaluacion, Vigencia } from "../../api/types";
import Badge from "../ui/Badge";
import { etiquetaEstado, RESULTADO, VIGENCIA } from "./estados";

export function EstadoProveedorBadge({ status, activo }: { status: EstadoProveedor; activo: boolean }) {
  const e = etiquetaEstado(status, activo);
  return <Badge tone={e.tono}>{e.label}</Badge>;
}

export function VigenciaBadge({ vigencia }: { vigencia: Vigencia | "FALTA" }) {
  const v = VIGENCIA[vigencia];
  return <Badge tone={v.tono}>{v.label}</Badge>;
}

export function ResultadoBadge({ resultado }: { resultado: ResultadoEvaluacion }) {
  const r = RESULTADO[resultado];
  return <Badge tone={r.tono}>{r.label}</Badge>;
}
