import { useDeferredValue, useMemo, useState } from "react";
import { useDatos } from "../api/useDatos";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Search, Truck, UserPlus } from "lucide-react";
import { obtenerPanelProveedores } from "../api/proveedores";
import type { FilaProveedor } from "../api/types";
import { fmtDia } from "../components/formato";
import { fmtNota } from "../components/proveedores/estados";
import { EstadoProveedorBadge, VigenciaBadge } from "../components/proveedores/Insignias";
import PanelConfiguracion from "../components/proveedores/PanelConfiguracion";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import FilaConDetalle from "../components/ui/FilaConDetalle";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";

type Filtro = "todos" | "habilitados" | "revision" | "observados" | "reevaluar" | "borradores" | "rechazados" | "inactivos";

const FILTROS_VISIBLES: { id: Filtro; label: string }[] = [
  { id: "todos", label: "Todos" },
  { id: "habilitados", label: "Habilitados" },
  { id: "revision", label: "En revisión" },
  { id: "observados", label: "Observados" },
];

const FILTROS_MAS: { id: Filtro; label: string }[] = [
  { id: "borradores", label: "Borradores" },
  { id: "rechazados", label: "Rechazados" },
  { id: "inactivos", label: "Inactivos" },
];

const operativo = (p: FilaProveedor) => p.status === "HABILITADO" && p.active;

const CUMPLE: Record<Filtro, (p: FilaProveedor) => boolean> = {
  todos: () => true,
  habilitados: operativo,
  revision: (p) => p.status === "PENDIENTE_REVISION",
  observados: (p) => p.status === "OBSERVADO",
  reevaluar: (p) => operativo(p) && (p.vigencia === "PROXIMO_A_VENCER" || p.vigencia === "VENCIDO"),
  borradores: (p) => p.status === "BORRADOR",
  rechazados: (p) => p.status === "RECHAZADO",
  inactivos: (p) => p.status === "HABILITADO" && !p.active,
};

/** Minusculas sin tildes, para buscar "credito" y encontrar "Crédito". */
const normal = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

function Indicador({
  titulo,
  valor,
  detalle,
  activo,
  alerta,
  onClick,
}: {
  titulo: string;
  valor: number;
  detalle?: string;
  activo: boolean;
  alerta?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={activo}
      className={`rounded-xl border bg-white px-4 py-3 text-left shadow-superficie transition-colors hover:border-brand-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 ${
        activo ? "border-brand-500 ring-1 ring-brand-500" : "border-slate-200"
      }`}
    >
      <p className="text-xs font-medium text-slate-500">{titulo}</p>
      <p className={`mt-1 font-serif text-[1.75rem] leading-none tabular-nums ${alerta ? "text-amber-700" : "text-slate-950"}`}>
        {valor}
      </p>
      {detalle && <p className="mt-1.5 text-xs text-slate-500">{detalle}</p>}
    </button>
  );
}

export default function Proveedores() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const filtro = (params.get("filtro") as Filtro | null) ?? "todos";
  const panelDatos = useDatos("proveedores-panel", obtenerPanelProveedores);
  const panel = panelDatos.datos;
  const error = panelDatos.error;
  const [busqueda, setBusqueda] = useState("");
  // Filtrar no traba el teclado: la lista se actualiza apenas haya un respiro.
  const busquedaDiferido = useDeferredValue(busqueda);
  const [configurando, setConfigurando] = useState(false);

  function filtrar(f: Filtro) {
    setParams(f === "todos" ? {} : { filtro: f }, { replace: true });
  }

  const visibles = useMemo(() => {
    if (!panel) return [];
    const q = normal(busquedaDiferido.trim());
    return panel.proveedores.filter((p) => {
      if (!(CUMPLE[filtro] ?? CUMPLE.todos)(p)) return false;
      if (!q) return true;
      return normal([p.ruc, p.legalName, p.tradeName, p.categorias, p.busqueda].filter(Boolean).join(" ")).includes(q);
    });
  }, [panel, busquedaDiferido, filtro]);

  const k = panel?.kpis;
  const filtroMas = FILTROS_MAS.find((f) => f.id === filtro);

  return (
    <div>
      <PageHeader
        title="Proveedores"
        actions={
          <>
            <MenuAcciones acciones={[{ label: "Configurar evaluaciones y documentos", onClick: () => setConfigurando(true) }]} />
            <Button href="/proveedores/nuevo" icon={<UserPlus className="size-4" />}>
              Nuevo proveedor
            </Button>
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <div className="mb-5 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {!k ? (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-[5.25rem] rounded-xl" />)
        ) : (
          <>
            <Indicador titulo="Registrados" valor={k.registrados} activo={filtro === "todos"} onClick={() => filtrar("todos")} />
            <Indicador
              titulo="Habilitados"
              valor={k.habilitados}
              detalle={k.sinEvaluacion ? `${k.sinEvaluacion} sin evaluación` : undefined}
              activo={filtro === "habilitados"}
              onClick={() => filtrar("habilitados")}
            />
            <Indicador titulo="En revisión" valor={k.enRevision} activo={filtro === "revision"} onClick={() => filtrar("revision")} />
            <Indicador
              titulo="Por reevaluar"
              valor={k.porVencer + k.vencidas}
              detalle={k.vencidas ? `${k.vencidas} vencidas` : undefined}
              alerta={k.porVencer + k.vencidas > 0}
              activo={filtro === "reevaluar"}
              onClick={() => filtrar("reevaluar")}
            />
          </>
        )}
      </div>

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-3 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <input
              className={`${inputClass} w-full pl-9`}
              placeholder="RUC, razón social, producto o categoría"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              aria-label="Buscar proveedor"
            />
          </div>
          <div className="flex items-center gap-1">
            {FILTROS_VISIBLES.map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => filtrar(f.id)}
                aria-pressed={filtro === f.id}
                className={`rounded-md px-2.5 py-1.5 text-sm font-medium transition-colors ${
                  filtro === f.id ? "bg-brand-50 text-brand-800" : "text-slate-600 hover:bg-slate-100"
                }`}
              >
                {f.label}
              </button>
            ))}
            {filtroMas && (
              <span className="rounded-md bg-brand-50 px-2.5 py-1.5 text-sm font-medium text-brand-800">{filtroMas.label}</span>
            )}
            {filtro === "reevaluar" && (
              <span className="rounded-md bg-brand-50 px-2.5 py-1.5 text-sm font-medium text-brand-800">Por reevaluar</span>
            )}
            <MenuAcciones etiqueta="Más filtros" acciones={FILTROS_MAS.map((f) => ({ label: f.label, onClick: () => filtrar(f.id) }))} />
          </div>
        </div>

        {!panel ? (
          <SkeletonTable cols={5} />
        ) : visibles.length === 0 ? (
          <EmptyState
            icon={Truck}
            title={panel.proveedores.length === 0 ? "Todavía no hay proveedores registrados" : "Ningún proveedor coincide"}
          />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="cabecera-tabla border-b border-slate-200">
                  <th className="px-5 py-3">Proveedor</th>
                  <th className="px-5 py-3">Categorías</th>
                  <th className="px-5 py-3">Estado</th>
                  <th className="px-5 py-3">Evaluación</th>
                  <th className="px-5 py-3 text-right">Productos</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {visibles.map((p) => (
                  <FilaConDetalle
                    key={p.supplierId}
                    className="cursor-pointer hover:bg-slate-50"
                    detalle={
                      <div className="space-y-1 text-sm">
                        <p className="font-medium text-slate-900">{p.tradeName ?? p.legalName}</p>
                        <p className="text-slate-600">{p.contacto ?? "Sin contacto"}</p>
                        {p.city && <p className="text-slate-500">{p.city}</p>}
                        {p.proximaEvaluacion && (
                          <p className="text-slate-500">Próxima evaluación: {fmtDia(p.proximaEvaluacion)}</p>
                        )}
                      </div>
                    }
                  >
                    <td className="col-flex px-5 py-3" title={p.legalName}>
                      <button
                        type="button"
                        onClick={() => navigate(`/proveedores/${p.supplierId}`)}
                        className="block max-w-full truncate text-left font-medium text-slate-900 hover:text-brand-700"
                      >
                        {p.legalName}
                      </button>
                      <span className="font-mono text-xs text-slate-500">{p.ruc}</span>
                      {p.supplierType === "CRITICO" && <span className="ml-2 text-xs font-medium text-amber-700">Crítico</span>}
                    </td>
                    <td className="max-w-[16rem] truncate px-5 py-3 text-slate-600" title={p.categorias ?? undefined}>
                      {p.categorias ?? "—"}
                    </td>
                    <td className="px-5 py-3" onClick={() => navigate(`/proveedores/${p.supplierId}`)}>
                      <EstadoProveedorBadge status={p.status} activo={p.active} />
                    </td>
                    <td className="px-5 py-3" onClick={() => navigate(`/proveedores/${p.supplierId}`)}>
                      <span className="inline-flex items-center gap-2">
                        {p.ultimaNota != null && (
                          <span className="font-medium tabular-nums text-slate-800">{fmtNota(p.ultimaNota)}</span>
                        )}
                        <VigenciaBadge vigencia={p.vigencia} />
                      </span>
                    </td>
                    <td className="px-5 py-3 text-right tabular-nums text-slate-600">{p.productos}</td>
                  </FilaConDetalle>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {configurando && <PanelConfiguracion onCerrar={() => setConfigurando(false)} />}
    </div>
  );
}
