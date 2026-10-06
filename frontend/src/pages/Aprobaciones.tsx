import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckSquare } from "lucide-react";
import { listarPendientes } from "../api/aprobaciones";
import type { AprobacionPendiente } from "../api/types";
import { useRealtime } from "../realtime/RealtimeProvider";
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
  const { onNuevaAprobacion } = useRealtime();
  const [items, setItems] = useState<AprobacionPendiente[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [nuevos, setNuevos] = useState<Set<string>>(new Set());

  useEffect(() => {
    listarPendientes()
      .then((res) => {
        if (res.ok) setItems(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, []);

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

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={6} />
        ) : items.length === 0 ? (
          <EmptyState icon={CheckSquare} title="No hay aprobaciones pendientes" />
        ) : (
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
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
    </div>
  );
}
