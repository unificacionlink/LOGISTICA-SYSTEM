import { Link } from "react-router-dom";
import { ArrowRight, Inbox, Plus } from "lucide-react";
import { obtenerMiResumen } from "../../api/requerimientos";
import type { EstadoRequerimiento } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import EstadoBadge from "../EstadoBadge";
import { fmtCantidad, fmtFecha } from "../formato";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import { Skeleton } from "../ui/Skeleton";

/** Los estados del requerimiento, agrupados en lo que le importa a quien pide. */
const GRUPOS: { titulo: string; estados: EstadoRequerimiento[]; tono: string; vista: string }[] = [
  { titulo: "Por corregir o enviar", estados: ["BORRADOR", "OBSERVADO"], tono: "text-amber-700", vista: "pendientes" },
  { titulo: "Por aprobar", estados: ["PENDIENTE_APROBACION"], tono: "text-slate-900", vista: "pendientes" },
  { titulo: "En atención", estados: ["VERIFICACION_STOCK", "EN_ATENCION", "ATENCION_PARCIAL"], tono: "text-brand-700", vista: "proceso" },
  { titulo: "Entregados", estados: ["ATENCION_COMPLETA"], tono: "text-emerald-700", vista: "cerrados" },
];

function Esqueleto() {
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[0, 1, 2, 3].map((k) => (
          <Skeleton key={k} className="h-[5.5rem] rounded-xl" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
        <Skeleton className="h-64 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    </div>
  );
}

/**
 * "Mis requerimientos" del Inicio (DEC-L076): todo lo que pidió el usuario,
 * por estado, cuánto ya le entregaron, lo que más pide y lo último que pidió.
 */
export default function MiResumen() {
  const resumen = useDatos("mi-resumen", obtenerMiResumen);
  const r = resumen.datos;

  if (resumen.cargando) return <Esqueleto />;
  if (!r) return resumen.error ? <p className="text-sm text-rose-600">{resumen.error}</p> : null;
  if (r.total === 0) {
    return (
      <Card>
        <EmptyState
          icon={Inbox}
          title="Aún no has pedido nada"
          action={
            <Button href="/requerimientos/nuevo" icon={<Plus className="size-4" />}>
              Nuevo requerimiento
            </Button>
          }
        />
      </Card>
    );
  }

  const cuenta = (estados: EstadoRequerimiento[]) => estados.reduce((s, e) => s + (r.estados[e] ?? 0), 0);
  const avance = r.unidadesPedidas > 0 ? Math.min(1, r.unidadesEntregadas / r.unidadesPedidas) : 0;
  const rechazados = r.estados.RECHAZADO ?? 0;

  return (
    <section aria-labelledby="mi-resumen-titulo" className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3">
        <h2 id="mi-resumen-titulo" className="text-lg font-semibold tracking-[-0.01em] text-slate-900">
          Mis requerimientos
          <span className="ml-2 font-mono text-sm font-normal tabular-nums text-slate-400">{r.total}</span>
        </h2>
        <Button href="/requerimientos/nuevo" size="sm" icon={<Plus className="size-4" />}>
          Nuevo requerimiento
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {GRUPOS.map((g) => (
          <Link
            key={g.titulo}
            to={`/requerimientos?vista=${g.vista}`}
            className="rounded-xl border border-slate-200 bg-white px-4 py-3.5 transition-colors hover:border-slate-300"
          >
            <span className="block text-xs text-slate-500">{g.titulo}</span>
            <span className={`mt-1 block font-mono text-2xl font-semibold tabular-nums ${g.tono}`}>{cuenta(g.estados)}</span>
          </Link>
        ))}
      </div>

      <Card className="px-4 py-3.5">
        <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
          <span className="text-slate-600">
            Te entregaron <strong className="font-semibold text-slate-900">{fmtCantidad(r.unidadesEntregadas)}</strong> de{" "}
            {fmtCantidad(r.unidadesPedidas)} unidades pedidas
          </span>
          <span className="font-mono text-xs tabular-nums text-slate-500">{Math.round(avance * 100)}%</span>
        </div>
        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
          <div className="h-full rounded-full bg-emerald-500" style={{ width: `${avance * 100}%` }} />
        </div>
        {rechazados > 0 && (
          <p className="mt-2 text-xs text-slate-500">
            {rechazados} {rechazados === 1 ? "requerimiento rechazado no cuenta" : "requerimientos rechazados no cuentan"}.
          </p>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
        <Card className="overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2.5">
            <span className="text-sm font-medium text-slate-900">Últimos pedidos</span>
            <Link to="/requerimientos" className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700">
              Ver todos <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <ul className="divide-y divide-slate-100">
            {r.recientes.map((q) => (
              <li key={q.id}>
                <Link to={`/requerimientos?req=${q.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-slate-900" title={q.resumen ?? undefined}>
                      {q.resumen ?? "Sin productos"}
                    </span>
                    <span className="mt-0.5 block font-mono text-[0.6875rem] text-slate-400">
                      {q.codigo} · {fmtFecha(q.creado)}
                    </span>
                  </span>
                  {q.avance > 0 && q.avance < 1 && (
                    <span className="hidden font-mono text-xs tabular-nums text-slate-500 sm:block">
                      {Math.round(q.avance * 100)}%
                    </span>
                  )}
                  <EstadoBadge estado={q.estado as EstadoRequerimiento} />
                </Link>
              </li>
            ))}
          </ul>
        </Card>

        <Card className="overflow-hidden">
          <div className="border-b border-slate-200 px-4 py-2.5">
            <span className="text-sm font-medium text-slate-900">Lo que más pides</span>
          </div>
          {r.productos.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-slate-400">Aún no envías requerimientos.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {r.productos.map((p) => (
                <li key={p.sku} className="flex items-center gap-3 px-4 py-3">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-slate-900" title={p.nombre}>
                      {p.nombre}
                    </span>
                    <span className="mt-0.5 block font-mono text-[0.6875rem] text-slate-400">
                      {p.sku} · {p.veces} {p.veces === 1 ? "pedido" : "pedidos"}
                    </span>
                  </span>
                  <span className="shrink-0 text-right font-mono text-xs tabular-nums text-slate-600">
                    {fmtCantidad(p.entregadas)}/{fmtCantidad(p.pedidas)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </section>
  );
}
