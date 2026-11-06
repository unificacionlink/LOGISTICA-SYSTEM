import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronDown } from "lucide-react";
import { obtenerRequerimiento } from "../../api/requerimientos";
import type { Requerimiento, RequerimientoDetalle } from "../../api/types";
import { fmtCantidad, fmtDia } from "../AlmacenBadges";
import EstadoBadge from "../EstadoBadge";
import { EMPRESAS } from "../empresas";
import PrioridadBadge from "../PrioridadBadge";
import { Skeleton } from "../ui/Skeleton";
import { diasParaVencer } from "./bandeja";

/** Detalle rapido: se pide al abrir la fila por primera vez. */
function DetalleRapido({ id }: { id: string }) {
  const [detalle, setDetalle] = useState<RequerimientoDetalle | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    obtenerRequerimiento(id).then((res) => {
      if (res.ok && res.result) setDetalle(res.result);
      else setError(res.message);
    });
  }, [id]);

  if (error) return <p className="text-xs text-rose-700">{error}</p>;
  if (!detalle) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3 w-1/3" />
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-4/5" />
      </div>
    );
  }

  const { requerimiento: r, items } = detalle;
  return (
    <div className="grid gap-4 text-xs md:grid-cols-[14rem_minmax(0,1fr)]">
      <dl className="flex flex-col gap-2">
        <div>
          <dt className="rotulo">Propósito</dt>
          <dd className="text-slate-700">{r.purpose ?? "—"}</dd>
        </div>
        {r.scheduledDeliveryDate && (
          <div>
            <dt className="rotulo">Entrega programada</dt>
            <dd className="text-slate-700">{fmtDia(r.scheduledDeliveryDate)}</dd>
          </div>
        )}
        {r.justification && (
          <div>
            <dt className="rotulo">Justificación</dt>
            <dd className="line-clamp-4 text-slate-600">{r.justification}</dd>
          </div>
        )}
      </dl>
      <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white">
        {items.map((i) => (
          <li key={i.id} className="flex items-start justify-between gap-3 px-3 py-1.5">
            <div className="min-w-0">
              <p className="truncate text-slate-700" title={i.nombreProducto}>
                {i.nombreProducto}
                {i.atributo ? <span className="text-slate-400"> · {i.atributo}</span> : null}
              </p>
              <p className="font-mono text-[11px] text-slate-400">{i.codigoVariante ?? i.skuBase}</p>
            </div>
            <span className="shrink-0 tabular-nums text-slate-700">
              {fmtCantidad(i.cantidad)} <span className="text-slate-400">{i.unidadMedida}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Vencimiento({ r }: { r: Requerimiento }) {
  const dias = diasParaVencer(r);
  if (!r.requiredDate) return <span className="text-slate-400">Sin fecha</span>;
  const texto =
    dias == null
      ? null
      : dias < 0
        ? `Vencido hace ${-dias} ${dias === -1 ? "día" : "días"}`
        : dias === 0
          ? "Vence hoy"
          : dias === 1
            ? "Vence mañana"
            : dias <= 7
              ? `En ${dias} días`
              : null;
  const tono = dias == null ? "" : dias < 0 ? "text-rose-700" : dias <= 2 ? "text-amber-700" : "text-slate-500";
  return (
    <span className="flex flex-col leading-tight">
      <span className="tabular-nums text-slate-700">{fmtDia(r.requiredDate)}</span>
      {texto && <span className={`text-[11px] font-medium ${tono}`}>{texto}</span>}
    </span>
  );
}

const MARCA = new Map(EMPRESAS.map((e) => [e.code, e]));

export default function FilaRequerimiento({
  r,
  esGestor,
  conEmpresa,
}: {
  r: Requerimiento;
  esGestor: boolean;
  /** Con "Todas las empresas": cada fila lleva la marca de su empresa. */
  conEmpresa: boolean;
}) {
  const [abierto, setAbierto] = useState(false);
  const empresa = MARCA.get(r.empresaCode ?? "");
  const productos = r.resumenItems ?? "";
  const items = r.itemsCount ?? 0;

  return (
    <li className="relative bg-white">
      {conEmpresa && (
        <span aria-hidden="true" className={`absolute inset-y-0 left-0 w-[3px] ${empresa?.marca ?? "bg-slate-300"}`} />
      )}
      <div className="flex items-stretch">
        <Link
          to={`/requerimientos/${r.id}`}
          className="grid min-w-0 flex-1 grid-cols-1 gap-x-4 gap-y-1.5 py-3 pl-4 pr-2 transition-colors hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none md:grid-cols-[minmax(0,1fr)_minmax(0,15rem)_7.5rem_11rem] md:items-center"
        >
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[13px] font-medium text-slate-900">{r.code}</span>
              {conEmpresa && (
                <span className="text-[11px] font-semibold tracking-wide text-slate-500">
                  {empresa?.label ?? r.empresaCode}
                </span>
              )}
              {r.priority !== "NORMAL" && <PrioridadBadge prioridad={r.priority} />}
            </div>
            <p className="mt-0.5 truncate text-[13px] text-slate-600" title={productos}>
              {items > 0 ? (
                <>
                  {productos}
                  <span className="text-slate-400">
                    {" "}
                    · {items} {items === 1 ? "ítem" : "ítems"}
                  </span>
                </>
              ) : (
                <span className="text-slate-400">Sin ítems</span>
              )}
            </p>
          </div>
          <div className="min-w-0 text-[13px]">
            <p className="truncate text-slate-700" title={r.cecoNombre ?? ""}>
              {r.cecoNombre ?? "—"}
            </p>
            {esGestor && r.requesterNombre && <p className="truncate text-xs text-slate-500">{r.requesterNombre}</p>}
          </div>
          <div className="text-[13px]">
            <Vencimiento r={r} />
          </div>
          <div className="md:text-right">
            <EstadoBadge estado={r.status} />
          </div>
        </Link>
        <button
          type="button"
          onClick={() => setAbierto((v) => !v)}
          aria-expanded={abierto}
          aria-label={abierto ? `Ocultar detalle de ${r.code}` : `Ver detalle de ${r.code}`}
          className="flex w-10 shrink-0 items-center justify-center text-slate-400 transition-colors hover:bg-slate-50 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-400"
        >
          <ChevronDown className={`size-4 transition-transform duration-200 ${abierto ? "rotate-180" : ""}`} />
        </button>
      </div>
      {abierto && (
        <div className="border-t border-slate-100 bg-slate-50/70 px-4 py-3">
          <DetalleRapido id={r.id} />
        </div>
      )}
    </li>
  );
}
