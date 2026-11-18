import { useEffect, useRef, useState } from "react";
import { Clock3 } from "lucide-react";
import type { SlaRequerimiento } from "../../api/types";
import Flotante from "../ui/Flotante";
import { diasHabiles, fechaLarga, prioridad } from "./prioridades";

/**
 * Boton "Tiempo de atencion" (DEC-L068): cuanto demora cada prioridad en dias
 * habiles y la fecha tentativa si el requerimiento se envia hoy.
 */
export default function TiempoAtencion({
  sla,
  seleccionada,
  plazoProductos,
}: {
  sla: SlaRequerimiento | null;
  seleccionada: string;
  /** Mayor plazo de atencion (dias) de los productos del pedido, si alguno lo tiene (DEC-L035). */
  plazoProductos: number | null;
}) {
  const [abierto, setAbierto] = useState(false);
  const boton = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    const fuera = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!boton.current?.contains(t) && !panel.current?.contains(t)) setAbierto(false);
    };
    const escape = (e: KeyboardEvent) => e.key === "Escape" && setAbierto(false);
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", escape);
    };
  }, [abierto]);

  return (
    <>
      <button
        ref={boton}
        type="button"
        aria-expanded={abierto}
        aria-haspopup="dialog"
        onClick={() => setAbierto((v) => !v)}
        className="inline-flex items-center gap-1 font-medium text-brand-700 hover:text-brand-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
      >
        <Clock3 className="size-3.5" />
        Tiempo de atención
      </button>
      {abierto && (
        <Flotante
          ancla={boton}
          panelRef={panel}
          role="dialog"
          aria-label="Tiempo de atención"
          className="w-[21rem] rounded-lg border border-slate-200 bg-white p-3 shadow-flotante"
        >
          <p className="text-sm font-semibold text-slate-900">Tiempo de atención</p>
          <p className="mt-0.5 text-xs text-slate-500">
            Si lo envías hoy{sla ? `, ${fechaLarga(sla.hoy)}` : ""}
          </p>
          <table className="mt-2 w-full text-sm">
            <thead>
              <tr className="text-left text-[0.6875rem] uppercase tracking-wide text-slate-400">
                <th className="py-1 font-medium">Prioridad</th>
                <th className="py-1 font-medium">Plazo</th>
                <th className="py-1 text-right font-medium">Atención</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(sla?.plazos ?? []).map((p) => (
                <tr key={p.prioridad} className={p.prioridad === seleccionada ? "font-medium text-slate-900" : "text-slate-600"}>
                  <td className="py-1.5">
                    <span className={`rounded-full px-2 py-0.5 text-xs ${prioridad(p.prioridad).clase}`}>{p.etiqueta}</span>
                  </td>
                  <td className="py-1.5 tabular-nums">{diasHabiles(p.diasHabiles)}</td>
                  <td className="py-1.5 text-right tabular-nums">{fechaLarga(p.fechaTentativa)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-2 text-xs text-slate-500 whitespace-normal">
            Días hábiles: lunes a viernes, sin feriados. Corre desde que se envía a aprobación.
          </p>
          {plazoProductos != null && plazoProductos > 0 && (
            <p className="mt-1 text-xs text-amber-700 whitespace-normal">
              Algún producto de tu pedido tiene un plazo de {plazoProductos} {plazoProductos === 1 ? "día" : "días"}.
            </p>
          )}
        </Flotante>
      )}
    </>
  );
}
