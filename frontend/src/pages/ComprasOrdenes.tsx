import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PackageCheck } from "lucide-react";
import { listarOrdenes } from "../api/compras";
import type { Orden } from "../api/types";
import OrdenEstadoBadge from "../components/OrdenEstadoBadge";
import Alert from "../components/ui/Alert";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

function fmtFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("es-PE");
}

export default function ComprasOrdenes() {
  const [ordenes, setOrdenes] = useState<Orden[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    listarOrdenes()
      .then((res) => {
        if (res.ok) setOrdenes(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

  return (
    <div>
      <PageHeader title="Órdenes de compra" backTo="/compras" backLabel="Compras" />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : ordenes.length === 0 ? (
          <EmptyState icon={PackageCheck} title="Todavía no se emitió ninguna orden de compra" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className="px-5 py-3">Código</th>
                  <th className="px-5 py-3">Requerimiento</th>
                  <th className="px-5 py-3">Proveedor</th>
                  <th className="px-5 py-3">Total</th>
                  <th className="px-5 py-3">Estado</th>
                  <th className="col-sec px-5 py-3">Emitida</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {ordenes.map((o) => (
                  <tr key={o.orderId} className="hover:bg-slate-50">
                    <td className="px-5 py-3">
                      <Link
                        to={`/compras/ordenes/${o.orderId}`}
                        className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        {o.orderCode}
                      </Link>
                    </td>
                    <td className="px-5 py-3 font-mono text-xs text-slate-600">{o.requestCode}</td>
                    <td className="col-flex px-5 py-3 text-slate-700" title={String(o.proveedorNombre)}>{o.proveedorNombre}</td>
                    <td className="px-5 py-3 text-slate-600">
                      {o.total} {o.currencyCode}
                    </td>
                    <td className="px-5 py-3">
                      <OrdenEstadoBadge estado={o.status} />
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtFecha(o.issueDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
