import { useEffect, useMemo, useState } from "react";
import { Boxes, FileSpreadsheet, Search } from "lucide-react";
import { listarSaldosStock } from "../api/stock";
import type { Familia, SaldoStock } from "../api/types";
import { fmtCantidad } from "../components/AlmacenBadges";
import FiltroFamilia, { coincideFamilia, etiquetaFamilia } from "../components/FiltroFamilia";
import PanelKardex from "../components/stock/PanelKardex";
import EstadoStock from "../components/stock/EstadoStock";
import { estadoStock } from "../components/stock/estado";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";
import { useNavigate } from "react-router-dom";

type Filtro = "todos" | "con" | "sin" | "bajo";

const FILTROS: { id: Filtro; label: string }[] = [
  { id: "todos", label: "Todos" },
  { id: "con", label: "Con stock" },
  { id: "bajo", label: "Bajo mínimo" },
  { id: "sin", label: "Sin stock" },
];

const POR_PAGINA = 100;
const th = "px-4 py-3";

function coincideFiltro(f: Filtro, s: SaldoStock): boolean {
  if (f === "todos") return true;
  if (f === "con") return s.fisico > 0;
  if (f === "sin") return s.fisico <= 0;
  const e = estadoStock(s);
  return e === "BAJO_MINIMO" || e === "AGOTADO";
}

/**
 * Stock del almacén central del grupo (DEC-L045): todo el catálogo, con su
 * físico, reservado, disponible y mínimo. Clic en un SKU abre su historial.
 */
export default function Stock() {
  const navigate = useNavigate();
  const [saldos, setSaldos] = useState<SaldoStock[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [texto, setTexto] = useState("");
  const [familia, setFamilia] = useState("");
  const [filtro, setFiltro] = useState<Filtro>("con");
  const [visibles, setVisibles] = useState(POR_PAGINA);
  const [abierto, setAbierto] = useState<SaldoStock | null>(null);

  useEffect(() => {
    listarSaldosStock()
      .then((res) => {
        if (res.ok) setSaldos(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

  const familias = useMemo<Familia[]>(() => {
    const m = new Map<string, Familia>();
    for (const s of saldos) {
      if (!m.has(s.familiaId)) {
        m.set(s.familiaId, {
          familyId: s.familiaId,
          code: "",
          nombre: s.familiaNombre,
          tipoId: s.tipoId,
          tipoCode: "",
          tipoNombre: s.tipoNombre,
        });
      }
    }
    return [...m.values()].sort((a, b) => a.tipoNombre.localeCompare(b.tipoNombre) || a.nombre.localeCompare(b.nombre));
  }, [saldos]);

  // Busqueda y familia primero: los conteos de los filtros responden a ellas.
  const base = useMemo(() => {
    const t = texto.trim().toLowerCase();
    return saldos.filter(
      (s) =>
        coincideFamilia(familia, s.tipoId, s.familiaId) &&
        (!t || s.codigoVariante.toLowerCase().includes(t) || s.nombreProducto.toLowerCase().includes(t)),
    );
  }, [saldos, texto, familia]);

  const conteo = useMemo(() => {
    const c: Record<Filtro, number> = { todos: base.length, con: 0, sin: 0, bajo: 0 };
    for (const s of base) {
      if (coincideFiltro("con", s)) c.con++;
      if (coincideFiltro("sin", s)) c.sin++;
      if (coincideFiltro("bajo", s)) c.bajo++;
    }
    return c;
  }, [base]);

  const filtrados = useMemo(() => base.filter((s) => coincideFiltro(filtro, s)), [base, filtro]);

  function actualizar(s: SaldoStock) {
    setSaldos((prev) => prev.map((x) => (x.variantId === s.variantId ? s : x)));
    setAbierto((prev) => (prev?.variantId === s.variantId ? s : prev));
  }

  return (
    <div>
      <PageHeader
        title="Stock"
        actions={
          <>
            <Button href="/stock/carga" icon={<FileSpreadsheet className="size-4" />}>
              Inventario por Excel
            </Button>
            <MenuAcciones acciones={[{ label: "Ajuste manual", onClick: () => navigate("/stock/ajustes") }]} />
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <div role="tablist" className="mb-3 flex flex-wrap gap-1.5">
        {FILTROS.map((f) => (
          <button
            key={f.id}
            type="button"
            role="tab"
            aria-selected={filtro === f.id}
            onClick={() => {
              setFiltro(f.id);
              setVisibles(POR_PAGINA);
            }}
            className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
              filtro === f.id
                ? "border-brand-600 bg-brand-50 text-brand-700"
                : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
            }`}
          >
            {f.label}
            <span
              className={`tabular-nums ${
                f.id === "bajo" && conteo.bajo > 0 && filtro !== f.id ? "text-amber-700" : "text-slate-400"
              }`}
            >
              {cargando ? "–" : conteo[f.id]}
            </span>
          </button>
        ))}
      </div>

      <div className="mb-3 flex flex-col gap-2 sm:flex-row">
        <label className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value);
              setVisibles(POR_PAGINA);
            }}
            placeholder="Buscar por código o producto"
            className={`${inputClass} w-full pl-9`}
          />
        </label>
        <FiltroFamilia
          familias={familias}
          value={familia}
          onChange={(v) => {
            setFamilia(v);
            setVisibles(POR_PAGINA);
          }}
          className="w-full sm:w-64"
        />
      </div>

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : filtrados.length === 0 ? (
          <EmptyState
            icon={Boxes}
            title={saldos.length === 0 ? "Sin productos" : filtro === "bajo" ? "Nada por debajo del mínimo" : "Sin resultados"}
          />
        ) : (
          <>
            <table className="tabla">
              <thead>
                <tr className="cabecera-tabla border-b border-slate-200">
                  <th className={th}>Producto</th>
                  <th className={`${th} text-right`}>Físico</th>
                  <th className={`${th} col-sec text-right`}>Reservado</th>
                  <th className={`${th} text-right`}>Disponible</th>
                  <th className={`${th} col-sec text-right`}>Mínimo</th>
                  <th className={`${th} col-sec`} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtrados.slice(0, visibles).map((s) => (
                  <tr
                    key={s.variantId}
                    onClick={() => setAbierto(s)}
                    className="cursor-pointer"
                    tabIndex={0}
                    onKeyDown={(e) => e.key === "Enter" && setAbierto(s)}
                  >
                    <td className="col-flex px-4 py-2.5" title={s.nombreProducto}>
                      <p className="truncate text-sm text-slate-800">
                        {s.nombreProducto}
                        {s.atributo && <span className="text-slate-400"> · {s.atributo}</span>}
                      </p>
                      <p className="truncate text-xs text-slate-500">
                        <span className="font-mono">{s.codigoVariante}</span> · {etiquetaFamilia(s.tipoNombre, s.familiaNombre)}
                      </p>
                    </td>
                    <td className="px-4 py-2.5 text-right text-slate-700">
                      {fmtCantidad(s.fisico)} <span className="text-xs text-slate-400">{s.unidad}</span>
                    </td>
                    <td className="col-sec px-4 py-2.5 text-right text-slate-500">
                      {s.reservado > 0 ? fmtCantidad(s.reservado) : "—"}
                    </td>
                    <td className={`px-4 py-2.5 text-right font-medium ${s.disponible > 0 ? "text-slate-900" : "text-slate-400"}`}>
                      {fmtCantidad(s.disponible)}
                    </td>
                    <td className="col-sec px-4 py-2.5 text-right text-slate-500">
                      {s.minimo != null ? fmtCantidad(s.minimo) : "—"}
                    </td>
                    <td className="col-sec px-4 py-2.5">
                      <EstadoStock saldo={s} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filtrados.length > visibles && (
              <div className="border-t border-slate-100 px-4 py-3 text-center">
                <Button variant="ghost" size="sm" onClick={() => setVisibles((v) => v + POR_PAGINA)}>
                  Mostrar más ({filtrados.length - visibles} restantes)
                </Button>
              </div>
            )}
          </>
        )}
      </Card>

      {abierto && <PanelKardex saldo={abierto} onCerrar={() => setAbierto(null)} onActualizado={actualizar} />}
    </div>
  );
}
