import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AlertTriangle, ChevronDown, Flame, Inbox, Plus, Search, X } from "lucide-react";
import { listarRequerimientos } from "../api/requerimientos";
import type { Requerimiento } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { EMPRESAS } from "../components/empresas";
import FilaRequerimiento from "../components/requerimientos/FilaRequerimiento";
import {
  coincideBusqueda,
  enVista,
  esPrioritario,
  estaVencido,
  ETAPAS,
  etapaDe,
  etapasAbiertas,
  ordenar,
  type Etapa,
  type Vista,
  VISTAS,
} from "../components/requerimientos/bandeja";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

const POR_GRUPO = 30;
const CLAVE_EMPRESA = "requerimientos.empresa";

function leerEmpresaGuardada(): string {
  try {
    return localStorage.getItem(CLAVE_EMPRESA) ?? "";
  } catch {
    return "";
  }
}

/** Encabezado de columnas (solo en pantallas anchas): mismo grid que cada fila. */
function Columnas({ esGestor }: { esGestor: boolean }) {
  return (
    <div className="cabecera-tabla hidden border-b border-slate-200 pl-4 pr-12 md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,15rem)_7.5rem_11rem] md:gap-x-4">
      <span className="py-2.5">Requerimiento</span>
      <span className="py-2.5">{esGestor ? "CECO · Solicitante" : "CECO"}</span>
      <span className="py-2.5">Requerido</span>
      <span className="py-2.5 text-right">Estado</span>
    </div>
  );
}

function Grupo({
  etapa,
  filas,
  esGestor,
  conEmpresa,
  plano = false,
}: {
  etapa: Etapa;
  filas: Requerimiento[];
  esGestor: boolean;
  conEmpresa: boolean;
  /** Sin encabezado de etapa (vistas cerradas o "Todos"). */
  plano?: boolean;
}) {
  const [plegado, setPlegado] = useState(false);
  const [visibles, setVisibles] = useState(POR_GRUPO);
  const info = ETAPAS[etapa];
  const vencidos = filas.filter(estaVencido).length;
  return (
    <section aria-labelledby={plano ? undefined : `grupo-${etapa}`}>
      {!plano && (
        <button
          type="button"
          onClick={() => setPlegado((v) => !v)}
          aria-expanded={!plegado}
          className="flex w-full items-center gap-2.5 border-b border-slate-200 bg-slate-50/80 px-4 py-2 text-left transition-colors hover:bg-slate-100/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-400"
        >
          <ChevronDown
            className={`size-3.5 text-slate-400 transition-transform duration-200 ${plegado ? "-rotate-90" : ""}`}
          />
          <span className={`size-2 rounded-full ${info.marca}`} aria-hidden="true" />
          <h2 id={`grupo-${etapa}`} className="text-[13px] font-semibold text-slate-800">
            {info.label}
          </h2>
          <span className="font-mono text-xs tabular-nums text-slate-500">{filas.length}</span>
          {vencidos > 0 && <span className="ml-auto text-xs font-medium text-rose-700">{vencidos} vencido(s)</span>}
        </button>
      )}
      {!plegado && (
        <>
          <ul className="divide-y divide-slate-100 border-b border-slate-200 last:border-b-0">
            {filas.slice(0, visibles).map((r) => (
              <FilaRequerimiento key={r.id} r={r} esGestor={esGestor} conEmpresa={conEmpresa} />
            ))}
          </ul>
          {filas.length > visibles && (
            <div className="border-b border-slate-200 px-4 py-2 text-center">
              <Button variant="ghost" size="sm" onClick={() => setVisibles((v) => v + POR_GRUPO)}>
                Mostrar {Math.min(POR_GRUPO, filas.length - visibles)} más de {filas.length - visibles}
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/**
 * Bandeja de Requerimientos (DEC-L046): una empresa a la vez (o todas, con la
 * marca de cada una en la fila), lo abierto agrupado por etapa y ordenado por
 * urgencia, y busqueda por codigo, CECO, solicitante o producto.
 */
export default function Requerimientos() {
  const { usuario, tieneAlgunRol } = useAuth();
  const esGestor = tieneAlgunRol("LOGISTICA", "ADMIN");
  const veTodo = esGestor || tieneAlgunRol("APROBADOR", "GERENCIA");
  const [params, setParams] = useSearchParams();
  const [items, setItems] = useState<Requerimiento[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [texto, setTexto] = useState("");
  const [soloVencidos, setSoloVencidos] = useState(false);
  const [soloPrioritarios, setSoloPrioritarios] = useState(false);

  const vista = (VISTAS.some((v) => v.id === params.get("vista")) ? params.get("vista") : "abiertos") as Vista;
  const empresa = params.get("empresa") ?? leerEmpresaGuardada();

  useEffect(() => {
    listarRequerimientos()
      .then((res) => {
        if (res.ok) setItems(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

  function cambiar(clave: "vista" | "empresa", valor: string) {
    const p = new URLSearchParams(params);
    if (!valor || (clave === "vista" && valor === "abiertos")) p.delete(clave);
    else p.set(clave, valor);
    setParams(p, { replace: true });
    if (clave === "empresa") {
      try {
        localStorage.setItem(CLAVE_EMPRESA, valor);
      } catch {
        // Sin almacenamiento: solo no se recuerda la eleccion.
      }
    }
  }

  // Los borradores ajenos no son trabajo de nadie mas que su dueno: solo aparecen en "Todos".
  const propios = useMemo(
    () => items.filter((r) => r.status !== "BORRADOR" || r.requesterUserId === usuario?.id || vista === "todos"),
    [items, usuario?.id, vista],
  );

  const empresas = useMemo(() => {
    const extra = [...new Set(propios.map((r) => r.empresaCode ?? ""))]
      .filter((c) => c && !EMPRESAS.some((e) => e.code === c))
      .map((c) => ({ code: c, label: c, marca: "bg-slate-400" }));
    return [...EMPRESAS, ...extra]
      .map((e) => ({
        ...e,
        abiertos: propios.filter((r) => r.empresaCode === e.code && enVista("abiertos", r)).length,
        total: propios.filter((r) => r.empresaCode === e.code).length,
      }))
      .filter((e) => veTodo || e.total > 0);
  }, [propios, veTodo]);

  const empresaValida = empresas.some((e) => e.code === empresa) ? empresa : "";
  const conSelectorEmpresa = empresas.length > 1;

  // Empresa + busqueda primero: los conteos de vistas y filtros responden a ellas.
  const base = useMemo(
    () => propios.filter((r) => (!empresaValida || r.empresaCode === empresaValida) && coincideBusqueda(r, texto)),
    [propios, empresaValida, texto],
  );

  const conteoVista = useMemo(() => {
    const c = { abiertos: 0, atendidos: 0, rechazados: 0, todos: base.length } as Record<Vista, number>;
    for (const r of base) {
      if (enVista("abiertos", r)) c.abiertos++;
      else if (enVista("atendidos", r)) c.atendidos++;
      else if (enVista("rechazados", r)) c.rechazados++;
    }
    return c;
  }, [base]);

  const enLaVista = useMemo(() => base.filter((r) => enVista(vista, r)), [base, vista]);
  const vencidos = enLaVista.filter(estaVencido).length;
  const prioritarios = enLaVista.filter(esPrioritario).length;

  const filtrados = useMemo(
    () =>
      vista !== "abiertos"
        ? enLaVista
        : enLaVista.filter((r) => (!soloVencidos || estaVencido(r)) && (!soloPrioritarios || esPrioritario(r))),
    [enLaVista, soloVencidos, soloPrioritarios, vista],
  );

  const grupos = useMemo(() => {
    if (vista !== "abiertos") return null;
    return etapasAbiertas(esGestor)
      .map((etapa) => ({
        etapa,
        filas: ordenar(
          filtrados.filter((r) => etapaDe(r.status) === etapa),
          true,
        ),
      }))
      .filter((g) => g.filas.length > 0);
  }, [filtrados, vista, esGestor]);

  const conEmpresa = conSelectorEmpresa && !empresaValida;
  const hayFiltros = texto.trim() !== "" || (vista === "abiertos" && (soloVencidos || soloPrioritarios));

  return (
    <div>
      <PageHeader
        title="Requerimientos"
        actions={
          <Button href="/requerimientos/nuevo" icon={<Plus className="size-4" />}>
            Nuevo requerimiento
          </Button>
        }
      />
      {error && (
        <Alert tone="danger" className="mb-4">
          {error}
        </Alert>
      )}

      {conSelectorEmpresa && (
        <div
          role="tablist"
          aria-label="Empresa"
          className="mb-4 flex gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-1 sm:inline-flex"
        >
          {[
            { code: "", label: "Todas", marca: "", abiertos: empresas.reduce((s, e) => s + e.abiertos, 0) },
            ...empresas,
          ].map((e) => {
            const activa = empresaValida === e.code;
            return (
              <button
                key={e.code || "todas"}
                type="button"
                role="tab"
                aria-selected={activa}
                onClick={() => cambiar("empresa", e.code)}
                className={`flex shrink-0 items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  activa ? "bg-white text-slate-900 shadow-boton" : "text-slate-500 hover:text-slate-800"
                }`}
              >
                {e.marca && <span className={`size-2 rounded-full ${e.marca}`} aria-hidden="true" />}
                {e.label}
                <span className={`font-mono text-xs tabular-nums ${activa ? "text-slate-500" : "text-slate-400"}`}>
                  {cargando ? "–" : e.abiertos}
                </span>
              </button>
            );
          })}
        </div>
      )}

      <div className="mb-3 flex flex-col gap-2 lg:flex-row lg:items-center">
        <label className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder={
              esGestor ? "Buscar por código, CECO, solicitante o producto" : "Buscar por código, CECO o producto"
            }
            className={`${inputClass} w-full pl-9 pr-9`}
          />
          {texto && (
            <button
              type="button"
              onClick={() => setTexto("")}
              aria-label="Limpiar búsqueda"
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-slate-700"
            >
              <X className="size-3.5" />
            </button>
          )}
        </label>
        <div
          role="tablist"
          aria-label="Vista"
          className="flex shrink-0 gap-1 overflow-x-auto border-b border-slate-200 lg:border-b-0"
        >
          {VISTAS.map((v) => (
            <button
              key={v.id}
              type="button"
              role="tab"
              aria-selected={vista === v.id}
              onClick={() => cambiar("vista", v.id)}
              className={`-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition-colors lg:mb-0 ${
                vista === v.id
                  ? "border-brand-600 text-brand-700"
                  : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              {v.label}
              <span className="font-mono text-xs tabular-nums text-slate-400">
                {cargando ? "–" : conteoVista[v.id]}
              </span>
            </button>
          ))}
        </div>
      </div>

      {!cargando && vista === "abiertos" && (vencidos > 0 || prioritarios > 0 || soloVencidos || soloPrioritarios) && (
        <div className="mb-3 flex flex-wrap gap-1.5">
          {(vencidos > 0 || soloVencidos) && (
            <button
              type="button"
              aria-pressed={soloVencidos}
              onClick={() => setSoloVencidos((v) => !v)}
              className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                soloVencidos
                  ? "border-rose-300 bg-rose-50 text-rose-700"
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
              }`}
            >
              <AlertTriangle className="size-3.5" />
              Vencidos <span className="tabular-nums">{vencidos}</span>
            </button>
          )}
          {(prioritarios > 0 || soloPrioritarios) && (
            <button
              type="button"
              aria-pressed={soloPrioritarios}
              onClick={() => setSoloPrioritarios((v) => !v)}
              className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                soloPrioritarios
                  ? "border-amber-300 bg-amber-50 text-amber-800"
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
              }`}
            >
              <Flame className="size-3.5" />
              Urgentes y críticos <span className="tabular-nums">{prioritarios}</span>
            </button>
          )}
        </div>
      )}

      <Card className="overflow-hidden">
        {cargando ? (
          <div className="divide-y divide-slate-100">
            {[0, 1, 2, 3, 4, 5].map((k) => (
              <div key={k} className="grid gap-2 px-4 py-3.5 md:grid-cols-[minmax(0,1fr)_15rem_7.5rem_11rem] md:gap-4">
                <div className="flex flex-col gap-2">
                  <Skeleton className="h-3.5 w-28" />
                  <Skeleton className="h-3 w-3/4" />
                </div>
                <Skeleton className="h-3.5 w-4/5" />
                <Skeleton className="h-3.5 w-20" />
                <Skeleton className="h-5 w-28 md:ml-auto" />
              </div>
            ))}
          </div>
        ) : filtrados.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title={
              hayFiltros
                ? "Sin resultados"
                : items.length === 0
                  ? "Aún no hay requerimientos"
                  : vista === "abiertos"
                    ? "Nada pendiente"
                    : "Sin requerimientos en esta vista"
            }
            action={
              hayFiltros ? (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setTexto("");
                    setSoloVencidos(false);
                    setSoloPrioritarios(false);
                  }}
                >
                  Quitar filtros
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <Columnas esGestor={esGestor} />
            {grupos ? (
              grupos.map((g) => (
                <Grupo key={g.etapa} etapa={g.etapa} filas={g.filas} esGestor={esGestor} conEmpresa={conEmpresa} />
              ))
            ) : (
              <Grupo
                key={vista}
                etapa={vista === "atendidos" ? "atendido" : vista === "rechazados" ? "rechazado" : "atencion"}
                filas={ordenar(filtrados, false)}
                esGestor={esGestor}
                conEmpresa={conEmpresa}
                plano
              />
            )}
          </>
        )}
      </Card>
    </div>
  );
}
