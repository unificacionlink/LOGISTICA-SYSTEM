import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckSquare } from "lucide-react";
import { listarPendientes } from "../api/aprobaciones";
import { listarOrdenesPorAprobar } from "../api/compras";
import type { AprobacionPendiente, Orden } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { etiquetaRequerimientos } from "../components/compras/formato";
import { useRealtime } from "../realtime/useRealtime";
import PrioridadBadge from "../components/PrioridadBadge";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

export default function Aprobaciones() {
  const { onNuevaAprobacion, onOrdenDeCompra } = useRealtime();
  const { usuario, tieneAlgunRol } = useAuth();
  // DEC-L061: las OC las deciden Gerencia y Logística (Admin de respaldo), no el aprobador de CECO.
  const decideOrdenes = tieneAlgunRol("GERENCIA", "LOGISTICA", "ADMIN");
  const decideReqs = tieneAlgunRol("APROBADOR", "GERENCIA", "ADMIN");
  const [items, setItems] = useState<AprobacionPendiente[]>([]);
  const [ordenes, setOrdenes] = useState<Orden[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [nuevos, setNuevos] = useState<Set<string>>(new Set());

  useEffect(() => {
    Promise.all([
      decideReqs
        ? listarPendientes().then((res) => {
            if (res.ok) setItems(res.result ?? []);
            else setError(res.message);
          })
        : null,
      decideOrdenes
        ? listarOrdenesPorAprobar().then((res) => {
            if (res.ok) setOrdenes(res.result ?? []);
            else setError(res.message);
          })
        : null,
    ]).finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    return onOrdenDeCompra((o) => {
      setOrdenes((prev) => {
        const resto = prev.filter((p) => p.orderId !== o.orderId);
        return o.status === "POR_APROBAR" && o.createdById !== usuario?.id ? [o, ...resto] : resto;
      });
      if (o.status === "POR_APROBAR") setNuevos((prev) => new Set(prev).add(o.orderId));
    });
  }, [onOrdenDeCompra, usuario?.id]);

  useEffect(() => {
    return onNuevaAprobacion((pendiente) => {
      setItems((prev) => [pendiente, ...prev.filter((p) => p.approvalId !== pendiente.approvalId)]);
      setNuevos((prev) => new Set(prev).add(pendiente.approvalId));
    });
  }, [onNuevaAprobacion]);

  return (
    <div>
      <PageHeader title="Aprobaciones" />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {!cargando && ordenes.length > 0 && (
        <Card className="mb-4 overflow-hidden" data-tutorial="ordenes-por-aprobar">
          <div className="px-5 pt-4 pb-3 rotulo">Órdenes de compra ({ordenes.length})</div>
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-y border-slate-200 cabecera-tabla">
                  <th className="px-5 py-3">Código</th>
                  <th className="px-5 py-3">Proveedor</th>
                  <th className="col-sec px-5 py-3">Requerimiento</th>
                  <th className="px-5 py-3">Total</th>
                  <th className="col-sec px-5 py-3">Emitida por</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {ordenes.map((o) => (
                  <tr key={o.orderId} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">
                      <span className="inline-flex items-center gap-2">
                        {o.orderCode}
                        {nuevos.has(o.orderId) && <Badge tone="verde">nuevo</Badge>}
                      </span>
                    </td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={o.proveedorNombre}>{o.proveedorNombre}</td>
                    <td className="col-sec px-5 py-3 font-mono text-xs text-slate-500">{etiquetaRequerimientos(o.requestCode)}</td>
                    <td className="px-5 py-3 text-slate-600">
                      {o.total} {o.currencyCode}
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-500">{o.createdByName}</td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/compras/ordenes/${o.orderId}`} className="text-sm font-medium text-brand-600 hover:text-brand-700">
                        Revisar
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {(cargando || decideReqs) && (
      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : items.length === 0 ? (
          <EmptyState icon={CheckSquare} title={ordenes.length > 0 ? "No hay requerimientos por aprobar" : "No hay aprobaciones pendientes"} />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className="px-5 py-3">Código</th>
                  <th className="px-5 py-3">CECO</th>
                  <th className="px-5 py-3">Solicitante</th>
                  <th className="px-5 py-3">Prioridad</th>
                  <th className="col-sec px-5 py-3">Enviado</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((a) => (
                  <tr key={a.approvalId} className="hover:bg-slate-50">
                    <td className="px-5 py-3 font-mono text-xs text-slate-700">
                      <span className="inline-flex items-center gap-2">
                        {a.requerimiento.code}
                        {nuevos.has(a.approvalId) && <Badge tone="verde">nuevo</Badge>}
                      </span>
                    </td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(a.requerimiento.cecoNombre ?? "—")}>{a.requerimiento.cecoNombre ?? "—"}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(a.requerimiento.requesterNombre ?? "—")}>{a.requerimiento.requesterNombre ?? "—"}</td>
                    <td className="px-5 py-3">
                      <PrioridadBadge prioridad={a.requerimiento.priority} />
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtFechaHora(a.requerimiento.createdAt)}</td>
                    <td className="px-5 py-3 text-right">
                      <Link
                        to={`/requerimientos/${a.requerimiento.id}`}
                        className="text-sm font-medium text-brand-600 hover:text-brand-700"
                      >
                        Revisar
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      )}
      {!cargando && !decideReqs && ordenes.length === 0 && (
        <Card>
          <EmptyState icon={CheckSquare} title="No hay órdenes de compra por aprobar" />
        </Card>
      )}
    </div>
  );
}
