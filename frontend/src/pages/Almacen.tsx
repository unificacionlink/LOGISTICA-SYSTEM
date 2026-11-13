import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { FileSpreadsheet, FileText, PackageCheck, PackageOpen, Truck } from "lucide-react";
import { listarNotas, listarOrdenesPorRecibir, listarPorDespachar } from "../api/almacen";
import type { NotaInventario, Orden, Requerimiento } from "../api/types";
import { EnvioBadge, NotaBadge } from "../components/AlmacenBadges";
import { fmtCantidad, fmtDia, fmtFecha } from "../components/formato";
import EstadoBadge from "../components/EstadoBadge";
import OrdenEstadoBadge from "../components/OrdenEstadoBadge";
import PrioridadBadge from "../components/PrioridadBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

type Pestana = "recibir" | "despachar" | "notas";

const PESTANAS: { id: Pestana; label: string; icon: typeof Truck }[] = [
  { id: "recibir", label: "Por recibir", icon: PackageOpen },
  { id: "despachar", label: "Por despachar", icon: PackageCheck },
  { id: "notas", label: "Notas de inventario", icon: FileText },
];

const thClass = "px-5 py-3";
const linkClass = "text-sm font-medium text-brand-600 hover:text-brand-700";

export default function Almacen() {
  // La pestana vive en la URL (?tab=) para que "volver" desde un detalle la conserve.
  const [params, setParams] = useSearchParams();
  const tab = (PESTANAS.some((p) => p.id === params.get("tab")) ? params.get("tab") : "recibir") as Pestana;

  return (
    <div>
      <PageHeader
        title="Almacén"
        actions={
          <Button href="/almacen/carga-masiva" variant="secondary" size="sm" icon={<FileSpreadsheet className="size-4" />}>
            Carga masiva
          </Button>
        }
      />

      <div role="tablist" className="mb-4 flex flex-wrap gap-1 border-b border-slate-200">
        {PESTANAS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            data-tutorial={`almacen-tab-${id}`}
            aria-selected={tab === id}
            onClick={() => setParams({ tab: id }, { replace: true })}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === id
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="size-4" />
            {label}
          </button>
        ))}
      </div>

      {tab === "recibir" && <PorRecibir />}
      {tab === "despachar" && <PorDespachar />}
      {tab === "notas" && <Notas />}
    </div>
  );
}

function useCarga<T>(cargar: () => Promise<{ ok: boolean; result?: T | null; message: string }>) {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  useEffect(() => {
    cargar()
      .then((res) => {
        if (res.ok) setDatos(res.result ?? null);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return { datos, error, cargando };
}

function PorRecibir() {
  const { datos, error, cargando } = useCarga<Orden[]>(listarOrdenesPorRecibir);
  const ordenes = datos ?? [];
  return (
    <>
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : ordenes.length === 0 ? (
          <EmptyState icon={PackageOpen} title="No hay órdenes de compra por recibir" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className={thClass}>Orden</th>
                  <th className={thClass}>Proveedor</th>
                  <th className={thClass}>Requerimiento</th>
                  <th className={`${thClass} col-sec`}>Emitida</th>
                  <th className={thClass}>Estado</th>
                  <th className={thClass} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {ordenes.map((o) => (
                  <tr key={o.orderId} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">{o.orderCode}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(o.proveedorNombre)}>{o.proveedorNombre}</td>
                    <td className="px-5 py-3 font-mono text-xs text-slate-500">{o.requestCode}</td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtDia(o.issueDate)}</td>
                    <td className="px-5 py-3">
                      <OrdenEstadoBadge estado={o.status} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/almacen/ordenes/${o.orderId}/recepcion`} className={linkClass}>
                        Recibir
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}

function PorDespachar() {
  const { datos, error, cargando } = useCarga<Requerimiento[]>(listarPorDespachar);
  const reqs = datos ?? [];
  return (
    <>
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : reqs.length === 0 ? (
          <EmptyState icon={PackageCheck} title="No hay requerimientos por despachar" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className={thClass}>Código</th>
                  <th className={thClass}>CECO</th>
                  <th className={thClass}>Solicitante</th>
                  <th className={`${thClass} col-sec`}>Prioridad</th>
                  <th className={thClass}>Estado</th>
                  <th className={thClass} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {reqs.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">{r.code}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(r.cecoNombre ?? "—")}>{r.cecoNombre ?? "—"}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(r.requesterNombre ?? "—")}>{r.requesterNombre ?? "—"}</td>
                    <td className="col-sec px-5 py-3">
                      <PrioridadBadge prioridad={r.priority} />
                    </td>
                    <td className="px-5 py-3">
                      <EstadoBadge estado={r.status} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/almacen/requerimientos/${r.id}/despacho`} className={linkClass}>
                        Despachar
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}

function Notas() {
  const { datos, error, cargando } = useCarga<NotaInventario[]>(listarNotas);
  const notas = datos ?? [];
  return (
    <>
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={7} />
        ) : notas.length === 0 ? (
          <EmptyState icon={FileText} title="Aún no hay notas de inventario" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className={thClass}>Nota</th>
                  <th className={thClass}>Requerimiento</th>
                  <th className={thClass}>Destino</th>
                  <th className={thClass}>Unidades</th>
                  <th className={thClass}>Estado</th>
                  <th className={thClass}>Envío</th>
                  <th className={`${thClass} col-sec`}>Emitida</th>
                  <th className={thClass} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {notas.map((n) => (
                  <tr key={n.inventoryNoteId} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">{n.inventoryNoteCode}</td>
                    <td className="px-5 py-3 font-mono text-xs text-slate-500">{n.requestCode}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(n.cecoNombre ?? "—")}>{n.cecoNombre ?? "—"}</td>
                    <td className="px-5 py-3 text-slate-600">{fmtCantidad(n.totalItemQty)}</td>
                    <td className="px-5 py-3">
                      <NotaBadge estado={n.status} />
                    </td>
                    <td className="px-5 py-3">
                      {n.envio ? <EnvioBadge estado={n.envio.status} /> : <span className="text-xs text-slate-400">Sin envío</span>}
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtFecha(n.issuedAt)}</td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/almacen/notas/${n.inventoryNoteId}`} className={linkClass}>
                        Ver
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}
