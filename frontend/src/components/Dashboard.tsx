import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Activity, ClipboardList, Handshake, Wallet } from "lucide-react";
import { descargarDemanda, descargarVentas, obtenerDashboard, obtenerDemanda, obtenerGasto, obtenerVentas } from "../api/dashboard";
import { obtenerClasificacion } from "../api/maestro";
import type { Familia } from "../api/types";
import { useDatos } from "../api/useDatos";
import { useAuth } from "../auth/AuthContext";
import BotonDescarga from "./BotonDescarga";
import PestanaDemanda from "./dashboard/PestanaDemanda";
import PestanaGasto from "./dashboard/PestanaGasto";
import PestanaProceso from "./dashboard/PestanaProceso";
import PestanaVentas from "./dashboard/PestanaVentas";
import { EMPRESAS } from "./empresas";
import FiltroFamilia from "./FiltroFamilia";
import Alert from "./ui/Alert";
import Pestanas, { type Pestana } from "./ui/Pestanas";
import { Skeleton } from "./ui/Skeleton";

type Vista = "proceso" | "demanda" | "gasto" | "ventas";

const PESTANAS: Pestana<Vista>[] = [
  { id: "proceso", label: "Proceso", icon: Activity },
  { id: "demanda", label: "Demanda", icon: ClipboardList },
  { id: "gasto", label: "Gasto", icon: Wallet },
];
/** DEC-L086: lo que TDEM vende a GOYDEL y METRIN; no lo ve el aprobador de CECO. */
const VENTAS: Pestana<Vista> = { id: "ventas", label: "Ventas", icon: Handshake };

const PERIODOS = [
  { dias: 30, label: "30 días" },
  { dias: 90, label: "90 días" },
  { dias: 365, label: "1 año" },
];

function Esqueleto({ tiles }: { tiles: number }) {
  const columnas = tiles === 6 ? "md:grid-cols-3 xl:grid-cols-6" : tiles === 4 ? "xl:grid-cols-4" : "xl:grid-cols-3";
  return (
    <div className="flex flex-col gap-4">
      <div className={`grid grid-cols-2 gap-3 ${columnas}`}>
        {Array.from({ length: tiles }).map((_, i) => (
          <Skeleton key={i} className="h-28 rounded-lg" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-5">
        <Skeleton className="h-64 rounded-lg lg:col-span-2" />
        <Skeleton className="h-64 rounded-lg lg:col-span-3" />
      </div>
    </div>
  );
}

/** Familia › subfamilia del maestro para el filtro de Demanda (DEC-L034). */
function useFamilias(activo: boolean): Familia[] {
  const [familias, setFamilias] = useState<Familia[]>([]);
  useEffect(() => {
    if (!activo || familias.length > 0) return;
    let vigente = true;
    void obtenerClasificacion().then((res) => {
      if (!vigente || !res.ok || !res.result) return;
      setFamilias(
        res.result.tipos.flatMap((t) =>
          t.familias.map((f) => ({
            familyId: f.id,
            code: f.code,
            nombre: f.nombre,
            tipoId: t.id,
            tipoCode: t.code,
            tipoNombre: t.nombre,
          })),
        ),
      );
    });
    return () => {
      vigente = false;
    };
  }, [activo, familias.length]);
  return familias;
}

/**
 * Dashboard del Inicio (DEC-L020, DEC-L083): Proceso, Demanda (dashboard
 * operativo de la V38.7) y Gasto. Periodo y empresa valen para las tres; solo
 * se pide la pestana visible.
 */
export default function Dashboard() {
  const [params, setParams] = useSearchParams();
  const { tieneAlgunRol } = useAuth();
  const pestanas = tieneAlgunRol("LOGISTICA", "ADMIN", "GERENCIA") ? [...PESTANAS, VENTAS] : PESTANAS;
  const vista = (pestanas.some((p) => p.id === params.get("tab")) ? params.get("tab") : "proceso") as Vista;
  const [dias, setDias] = useState(90);
  const [empresa, setEmpresa] = useState<string | null>(null);
  const [familia, setFamilia] = useState("");
  const familias = useFamilias(vista === "demanda");
  const filtros = useMemo(() => ({ dias, empresa, familia }), [dias, empresa, familia]);

  // Al cambiar un filtro sigue lo anterior (atenuado) hasta que llegue lo nuevo (DEC-L073).
  const filtroClave = `${dias}|${empresa ?? ""}`;
  const proceso = useDatos(vista === "proceso" ? `dashboard:${filtroClave}` : null, () => obtenerDashboard(dias, empresa), {
    conservar: true,
  });
  const demanda = useDatos(vista === "demanda" ? `dashboard-demanda:${filtroClave}|${familia}` : null, () => obtenerDemanda(filtros), {
    conservar: true,
  });
  const gasto = useDatos(vista === "gasto" ? `dashboard-gasto:${filtroClave}` : null, () => obtenerGasto(dias, empresa), {
    conservar: true,
  });
  const ventas = useDatos(vista === "ventas" ? `dashboard-ventas:${filtroClave}` : null, () => obtenerVentas(dias, empresa), {
    conservar: true,
  });
  const activa = vista === "proceso" ? proceso : vista === "demanda" ? demanda : vista === "gasto" ? gasto : ventas;

  function cambiarVista(id: Vista) {
    setParams(
      (p) => {
        const sig = new URLSearchParams(p);
        if (id === "proceso") sig.delete("tab");
        else sig.set("tab", id);
        return sig;
      },
      { replace: true },
    );
  }

  return (
    <div>
      <Pestanas pestanas={pestanas} activa={vista} onCambiar={cambiarVista} />

      {/* Filtros: una sola fila, afectan a toda la pestana. */}
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
        {vista === "demanda" && (
          <>
            <FiltroFamilia
              familias={familias}
              value={familia}
              onChange={setFamilia}
              className="w-full py-1.5! text-[0.8125rem]! shadow-none! sm:w-64"
            />
            <span className="sm:ml-auto">
              <BotonDescarga tipo="excel" etiqueta="Excel" descargar={() => descargarDemanda(filtros)} />
            </span>
          </>
        )}
        {vista === "ventas" && (
          <span className="sm:ml-auto">
            <BotonDescarga tipo="excel" etiqueta="Excel" descargar={() => descargarVentas(dias, empresa)} />
          </span>
        )}
      </div>

      {activa.error && <Alert tone="danger" className="mb-4">{activa.error}</Alert>}

      {!activa.datos ? (
        !activa.error && <Esqueleto tiles={vista === "proceso" ? 6 : vista === "demanda" ? 4 : 3} />
      ) : (
        // Al cambiar filtros se conserva lo anterior atenuado (sin parpadeo de skeletons).
        <div className={`transition-opacity duration-200 ${activa.previo ? "opacity-60" : ""}`}>
          {vista === "proceso" && proceso.datos && <PestanaProceso data={proceso.datos} />}
          {vista === "demanda" && demanda.datos && <PestanaDemanda data={demanda.datos} />}
          {vista === "gasto" && gasto.datos && <PestanaGasto data={gasto.datos} />}
          {vista === "ventas" && ventas.datos && <PestanaVentas data={ventas.datos} />}
        </div>
      )}
    </div>
  );
}
