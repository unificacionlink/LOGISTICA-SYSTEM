import type { EstadoConsolidado, OrigenConsolidado } from "../../api/types";
import Badge, { type BadgeTone } from "../ui/Badge";
import { cantidad } from "./formato";

const TONO: Record<EstadoConsolidado, BadgeTone> = {
  ABIERTA: "gris",
  EN_COTIZACION: "azul",
  ADJUDICADA: "amarillo",
  ORDENADA: "verde",
  ANULADA: "rojo",
};

const ETIQUETA: Record<EstadoConsolidado, string> = {
  ABIERTA: "Abierto",
  EN_COTIZACION: "En cotización",
  ADJUDICADA: "Adjudicado",
  ORDENADA: "Con OC",
  ANULADA: "Anulado",
};

export function ConsolidadoEstadoBadge({ estado }: { estado: EstadoConsolidado }) {
  return <Badge tone={TONO[estado]}>{ETIQUETA[estado]}</Badge>;
}

/** De que CECO (proyecto) y requerimiento viene cada parte del consolidado. */
export function OrigenesConsolidado({ origenes, max = 3 }: { origenes: OrigenConsolidado[]; max?: number }) {
  const visibles = origenes.slice(0, max);
  const resto = origenes.length - visibles.length;
  return (
    <ul className="flex flex-col gap-0.5 text-xs">
      {visibles.map((o) => (
        <li key={o.needId} className="flex items-baseline gap-1.5 whitespace-nowrap" title={`${o.cecoCode} ${o.cecoNombre}`}>
          <span className="font-mono font-medium text-slate-700">{o.cecoCode}</span>
          <span className="max-w-40 truncate text-slate-500">{o.cecoNombre}</span>
          <span className="font-mono text-slate-400">{o.requestCode}</span>
          <span className="text-slate-600">· {cantidad(o.qty)}</span>
        </li>
      ))}
      {resto > 0 && <li className="text-slate-400">+{resto} más</li>}
    </ul>
  );
}
