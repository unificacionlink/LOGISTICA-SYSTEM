import type { AvanceBandeja } from "../../api/types";
import { TRAMOS } from "./bandeja";

function pct(n: number): string {
  return `${Math.round(n * 100)}%`;
}

/**
 * Barra apilada de avance: entregado, en camino, listo en almacen y en compra;
 * lo que queda en gris aun no tiene como atenderse.
 */
export default function BarraAvance({ avance, className = "" }: { avance: AvanceBandeja; className?: string }) {
  const sinCubrir = Math.max(0, 1 - avance.entregado - avance.enCamino - avance.listo - avance.enCompra);
  const titulo = [...TRAMOS.map((t) => `${t.label}: ${pct(avance[t.clave])}`), `Sin cubrir: ${pct(sinCubrir)}`].join(
    " · ",
  );
  return (
    <div
      role="img"
      aria-label={titulo}
      title={titulo}
      className={`flex h-1.5 w-full overflow-hidden rounded-full bg-slate-200 ${className}`}
    >
      {TRAMOS.map((t) =>
        avance[t.clave] > 0 ? (
          <span key={t.clave} className={`h-full ${t.color}`} style={{ width: pct(avance[t.clave]) }} />
        ) : null,
      )}
    </div>
  );
}
