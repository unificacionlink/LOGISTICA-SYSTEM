import { Link } from "react-router-dom";
import type { ExcepcionPresupuestal } from "../../api/types";
import EstadoBadge from "../EstadoBadge";
import { textoDesviacion } from "./formato";

/** Requerimientos del semestre enviados con excepción justificada (alertas internas de la V38.7, REQ-03). */
export default function ExcepcionesPresupuesto({ excepciones }: { excepciones: ExcepcionPresupuestal[] }) {
  if (excepciones.length === 0) return null;
  return (
    <details className="mb-4 rounded-xl border border-amber-200 bg-amber-50/40">
      <summary className="cursor-pointer px-5 py-3 text-sm font-medium text-amber-800">
        Excepciones presupuestales ({excepciones.length})
      </summary>
      <ul className="flex flex-col divide-y divide-amber-100 px-5 pb-3">
        {excepciones.map((e) => (
          <li key={e.requestId} className="py-2.5 text-sm text-slate-700">
            <div className="flex flex-wrap items-center gap-2">
              <Link to={`/requerimientos/${e.requestId}`} className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700">
                {e.requestCode}
              </Link>
              <EstadoBadge estado={e.estadoRequerimiento} />
              <span className="text-xs text-slate-500">
                {e.solicitante ?? "—"} · {new Date(e.fecha).toLocaleDateString("es-PE")}
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-600">{e.desviaciones.map(textoDesviacion).join(" · ")}</p>
            <p className="mt-0.5 text-xs text-slate-500">Justificación: {e.justificacion}</p>
          </li>
        ))}
      </ul>
    </details>
  );
}
