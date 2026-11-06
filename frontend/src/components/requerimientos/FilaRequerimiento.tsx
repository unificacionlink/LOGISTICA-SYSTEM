import { Link } from "react-router-dom";
import { Clock3 } from "lucide-react";
import type { FilaBandeja } from "../../api/types";
import { fmtDia } from "../formato";
import EstadoBadge from "../EstadoBadge";
import { EMPRESAS } from "../empresas";
import PrioridadBadge from "../PrioridadBadge";
import { accionPrincipal, etiquetaAccion, rutaAccion } from "./acciones";
import BarraAvance from "./BarraAvance";
import { diasParaVencer, textoVencimiento } from "./bandeja";

const MARCA = new Map(EMPRESAS.map((e) => [e.code, e]));

/** Mismo grid que el encabezado de columnas de la bandeja. */
export const GRID_FILA = "md:grid-cols-[minmax(0,1fr)_9.5rem_7.5rem_10.5rem]";

function titulo(f: FilaBandeja): { principal: string; extra: string | null } {
  const nombres = (f.resumenItems ?? "").split(" · ").filter(Boolean);
  if (nombres.length === 0) return { principal: "Sin productos", extra: null };
  return { principal: nombres[0], extra: f.itemsCount > 1 ? `+${f.itemsCount - 1} más` : null };
}

function TextoAvance({ f }: { f: FilaBandeja }) {
  const { entregado, enCamino, listo, enCompra } = f.avance;
  if (f.status === "BORRADOR" || f.status === "PENDIENTE_APROBACION" || f.status === "OBSERVADO") {
    return <span className="text-slate-400">Sin aprobar</span>;
  }
  if (f.status === "RECHAZADO") return <span className="text-slate-400">—</span>;
  if (entregado + enCamino + listo + enCompra === 0) return <span className="text-slate-400">Sin atender</span>;
  return (
    <span className="text-slate-500">
      {f.itemsEntregados}/{f.itemsCount} entregado{f.itemsCount === 1 ? "" : "s"}
    </span>
  );
}

/**
 * Fila de la bandeja (DEC-L048): lo pedido como titulo, empresa y codigo como
 * dato secundario, avance, vencimiento, estado y la siguiente accion. Clic en
 * la fila abre el panel lateral; el boton de accion lleva a su pantalla.
 */
export default function FilaRequerimiento({
  f,
  esGestor,
  conEmpresa,
  seleccionada,
  vista,
  onAbrir,
}: {
  f: FilaBandeja;
  esGestor: boolean;
  conEmpresa: boolean;
  seleccionada: boolean;
  vista: string;
  onAbrir: () => void;
}) {
  const empresa = MARCA.get(f.empresaCode);
  const { principal, extra } = titulo(f);
  const venc = textoVencimiento(diasParaVencer(f));
  const accion = accionPrincipal(f.acciones, vista);
  const otras = f.acciones.filter((a) => a !== accion);

  return (
    <li
      className={`relative flex flex-col md:flex-row md:items-stretch ${seleccionada ? "bg-brand-50/50" : "bg-white"}`}
    >
      {conEmpresa && (
        <span aria-hidden="true" className={`absolute inset-y-0 left-0 w-[3px] ${empresa?.marca ?? "bg-slate-300"}`} />
      )}
      <button
        type="button"
        onClick={onAbrir}
        aria-current={seleccionada || undefined}
        className={`grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 py-3 pl-4 pr-3 text-left transition-colors hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none md:items-center ${GRID_FILA}`}
      >
        <div className="col-span-2 min-w-0 md:col-span-1">
          <p className="flex min-w-0 items-center gap-2">
            <span className="truncate text-[13.5px] font-medium text-slate-900" title={f.resumenItems ?? ""}>
              {principal}
            </span>
            {extra && <span className="shrink-0 text-xs text-slate-400">{extra}</span>}
            {f.priority !== "NORMAL" && <PrioridadBadge prioridad={f.priority} />}
          </p>
          <p className="mt-0.5 flex min-w-0 items-center gap-1.5 text-xs text-slate-500">
            {conEmpresa && (
              <span className="flex shrink-0 items-center gap-1 font-medium text-slate-600">
                <span className={`size-1.5 rounded-full ${empresa?.marca ?? "bg-slate-400"}`} aria-hidden="true" />
                {empresa?.label ?? f.empresaCode}
                <span className="text-slate-300">·</span>
              </span>
            )}
            <span className="shrink-0 font-mono text-[11px] text-slate-400">{f.code}</span>
            <span className="text-slate-300">·</span>
            <span className="truncate" title={`${f.cecoCode ?? ""} ${f.cecoNombre ?? ""}`}>
              {f.cecoNombre ?? "—"}
              {esGestor && f.solicitante && <span className="text-slate-400"> · {f.solicitante}</span>}
            </span>
          </p>
        </div>
        <div className="col-span-2 flex flex-col gap-1 text-xs md:col-span-1">
          <BarraAvance avance={f.avance} />
          <TextoAvance f={f} />
        </div>
        <div className="flex items-center gap-2 text-[13px] md:flex-col md:items-start md:gap-0 md:leading-tight">
          {f.requiredDate ? (
            <span className="tabular-nums text-slate-700">{fmtDia(f.requiredDate)}</span>
          ) : (
            <span className="text-slate-400">Sin fecha</span>
          )}
          {venc && <span className={`text-[11px] font-medium ${venc.tono}`}>{venc.texto}</span>}
        </div>
        <div className="text-right">
          <EstadoBadge estado={f.status} />
        </div>
      </button>
      <div className="flex items-center gap-2 px-4 pb-3 empty:hidden md:w-44 md:shrink-0 md:justify-end md:pb-0 md:pl-0 md:pr-4">
        {accion &&
          (accion.espera ? (
            <Link
              to={rutaAccion(accion, f.id)}
              className="inline-flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-800"
            >
              <Clock3 className="size-3.5" />
              {etiquetaAccion(accion)}
            </Link>
          ) : (
            <Link
              to={rutaAccion(accion, f.id)}
              className="inline-flex h-7 items-center rounded-md border border-brand-200 bg-brand-50 px-2.5 text-xs font-medium text-brand-800 transition-colors hover:border-brand-300 hover:bg-brand-100"
            >
              {etiquetaAccion(accion)}
            </Link>
          ))}
        {otras.length > 0 && (
          <span className="text-[11px] text-slate-400" title={otras.map(etiquetaAccion).join(" · ")}>
            +{otras.length}
          </span>
        )}
      </div>
    </li>
  );
}
