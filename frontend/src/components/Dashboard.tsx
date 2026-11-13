import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { obtenerDashboard } from "../api/dashboard";
import type { CodigoEtapa, Dashboard as DashboardData } from "../api/types";
import { EMPRESAS } from "./empresas";
import Alert from "./ui/Alert";
import { Skeleton } from "./ui/Skeleton";

// Un solo tono para todas las marcas de datos (series unicas: no hay identidad
// que codificar). El azul TDEM ya es el acento de la app.
const MARCA = "bg-brand-500";

const PERIODOS = [
  { dias: 30, label: "30 días" },
  { dias: 90, label: "90 días" },
  { dias: 365, label: "1 año" },
];

const ETAPAS: Record<CodigoEtapa, { label: string; to: string }> = {
  POR_APROBAR: { label: "Por aprobar", to: "/aprobaciones" },
  EN_COTIZACION: { label: "En cotización", to: "/compras" },
  OC_EN_CURSO: { label: "OC en curso", to: "/compras/ordenes" },
  POR_DESPACHAR: { label: "Por despachar", to: "/almacen" },
  EN_TRANSITO: { label: "En tránsito", to: "/almacen" },
};

function fmtNum(n: number | null | undefined, decimales = 1): string {
  if (n === null || n === undefined) return "—";
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: decimales });
}

function fmtMonto(moneda: string, n: number): string {
  const simbolo = moneda === "PEN" ? "S/" : moneda === "USD" ? "US$" : moneda;
  return `${simbolo} ${Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** Horas si es menos de 2 dias; si no, dias (48 h se lee peor que 2 d). */
function duracionHoras(horas: number | null): { valor: string; unidad: string } {
  if (horas === null) return { valor: "—", unidad: "" };
  if (horas < 48) return { valor: fmtNum(horas), unidad: "h" };
  return { valor: fmtNum(horas / 24), unidad: "días" };
}

function fmtSemana(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}

// ---------------------------------------------------------------------------

function Tile({
  rotulo,
  valor,
  unidad,
  detalle,
  children,
}: {
  rotulo: string;
  valor: ReactNode;
  unidad?: string;
  detalle?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-col rounded-lg border border-slate-200 bg-white px-4 py-4">
      <span className="rotulo">{rotulo}</span>
      <span className="mt-2.5 flex items-baseline gap-1.5">
        <span className="text-[1.75rem] font-semibold leading-none tracking-[-0.02em] text-slate-950">{valor}</span>
        {unidad && <span className="text-sm text-slate-500">{unidad}</span>}
      </span>
      {children}
      {detalle && <span className="mt-auto pt-2.5 text-xs text-slate-500">{detalle}</span>}
    </div>
  );
}

function Panel({ titulo, nota, children }: { titulo: string; nota?: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <header className="border-b border-slate-200 px-5 py-3.5">
        <h2 className="text-[0.9375rem] font-semibold text-slate-900">{titulo}</h2>
        {nota && <p className="mt-0.5 text-xs text-slate-500">{nota}</p>}
      </header>
      <div className="p-5">{children}</div>
    </section>
  );
}

/** Barras horizontales: una por etapa, cada fila lleva al modulo donde se resuelve. */
function BarrasEtapas({ etapas }: { etapas: DashboardData["etapas"] }) {
  const max = Math.max(1, ...etapas.map((e) => e.cantidad));
  return (
    <ul className="flex flex-col gap-1">
      {etapas.map((e) => {
        const meta = ETAPAS[e.codigo];
        return (
          <li key={e.codigo}>
            <Link
              to={meta.to}
              className="group grid grid-cols-[7.5rem_1fr_2.5rem] items-center gap-3 rounded-md px-1.5 py-2 transition-colors hover:bg-slate-50"
              title={`${meta.label}: ${e.cantidad} requerimiento(s)`}
            >
              <span className="text-sm text-slate-700 group-hover:text-slate-950">{meta.label}</span>
              <span className="h-3.5">
                {e.cantidad > 0 && (
                  <span
                    className={`block h-full rounded-r-[4px] ${MARCA} transition-[width] duration-500 ease-(--ease-suave)`}
                    style={{ width: `${Math.max(2, (e.cantidad / max) * 100)}%` }}
                  />
                )}
              </span>
              <span className="text-right text-sm font-medium tabular-nums text-slate-900">{e.cantidad}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

/** Columnas por semana con tooltip; eje con solo 0 y el maximo (grilla tenue). */
function ColumnasSemanales({ semanas }: { semanas: DashboardData["semanas"] }) {
  const [activa, setActiva] = useState<number | null>(null);
  const max = Math.max(1, ...semanas.map((s) => s.creados));
  const total = semanas.reduce((a, s) => a + s.creados, 0);
  if (total === 0) {
    return <p className="py-12 text-center text-sm text-slate-400">No se crearon requerimientos en el período.</p>;
  }
  // Etiquetas del eje X espaciadas: ~6 visibles como maximo.
  const paso = Math.max(1, Math.ceil(semanas.length / 6));
  const sel = activa !== null ? semanas[activa] : null;

  return (
    <div>
      <div className="mb-3 flex h-5 items-baseline gap-2 text-sm">
        {sel ? (
          <>
            <span className="text-slate-500">Semana del {fmtSemana(sel.semana)}</span>
            <span className="font-semibold text-slate-900">{sel.creados} creados</span>
          </>
        ) : (
          <span className="text-slate-500">
            <span className="font-semibold text-slate-900">{total}</span> en el período
          </span>
        )}
      </div>
      <div className="relative">
        {/* Grilla: linea del maximo y linea base, hairlines solidas. */}
        <div className="pointer-events-none absolute inset-x-0 top-0 border-t border-slate-100">
          <span className="absolute -top-2.5 right-0 bg-white pl-1 text-[0.625rem] tabular-nums text-slate-400">{max}</span>
        </div>
        <div
          className="flex h-44 items-end gap-0.5 border-b border-slate-200"
          onMouseLeave={() => setActiva(null)}
          role="img"
          aria-label={`Requerimientos creados por semana: ${total} en total, máximo ${max} en una semana`}
        >
          {semanas.map((s, i) => (
            <button
              key={s.semana}
              type="button"
              aria-label={`Semana del ${fmtSemana(s.semana)}: ${s.creados}`}
              onMouseEnter={() => setActiva(i)}
              onFocus={() => setActiva(i)}
              onBlur={() => setActiva(null)}
              className="flex h-full flex-1 items-end justify-center focus-visible:outline-none"
            >
              <span
                className={`block w-[68%] max-w-10 rounded-t-[4px] transition-[height,opacity] duration-500 ease-(--ease-suave) ${MARCA} ${
                  activa !== null && activa !== i ? "opacity-40" : ""
                }`}
                style={{ height: s.creados > 0 ? `${Math.max(2, (s.creados / max) * 100)}%` : "0" }}
              />
            </button>
          ))}
        </div>
        <div className="mt-1.5 flex gap-0.5">
          {semanas.map((s, i) => (
            <span key={s.semana} className="flex-1 text-center text-[0.625rem] tabular-nums text-slate-400">
              {i % paso === 0 ? fmtSemana(s.semana) : ""}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function TablaProveedores({ filas }: { filas: DashboardData["proveedores"] }) {
  if (filas.length === 0) {
    return <p className="py-6 text-center text-sm text-slate-400">No se enviaron RFQ en el período.</p>;
  }
  return (
    <table className="tabla -mx-1 w-[calc(100%+0.5rem)]">
      <thead>
        <tr className="cabecera-tabla bg-transparent">
          <th className="px-1 pb-2">Proveedor</th>
          <th className="px-2 pb-2 text-right">RFQ</th>
          <th className="px-2 pb-2">Respuesta</th>
          <th className="px-2 pb-2 text-right">Días</th>
          <th className="px-1 pb-2 text-right">Elegido</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {filas.map((p) => {
          const pct = p.enviadas > 0 ? Math.round((p.respondidas / p.enviadas) * 100) : 0;
          return (
            <tr key={p.proveedor}>
              <td className="col-flex px-1 py-2.5 text-slate-800" title={p.proveedor}>{p.proveedor}</td>
              <td className="px-2 py-2.5 text-right text-slate-600">{p.enviadas}</td>
              <td className="px-2 py-2.5">
                {/* Medidor: pista y relleno del mismo tono. */}
                <span className="flex items-center gap-2" title={`${p.respondidas} de ${p.enviadas} respondidas`}>
                  <span className="h-1.5 w-16 overflow-hidden rounded-full bg-brand-100">
                    <span className={`block h-full rounded-full ${MARCA}`} style={{ width: `${pct}%` }} />
                  </span>
                  <span className="w-9 text-right text-xs text-slate-700">{pct}%</span>
                </span>
              </td>
              <td className="px-2 py-2.5 text-right text-slate-600">{fmtNum(p.diasRespuesta)}</td>
              <td className="px-1 py-2.5 text-right text-slate-600">{p.elegidas}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function TablaComparativos({ filas }: { filas: DashboardData["comparativos"] }) {
  if (filas.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-slate-400">
        Aún no hay compras con dos o más cotizaciones en el período.
      </p>
    );
  }
  return (
    <table className="tabla -mx-1 w-[calc(100%+0.5rem)]">
      <thead>
        <tr className="cabecera-tabla bg-transparent">
          <th className="px-1 pb-2">Requerimiento</th>
          <th className="px-2 pb-2">Proveedor elegido</th>
          <th className="px-2 pb-2 text-right">Elegida vs mayor</th>
          <th className="px-1 pb-2 text-right">Ahorro</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {filas.map((c) => (
          <tr key={`${c.codigo}-${c.fecha}`}>
            <td className="px-1 py-2.5">
              <span className="block font-mono text-xs text-slate-800">{c.codigo}</span>
              <span className="text-[0.6875rem] text-slate-400">{c.cotizaciones} cotizaciones</span>
            </td>
            <td className="col-flex px-2 py-2.5 text-slate-700" title={c.proveedor}>{c.proveedor}</td>
            <td className="px-2 py-2.5 text-right text-slate-600">
              {fmtMonto(c.moneda, c.elegido)}
              <span className="block text-[0.6875rem] text-slate-400">vs {fmtMonto(c.moneda, c.mayor)}</span>
            </td>
            <td className="px-1 py-2.5 text-right font-medium text-slate-900">{fmtMonto(c.moneda, c.ahorro)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ---------------------------------------------------------------------------

export default function Dashboard() {
  const [dias, setDias] = useState(90);
  const [empresa, setEmpresa] = useState<string | null>(null);
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Filtros de la ultima respuesta: si no coinciden con los actuales, se esta actualizando.
  const [cargadoPara, setCargadoPara] = useState<string | null>(null);
  const clave = `${dias}|${empresa ?? ""}`;
  const actualizando = cargadoPara !== clave;

  useEffect(() => {
    let vigente = true;
    obtenerDashboard(dias, empresa).then((res) => {
      if (!vigente) return;
      if (res.ok) {
        setData(res.result);
        setError(null);
      } else setError(res.message);
      setCargadoPara(`${dias}|${empresa ?? ""}`);
    });
    return () => {
      vigente = false;
    };
  }, [dias, empresa]);

  const pctRespuesta = data && data.rfqEnviadas > 0 ? Math.round((data.rfqRespondidas / data.rfqEnviadas) * 100) : null;
  const aprobacion = duracionHoras(data?.horasAprobacion ?? null);
  const ahorroPen = data?.ahorro.find((a) => a.moneda === "PEN");
  const ahorroOtras = data?.ahorro.filter((a) => a.moneda !== "PEN") ?? [];

  return (
    <div>
      {/* Filtros: una sola fila, afectan a todo el tablero. */}
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div role="radiogroup" aria-label="Período" className="inline-flex rounded-md border border-slate-200 bg-white p-0.5">
          {PERIODOS.map((p) => (
            <button
              key={p.dias}
              type="button"
              role="radio"
              aria-checked={dias === p.dias}
              onClick={() => setDias(p.dias)}
              className={`rounded px-3 py-1.5 text-[0.8125rem] transition-colors ${
                dias === p.dias ? "bg-brand-700 font-medium text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
        <select
          aria-label="Empresa"
          value={empresa ?? ""}
          onChange={(e) => setEmpresa(e.target.value || null)}
          className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-[0.8125rem] text-slate-800 hover:border-slate-300 focus:border-slate-500 focus:outline-none"
        >
          <option value="">Todas las empresas</option>
          {EMPRESAS.map((e) => (
            <option key={e.code} value={e.code}>
              {e.label}
            </option>
          ))}
        </select>
      </div>

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {!data ? (
        !error && (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-28 rounded-lg" />
              ))}
            </div>
            <Skeleton className="h-64 rounded-lg" />
          </div>
        )
      ) : (
        // Al cambiar filtros se conserva lo anterior atenuado (sin parpadeo de skeletons).
        <div className={`flex flex-col gap-4 transition-opacity duration-200 ${actualizando ? "opacity-60" : ""}`}>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
            <Tile
              rotulo="Abiertos"
              valor={data.abiertos}
              detalle={`${data.creados} creados · ${data.completados} completados en el período`}
            />
            <Tile rotulo="Aprobación" valor={aprobacion.valor} unidad={aprobacion.unidad} detalle="Promedio hasta aprobarse" />
            <Tile
              rotulo="Ciclo completo"
              valor={fmtNum(data.diasCiclo)}
              unidad={data.diasCiclo !== null ? "días" : undefined}
              detalle="De creado a entregado en destino"
            />
            <Tile
              rotulo="Respuesta proveedores"
              valor={pctRespuesta !== null ? pctRespuesta : "—"}
              unidad={pctRespuesta !== null ? "%" : undefined}
              detalle={`${data.rfqRespondidas} de ${data.rfqEnviadas} RFQ respondidas`}
            >
              <span className="mt-3 h-1.5 overflow-hidden rounded-full bg-brand-100">
                <span className={`block h-full rounded-full ${MARCA}`} style={{ width: `${pctRespuesta ?? 0}%` }} />
              </span>
            </Tile>
            <Tile
              rotulo="Tiempo de respuesta"
              valor={fmtNum(data.diasRespuesta)}
              unidad={data.diasRespuesta !== null ? "días" : undefined}
              detalle="Promedio de RFQ a cotización"
            />
            <Tile
              rotulo="Ahorro por comparar"
              valor={ahorroPen ? fmtMonto("PEN", ahorroPen.monto) : "—"}
              detalle={
                <>
                  {ahorroOtras.map((a) => (
                    <span key={a.moneda} className="block text-slate-700">+ {fmtMonto(a.moneda, a.monto)}</span>
                  ))}
                  {data.comprasConComparativo} de {data.comprasTotal} compras con 2+ cotizaciones
                </>
              }
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-5">
            <div className="lg:col-span-2">
              <Panel titulo="Trabajo pendiente por etapa" nota="Hoy. Un requerimiento puede estar en más de una etapa.">
                <BarrasEtapas etapas={data.etapas} />
              </Panel>
            </div>
            <div className="lg:col-span-3">
              <Panel titulo="Requerimientos creados por semana">
                <ColumnasSemanales semanas={data.semanas} />
              </Panel>
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Panel titulo="Desempeño de proveedores" nota="RFQ recibidas, % respondidas, días en responder y veces elegido.">
              <TablaProveedores filas={data.proveedores} />
            </Panel>
            <Panel titulo="Comparativo de cotizaciones" nota="Oferta adjudicada frente a la más cara del mismo consolidado.">
              <TablaComparativos filas={data.comparativos} />
            </Panel>
          </div>
        </div>
      )}
    </div>
  );
}
