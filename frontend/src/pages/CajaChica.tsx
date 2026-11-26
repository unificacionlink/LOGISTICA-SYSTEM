import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { BarChart3, ExternalLink, Plus, Receipt, Wallet } from "lucide-react";
import {
  anularCompraCajaChica,
  consolidadoCajaChica,
  descargarConsolidadoCajaChica,
  misComprasCajaChica,
  TIPOS_COMPROBANTE,
  type CompraModuloCajaChica,
  type MontoCajaChica,
} from "../api/cajachica";
import { useDatos } from "../api/useDatos";
import { useAuth } from "../auth/AuthContext";
import BotonDescarga from "../components/BotonDescarga";
import { dinero } from "../components/compras/formato";
import { EMPRESAS } from "../components/empresas";
import { fmtCantidad, fmtDia } from "../components/formato";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import EmptyState from "../components/ui/EmptyState";
import FilaConDetalle from "../components/ui/FilaConDetalle";
import { inputClass } from "../components/ui/Field";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import Pestanas from "../components/ui/Pestanas";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";

type Tab = "mias" | "consolidado";
const ROLES_CONSOLIDADO = ["LOGISTICA", "ADMIN", "GERENCIA", "APROBADOR"];
const ETIQUETA_TIPO = new Map(TIPOS_COMPROBANTE.map((t) => [t.id, t.label]));
const th = "px-5 py-2.5";
const cabecera = "border-b border-slate-200 cabecera-tabla";

/** Los últimos 24 meses (AAAA-MM), el actual primero. */
function ultimosMeses(): { id: string; label: string }[] {
  const hoy = new Date();
  return Array.from({ length: 24 }, (_, i) => {
    const d = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const id = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const label = d.toLocaleDateString("es-PE", { month: "long", year: "numeric" });
    return { id, label: label.charAt(0).toUpperCase() + label.slice(1) };
  });
}

function nombreMes(id: string): string {
  const [a, m] = id.split("-").map(Number);
  const label = new Date(a, m - 1, 1).toLocaleDateString("es-PE", { month: "short", year: "numeric" });
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function comprobante(c: CompraModuloCajaChica): string {
  return `${ETIQUETA_TIPO.get(c.tipoComprobante) ?? c.tipoComprobante} ${c.serie ? `${c.serie}-` : ""}${c.numero}`;
}

/**
 * Caja chica (DEC-L092): cada quien ve y registra lo que su proyecto compró con
 * caja chica; Logística, Gerencia y Admin ven el consolidado de todos los CECOs,
 * y el aprobador el de los CECOs que aprueba o suple.
 */
export default function CajaChica() {
  const { tieneAlgunRol } = useAuth();
  const [params, setParams] = useSearchParams();
  const veConsolidado = tieneAlgunRol(...ROLES_CONSOLIDADO);
  const tab: Tab = veConsolidado && params.get("tab") === "consolidado" ? "consolidado" : "mias";

  return (
    <div>
      <PageHeader
        title="Caja chica"
        actions={
          <Button href="/caja-chica/nueva" icon={<Plus className="size-4" />}>
            Registrar compra
          </Button>
        }
      />
      {veConsolidado && (
        <Pestanas<Tab>
          pestanas={[
            { id: "mias", label: "Mis compras", icon: Receipt },
            { id: "consolidado", label: "Consolidado", icon: BarChart3 },
          ]}
          activa={tab}
          onCambiar={(id) => setParams(id === "mias" ? {} : { tab: id }, { replace: true })}
        />
      )}
      {tab === "mias" ? <MisCompras /> : <Consolidado />}
    </div>
  );
}

// ------------------------------------------------------------------ Mis compras

function MisCompras() {
  const confirmar = useConfirmar();
  const compras = useDatos("cajachica:mias", misComprasCajaChica);
  const [error, setError] = useState<string | null>(null);

  async function anular(c: CompraModuloCajaChica) {
    const r = await confirmar({
      titulo: `¿Anular la compra ${c.codigo}?`,
      mensaje: `${comprobante(c)} por ${dinero(c.total, "PEN")}. Deja de contar en el consolidado.`,
      confirmar: "Anular",
      peligro: true,
      motivo: "Motivo de la anulación",
    });
    if (!r) return;
    const res = await anularCompraCajaChica(c.id, r.motivo);
    if (!res.ok || !res.result) {
      setError(res.message);
      return;
    }
    setError(null);
    const anulada = res.result;
    compras.mutar((prev) => (prev ?? []).map((x) => (x.id === anulada.id ? anulada : x)));
  }

  return (
    <>
      {(error ?? compras.error) && <Alert tone="danger" className="mb-4">{error ?? compras.error}</Alert>}
      <Card className="overflow-hidden">
        {!compras.datos ? (
          <SkeletonTable cols={6} />
        ) : compras.datos.length === 0 ? (
          <EmptyState icon={Wallet} title="Aún no registras compras con caja chica" />
        ) : (
          <TablaCompras compras={compras.datos} onAnular={anular} />
        )}
      </Card>
    </>
  );
}

function TablaCompras({
  compras,
  onAnular,
  conSolicitante = false,
}: {
  compras: CompraModuloCajaChica[];
  onAnular?: (c: CompraModuloCajaChica) => void;
  conSolicitante?: boolean;
}) {
  return (
    <table className="tabla">
      <thead>
        <tr className={cabecera}>
          <th className={th}>Fecha</th>
          <th className={th}>Proveedor</th>
          <th className={th}>CECO</th>
          {conSolicitante && <th className={th}>Solicitante</th>}
          <th className={th}>Comprobante</th>
          <th className={`${th} text-right`}>Total</th>
          <th className={th} />
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {compras.map((c) => {
          const factura = c.facturaArchivoUrl ?? c.facturaUrl;
          const anulada = c.estado === "ANULADA";
          return (
            <FilaConDetalle key={c.id} detalle={<DetalleCompra c={c} />} className={`hover:bg-slate-50 ${anulada ? "opacity-60" : ""}`}>
              <td className="whitespace-nowrap px-5 py-2.5 text-slate-700">{fmtDia(c.fecha)}</td>
              <td className="col-flex px-5 py-2.5" title={c.proveedorNombre}>
                <span className="font-medium text-slate-900">{c.proveedorNombre}</span>
                <span className="ml-2 font-mono text-xs text-slate-400">{c.codigo}</span>
              </td>
              <td className="whitespace-nowrap px-5 py-2.5 text-slate-700" title={c.cecoNombre}>
                {c.cecoCode} <span className="text-xs text-slate-400">{c.empresaCode}</span>
              </td>
              {conSolicitante && <td className="whitespace-nowrap px-5 py-2.5 text-slate-700">{c.solicitante}</td>}
              <td className="whitespace-nowrap px-5 py-2.5 text-slate-700">
                {comprobante(c)}
                {anulada && (
                  <Badge tone="gris" className="ml-2">
                    Anulada
                  </Badge>
                )}
              </td>
              <td className={`whitespace-nowrap px-5 py-2.5 text-right font-medium ${anulada ? "text-slate-400 line-through" : "text-slate-900"}`}>
                {dinero(c.total, "PEN")}
              </td>
              <td className="whitespace-nowrap py-2.5 pr-5 text-right">
                <div className="flex items-center justify-end gap-1">
                  {factura && (
                    <a href={factura} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700">
                      Factura <ExternalLink className="size-3.5" />
                    </a>
                  )}
                  {onAnular && !anulada && <MenuAcciones acciones={[{ label: "Anular", onClick: () => onAnular(c) }]} />}
                </div>
              </td>
            </FilaConDetalle>
          );
        })}
      </tbody>
    </table>
  );
}

const MAX_DETALLE = 8;

function DetalleCompra({ c }: { c: CompraModuloCajaChica }) {
  return (
    <div className="flex w-80 flex-col gap-2">
      <p className="text-xs text-slate-500">
        {c.cecoCode} — {c.cecoNombre}
        {c.proveedorRuc ? ` · RUC ${c.proveedorRuc}` : ""}
      </p>
      <ul className="flex flex-col gap-1">
        {c.lineas.slice(0, MAX_DETALLE).map((l, i) => (
          <li key={i} className="flex justify-between gap-3 text-xs">
            <span className="truncate text-slate-700">
              {fmtCantidad(l.cantidad)} × {l.descripcion}
            </span>
            <span className="shrink-0 text-slate-900">{dinero(l.total, "PEN")}</span>
          </li>
        ))}
      </ul>
      {c.lineas.length > MAX_DETALLE && <p className="text-xs text-slate-400">y {c.lineas.length - MAX_DETALLE} más</p>}
      {c.nota && <p className="whitespace-normal text-xs text-slate-500">«{c.nota}»</p>}
      {c.motivoAnulacion && <p className="whitespace-normal text-xs text-rose-700">Anulada: {c.motivoAnulacion}</p>}
    </div>
  );
}

// ------------------------------------------------------------------ Consolidado

function Consolidado() {
  const meses = useMemo(() => ultimosMeses(), []);
  const [desde, setDesde] = useState(meses[0].id);
  const [hasta, setHasta] = useState(meses[0].id);
  const [empresa, setEmpresa] = useState("");
  const [ceco, setCeco] = useState<string | null>(null);
  const filtros = { desde: desde <= hasta ? desde : hasta, hasta: desde <= hasta ? hasta : desde, empresa };
  const datos = useDatos(`cajachica:consolidado|${filtros.desde}|${filtros.hasta}|${empresa}`, () => consolidadoCajaChica(filtros), {
    conservar: true,
  });
  const c = datos.datos;
  const detalle = c ? (ceco ? c.detalle.filter((d) => d.cecoCode === ceco) : c.detalle) : [];

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <select aria-label="Desde" value={desde} onChange={(e) => setDesde(e.target.value)} className={`${inputClass} w-48`}>
          {meses.map((m) => (
            <option key={m.id} value={m.id}>
              {m.label}
            </option>
          ))}
        </select>
        <span className="text-sm text-slate-500">a</span>
        <select aria-label="Hasta" value={hasta} onChange={(e) => setHasta(e.target.value)} className={`${inputClass} w-48`}>
          {meses.map((m) => (
            <option key={m.id} value={m.id}>
              {m.label}
            </option>
          ))}
        </select>
        <select aria-label="Empresa" value={empresa} onChange={(e) => setEmpresa(e.target.value)} className={`${inputClass} w-44`}>
          <option value="">Todas las empresas</option>
          {EMPRESAS.map((e) => (
            <option key={e.code} value={e.code}>
              {e.label}
            </option>
          ))}
        </select>
        <div className="ml-auto">
          <BotonDescarga etiqueta="Excel" tipo="excel" descargar={() => descargarConsolidadoCajaChica(filtros)} />
        </div>
      </div>

      {datos.error && <Alert tone="danger" className="mb-4">{datos.error}</Alert>}

      {!c ? (
        <div className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-3">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
          <Card className="overflow-hidden">
            <SkeletonTable cols={4} />
          </Card>
        </div>
      ) : (
        <div className={`flex flex-col gap-5 transition-opacity duration-200 ${datos.previo ? "opacity-60" : ""}`}>
          <div className="grid gap-4 sm:grid-cols-3">
            <Cifra titulo="Gastado" valor={dinero(c.total, "PEN")} />
            <Cifra titulo="Compras" valor={String(c.compras)} />
            <Cifra titulo="Anuladas" valor={String(c.anuladas)} />
          </div>

          {c.compras === 0 ? (
            <Card>
              <EmptyState icon={Wallet} title="No hay compras con caja chica en este periodo" />
            </Card>
          ) : (
            <>
              <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
                <Montos
                  titulo="Por CECO"
                  montos={c.porCeco}
                  total={c.total}
                  elegido={ceco}
                  onElegir={(clave) => setCeco((prev) => (prev === clave ? null : clave))}
                />
                <div className="flex flex-col gap-5">
                  {c.porMes.length > 1 && <Montos titulo="Por mes" montos={c.porMes} total={c.total} etiqueta={nombreMes} />}
                  <Montos titulo="Por solicitante" montos={c.porSolicitante} total={c.total} />
                </div>
              </div>

              <Card className="overflow-hidden">
                <div className="flex min-h-[3.25rem] items-center gap-2 px-5 py-3">
                  <h2 className="text-sm font-semibold text-slate-900">Detalle</h2>
                  {ceco && (
                    <button type="button" onClick={() => setCeco(null)} className="text-xs font-medium text-brand-600 hover:text-brand-700">
                      {ceco} · ver todos
                    </button>
                  )}
                </div>
                <div className="border-t border-slate-200">
                  <TablaCompras compras={detalle} conSolicitante />
                </div>
              </Card>
            </>
          )}
        </div>
      )}
    </>
  );
}

function Cifra({ titulo, valor }: { titulo: string; valor: string }) {
  return (
    <Card className="px-5 py-4">
      <p className="rotulo">{titulo}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{valor}</p>
    </Card>
  );
}

/** Tabla de montos con una barra fina de la parte de cada fila en el total del periodo. */
function Montos({
  titulo,
  montos,
  total,
  etiqueta = (clave) => clave,
  elegido,
  onElegir,
}: {
  titulo: string;
  montos: MontoCajaChica[];
  total: number;
  etiqueta?: (clave: string) => string;
  elegido?: string | null;
  onElegir?: (clave: string) => void;
}) {
  return (
    <Card className="overflow-hidden">
      <h2 className="px-5 py-3 text-sm font-semibold text-slate-900">{titulo}</h2>
      <table className="tabla">
        <tbody className="divide-y divide-slate-100 border-t border-slate-200">
          {montos.map((m) => {
            const parte = total > 0 ? (m.monto / total) * 100 : 0;
            return (
              <tr
                key={m.clave}
                onClick={onElegir ? () => onElegir(m.clave) : undefined}
                className={`${onElegir ? "cursor-pointer hover:bg-slate-50" : ""} ${elegido === m.clave ? "bg-brand-50" : ""}`}
              >
                <td className="col-flex px-5 py-2.5" title={m.detalle ?? etiqueta(m.clave)}>
                  <span className="font-medium text-slate-900">{etiqueta(m.clave)}</span>
                  {m.detalle && <span className="ml-2 text-xs text-slate-400">{m.detalle}</span>}
                  <div className="mt-1.5 h-1 rounded-full bg-slate-100">
                    <div className="h-1 rounded-full bg-brand-500" style={{ width: `${Math.max(parte, 1)}%` }} />
                  </div>
                </td>
                <td className="whitespace-nowrap px-3 py-2.5 text-right text-xs text-slate-500">
                  {m.compras} {m.compras === 1 ? "compra" : "compras"}
                </td>
                <td className="whitespace-nowrap py-2.5 pr-5 text-right font-medium text-slate-900">{dinero(m.monto, "PEN")}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}
