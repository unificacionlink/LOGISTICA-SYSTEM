import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Inbox, Plus, Search, X } from "lucide-react";
import { obtenerBandeja } from "../api/requerimientos";
import type { Bandeja, FilaBandeja } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { EMPRESAS } from "../components/empresas";
import FilaRequerimiento, { GRID_FILA } from "../components/requerimientos/FilaRequerimiento";
import PanelRequerimiento from "../components/requerimientos/PanelRequerimiento";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

const CLAVE_EMPRESA = "requerimientos.empresa";

// Mismas vistas que BandejaService: se pintan antes de que llegue la respuesta.
const VISTAS_GESTOR = [
  { id: "atencion", label: "Requieren atención" },
  { id: "compras", label: "Compras" },
  { id: "despacho", label: "Despacho" },
  { id: "seguimiento", label: "Seguimiento" },
  { id: "cerrados", label: "Cerrados" },
];
const VISTAS_SOLICITANTE = [
  { id: "pendientes", label: "Pendientes míos" },
  { id: "proceso", label: "En proceso" },
  { id: "cerrados", label: "Cerrados" },
];

const VACIO: Record<string, string> = {
  atencion: "Nada por atender",
  compras: "Sin compras en curso",
  despacho: "Nada por despachar",
  seguimiento: "Nada en seguimiento",
  cerrados: "Sin requerimientos cerrados",
  pendientes: "No tienes pendientes",
  proceso: "Nada en proceso",
};

function leerEmpresaGuardada(): string {
  try {
    return localStorage.getItem(CLAVE_EMPRESA) ?? "";
  } catch {
    return "";
  }
}

function FilaEsqueleto() {
  return (
    <div className={`grid gap-2 px-4 py-3.5 md:gap-4 md:pr-48 ${GRID_FILA}`}>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3.5 w-3/5" />
        <Skeleton className="h-3 w-2/5" />
      </div>
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-1.5 w-full" />
        <Skeleton className="h-3 w-16" />
      </div>
      <Skeleton className="h-3.5 w-20" />
      <Skeleton className="h-5 w-24 md:ml-auto" />
    </div>
  );
}

/**
 * Bandeja operativa de Requerimientos (DEC-L048, referencia ERPNext Material
 * Request): vistas por trabajo pendiente, empresa, busqueda y orden resueltos
 * en el servidor; cada fila muestra lo pedido, su avance y la siguiente accion;
 * el detalle se abre en un panel lateral sin salir de la bandeja.
 */
export default function Requerimientos() {
  const { tieneAlgunRol } = useAuth();
  const esGestor = tieneAlgunRol("LOGISTICA", "ADMIN");
  const vistasBase = esGestor ? VISTAS_GESTOR : VISTAS_SOLICITANTE;
  const [params, setParams] = useSearchParams();

  const vista = vistasBase.find((v) => v.id === params.get("vista"))?.id ?? vistasBase[0].id;
  const empresa = params.get("empresa") ?? leerEmpresaGuardada();
  const q = params.get("q") ?? "";
  const reqAbierto = params.get("req");

  const [texto, setTexto] = useState(q);
  const [datos, setDatos] = useState<Bandeja | null>(null);
  const [filas, setFilas] = useState<FilaBandeja[]>([]);
  const [cargando, setCargando] = useState(true);
  const [masCargando, setMasCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const consulta = useRef(0);

  const cambiar = useCallback(
    (cambios: Record<string, string | null>) => {
      setParams(
        (prev) => {
          const p = new URLSearchParams(prev);
          for (const [k, v] of Object.entries(cambios)) {
            if (v) p.set(k, v);
            else p.delete(k);
          }
          return p;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  // setParams cambia con cada cambio de URL: la fila memorizada recibe una
  // funcion fija que usa siempre la ultima version de cambiar.
  const cambiarRef = useRef(cambiar);
  useEffect(() => {
    cambiarRef.current = cambiar;
  }, [cambiar]);
  const abrirReq = useCallback((id: string) => cambiarRef.current({ req: id }), []);

  // La busqueda espera a que se deje de escribir.
  useEffect(() => {
    const t = setTimeout(() => {
      if (texto.trim() !== q) cambiar({ q: texto.trim() || null });
    }, 250);
    return () => clearTimeout(t);
  }, [texto, q, cambiar]);

  const cargar = useCallback(() => {
    const n = ++consulta.current;
    return obtenerBandeja({ vista, empresa, q }).then((res) => {
      if (n !== consulta.current) return;
      if (res.ok && res.result) {
        setDatos(res.result);
        setFilas(res.result.filas);
        setError(null);
      } else setError(res.message);
    });
  }, [vista, empresa, q]);

  useEffect(() => {
    cargar().finally(() => setCargando(false));
  }, [cargar]);

  async function mostrarMas() {
    if (!datos) return;
    setMasCargando(true);
    try {
      const res = await obtenerBandeja({ vista, empresa, q, pagina: datos.pagina + 1 });
      const pagina = res.result;
      if (res.ok && pagina) {
        setDatos(pagina);
        setFilas((prev) => [...prev, ...pagina.filas]);
      } else setError(res.message);
    } finally {
      setMasCargando(false);
    }
  }

  function elegirEmpresa(code: string) {
    cambiar({ empresa: code || null });
    try {
      localStorage.setItem(CLAVE_EMPRESA, code);
    } catch {
      // Sin almacenamiento: solo no se recuerda la eleccion.
    }
  }

  const conteoVista = useMemo(() => new Map((datos?.vistas ?? []).map((v) => [v.id, v.total])), [datos]);
  const conteoEmpresa = useMemo(() => new Map((datos?.empresas ?? []).map((e) => [e.code, e.total])), [datos]);
  const totalEmpresas = (datos?.empresas ?? []).reduce((s, e) => s + e.total, 0);
  // Logistica ve siempre las tres empresas; quien pide, solo si tiene de varias.
  const empresas = esGestor ? EMPRESAS : EMPRESAS.filter((e) => conteoEmpresa.has(e.code));
  const conSelector = esGestor || empresas.length > 1;
  const conEmpresa = conSelector && !empresa;
  const filaAbierta = filas.find((f) => f.id === reqAbierto) ?? null;

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

      <div className="mb-3 flex flex-col gap-2 lg:flex-row lg:items-center">
        {conSelector && (
          <div
            role="tablist"
            aria-label="Empresa"
            className="flex shrink-0 gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-slate-50 p-1"
          >
            {[{ code: "", label: "Todas", marca: "" }, ...empresas].map((e) => {
              const activa = empresa === e.code;
              const n = e.code ? (conteoEmpresa.get(e.code) ?? 0) : totalEmpresas;
              return (
                <button
                  key={e.code || "todas"}
                  type="button"
                  role="tab"
                  aria-selected={activa}
                  onClick={() => elegirEmpresa(e.code)}
                  className={`flex shrink-0 items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                    activa ? "bg-white text-slate-900 shadow-boton" : "text-slate-500 hover:text-slate-800"
                  }`}
                >
                  {e.marca && <span className={`size-2 rounded-full ${e.marca}`} aria-hidden="true" />}
                  {e.label}
                  <span className="font-mono text-xs tabular-nums text-slate-400">{datos ? n : "–"}</span>
                </button>
              );
            })}
          </div>
        )}
        <label className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder={
              esGestor ? "Buscar por código, producto, CECO o solicitante" : "Buscar por código, producto o CECO"
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
      </div>

      <div role="tablist" aria-label="Vista" className="mb-3 flex gap-1 overflow-x-auto border-b border-slate-200">
        {vistasBase.map((v) => (
          <button
            key={v.id}
            type="button"
            role="tab"
            aria-selected={vista === v.id}
            onClick={() => cambiar({ vista: v.id === vistasBase[0].id ? null : v.id })}
            className={`-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              vista === v.id
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {v.label}
            <span
              className={`rounded-md px-1.5 font-mono text-xs tabular-nums ${
                vista === v.id ? "bg-brand-50 text-brand-700" : "text-slate-400"
              }`}
            >
              {datos ? (conteoVista.get(v.id) ?? 0) : "–"}
            </span>
          </button>
        ))}
      </div>

      <Card className="overflow-hidden">
        <div className="cabecera-tabla hidden border-b border-slate-200 pl-4 md:flex md:items-center">
          <div className={`grid flex-1 gap-x-4 pr-3 ${GRID_FILA}`}>
            <span className="py-2.5">Requerimiento</span>
            <span className="py-2.5">Avance</span>
            <span className="py-2.5">Requerido</span>
            <span className="py-2.5 text-right">Estado</span>
          </div>
          <span className="w-44 shrink-0 py-2.5 pr-4 text-right">Siguiente paso</span>
        </div>

        {cargando ? (
          <div className="divide-y divide-slate-100">
            {[0, 1, 2, 3, 4, 5].map((k) => (
              <FilaEsqueleto key={k} />
            ))}
          </div>
        ) : filas.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title={q ? "Sin resultados" : (VACIO[vista] ?? "Sin requerimientos")}
            action={
              q ? (
                <Button variant="secondary" size="sm" onClick={() => setTexto("")}>
                  Quitar búsqueda
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {filas.map((f) => (
                <FilaRequerimiento
                  key={f.id}
                  f={f}
                  esGestor={esGestor}
                  conEmpresa={conEmpresa}
                  seleccionada={f.id === reqAbierto}
                  vista={vista}
                  onAbrir={abrirReq}
                />
              ))}
            </ul>
            {datos && filas.length < datos.total && (
              <div className="border-t border-slate-100 px-4 py-3 text-center">
                <Button variant="ghost" size="sm" loading={masCargando} onClick={mostrarMas}>
                  Mostrar más ({datos.total - filas.length} restantes)
                </Button>
              </div>
            )}
          </>
        )}
      </Card>

      {reqAbierto && (
        <PanelRequerimiento
          key={reqAbierto}
          id={reqAbierto}
          fila={filaAbierta}
          esGestor={esGestor}
          onCerrar={() => cambiar({ req: null })}
          onCambio={() => void cargar()}
        />
      )}
    </div>
  );
}
