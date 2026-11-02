import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronDown, Plus } from "lucide-react";
import { listarRequerimientos, obtenerRequerimiento } from "../api/requerimientos";
import type { Requerimiento, RequerimientoDetalle } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { fmtDia } from "../components/AlmacenBadges";
import EstadoBadge from "../components/EstadoBadge";
import { EMPRESAS } from "../components/empresas";
import PrioridadBadge from "../components/PrioridadBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

function fmtFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "short" });
}

function fmtCantidad(n: number): string {
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: 3 });
}

/** Detalle rapido de la tarjeta: se pide al abrirla por primera vez y queda en memoria. */
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
        <Skeleton className="h-3 w-2/3" />
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-4/5" />
      </div>
    );
  }

  const { requerimiento: r, items } = detalle;
  return (
    <div className="flex flex-col gap-2.5 text-xs">
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5">
        <div>
          <dt className="rotulo">Propósito</dt>
          <dd className="text-slate-700">{r.purpose ?? "—"}</dd>
        </div>
        <div>
          <dt className="rotulo">Fecha requerida</dt>
          <dd className="text-slate-700">{fmtDia(r.requiredDate)}</dd>
        </div>
      </dl>
      {r.justification && <p className="line-clamp-3 text-slate-600">{r.justification}</p>}
      <ul className="divide-y divide-slate-100 rounded-md border border-slate-100">
        {items.map((i) => (
          <li key={i.id} className="flex items-start justify-between gap-3 px-2.5 py-1.5">
            <div className="min-w-0">
              <p className="truncate text-slate-700" title={i.nombreProducto}>
                {i.nombreProducto}
                {i.atributo ? ` (${i.atributo})` : ""}
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

function Tarjeta({ r, esGestor }: { r: Requerimiento; esGestor: boolean }) {
  const [abierto, setAbierto] = useState(false);
  const idDetalle = `detalle-${r.id}`;
  return (
    <li className="rounded-md border border-slate-200 bg-white transition-[border-color,box-shadow] duration-200 hover:border-slate-300 hover:shadow-[0_2px_8px_rgb(27_26_24/0.05)]">
      <Link
        to={`/requerimientos/${r.id}`}
        className="group block rounded-t-md px-3.5 pb-2 pt-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
      >
        <div className="flex items-start justify-between gap-2">
          <span className="truncate font-mono text-xs font-medium text-slate-800 group-hover:text-slate-950">
            {r.code}
          </span>
          <PrioridadBadge prioridad={r.priority} />
        </div>
        <p className="mt-1.5 truncate text-[13px] text-slate-600" title={r.cecoNombre ?? ""}>
          {r.cecoNombre ?? "—"}
        </p>
        <div className="mt-2.5 flex items-center justify-between gap-2">
          <EstadoBadge estado={r.status} />
          <span className="shrink-0 text-[11px] text-slate-400">{fmtFecha(r.createdAt)}</span>
        </div>
        {esGestor && r.requesterNombre && (
          <p className="mt-2 truncate border-t border-slate-100 pt-2 text-xs text-slate-500">{r.requesterNombre}</p>
        )}
      </Link>
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        aria-controls={idDetalle}
        className="flex w-full items-center justify-center gap-1 rounded-b-md border-t border-slate-100 py-1 text-[11px] font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
      >
        {abierto ? "Ocultar detalle" : "Ver detalle"}
        <ChevronDown className={`size-3.5 transition-transform duration-200 ${abierto ? "rotate-180" : ""}`} />
      </button>
      {abierto && (
        <div id={idDetalle} className="border-t border-slate-100 px-3.5 py-3">
          <DetalleRapido id={r.id} />
        </div>
      )}
    </li>
  );
}

export default function Requerimientos() {
  const { tieneAlgunRol } = useAuth();
  const esGestor = tieneAlgunRol("LOGISTICA", "ADMIN");
  // Quien ve todo (Logistica/Admin/Aprobador/Gerencia) ve las 3 columnas aunque alguna este vacia;
  // el solicitante solo las empresas donde tiene requerimientos.
  const veTodo = esGestor || tieneAlgunRol("APROBADOR", "GERENCIA");
  const [items, setItems] = useState<Requerimiento[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    listarRequerimientos()
      .then((res) => {
        if (res.ok) setItems(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

  const grupos = useMemo(() => {
    const extra = [...new Set(items.map((r) => r.empresaCode ?? "SIN_EMPRESA"))]
      .filter((c) => !EMPRESAS.some((e) => e.code === c))
      .map((c) => ({ code: c, label: c === "SIN_EMPRESA" ? "Sin empresa" : c, marca: "bg-slate-400" }));
    return [...EMPRESAS, ...extra]
      .map((e) => ({ ...e, filas: items.filter((r) => (r.empresaCode ?? "SIN_EMPRESA") === e.code) }))
      .filter((g) => veTodo || g.filas.length > 0);
  }, [items, veTodo]);

  // Tantas columnas como empresas (3 normalmente); en pantallas angostas se apilan.
  const columnas = cargando ? EMPRESAS.length : Math.max(grupos.length, 1);
  const gridCols = columnas >= 3 ? "lg:grid-cols-3" : columnas === 2 ? "lg:grid-cols-2" : "lg:grid-cols-1 lg:max-w-xl";

  return (
    <div>
      <div>
        <PageHeader
          title="Requerimientos"
          subtitle={
            esGestor
              ? "Bandeja de Logística: todos los requerimientos, por empresa."
              : "Tus solicitudes de productos y su seguimiento."
          }
          actions={
            <Button href="/requerimientos/nuevo" icon={<Plus className="size-4" />}>
              Nuevo requerimiento
            </Button>
          }
        />
        {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      </div>

      {!cargando && !error && items.length === 0 && !veTodo ? (
        <p className="py-10 text-sm text-slate-500">
          Aún no tienes requerimientos. Crea el primero con el botón "Nuevo requerimiento".
        </p>
      ) : (
        <div className={`grid grid-cols-1 items-start gap-4 ${gridCols}`}>
          {(cargando ? EMPRESAS.map((e) => ({ ...e, filas: [] as Requerimiento[] })) : grupos).map((g) => {
            const pendientes = g.filas.filter((r) => r.status === "PENDIENTE_APROBACION").length;
            return (
              <section
                key={g.code}
                aria-labelledby={`col-${g.code}`}
                className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-slate-200 bg-slate-50"
              >
                <header className="relative flex items-center gap-3 bg-brand-900 px-4 pb-3 pt-3.5 text-white">
                  <span className={`absolute inset-x-0 top-0 h-1 ${g.marca}`} aria-hidden="true" />
                  <h2 id={`col-${g.code}`} className="text-[15px] font-semibold tracking-[0.04em]">
                    {g.label}
                  </h2>
                  <span className="rounded-md bg-white/15 px-2 py-0.5 font-mono text-xs tabular-nums">
                    {cargando ? "–" : g.filas.length}
                  </span>
                  {pendientes > 0 && <span className="ml-auto text-xs text-white/70">{pendientes} por aprobar</span>}
                </header>

                {/* Cada columna se desplaza sola, asi las 3 quedan a la vista a la vez. */}
                <div className="max-h-[calc(100dvh-15rem)] overflow-y-auto p-2.5">
                  {cargando ? (
                    <div className="flex flex-col gap-2">
                      {[0, 1, 2].map((k) => (
                        <div key={k} className="flex flex-col gap-2.5 rounded-md border border-slate-200 bg-white px-3.5 py-3">
                          <Skeleton className="h-3.5 w-1/2" />
                          <Skeleton className="h-3.5 w-4/5" />
                          <Skeleton className="h-4 w-1/3" />
                        </div>
                      ))}
                    </div>
                  ) : g.filas.length === 0 ? (
                    <p className="px-1.5 py-6 text-center text-sm text-slate-400">Sin requerimientos de {g.label}.</p>
                  ) : (
                    <ul className="flex flex-col gap-2">
                      {g.filas.map((r) => (
                        <Tarjeta key={r.id} r={r} esGestor={esGestor} />
                      ))}
                    </ul>
                  )}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
