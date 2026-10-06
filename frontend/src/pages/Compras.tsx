import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ClipboardList, FileQuestion, ShoppingCart, Wallet } from "lucide-react";
import { listarCajaChica, obtenerReglaCajaChica } from "../api/cajachica";
import { listarPendientesCompra, listarRfqsAbiertas } from "../api/compras";
import type { CompraCajaChica, ReglaCajaChica, Requerimiento, Rfq } from "../api/types";
import { fmtDia } from "../components/AlmacenBadges";
import PestanaCajaChica from "../components/cajachica/PestanaCajaChica";
import EstadoBadge from "../components/EstadoBadge";
import PrioridadBadge from "../components/PrioridadBadge";
import RfqEstadoBadge from "../components/RfqEstadoBadge";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

/** Urgente/critico con fecha requerida hoy o vencida: candidato a Caja Chica "mismo dia" (RF-023). */
function esParaHoy(r: Requerimiento): boolean {
  const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  return (r.priority === "URGENTE" || r.priority === "CRITICA") && !!r.requiredDate && r.requiredDate <= hoy;
}

type Pestana = "pendientes" | "rfq" | "caja";

const thClass = "px-5 py-3";
const linkClass = "text-sm font-medium text-brand-600 hover:text-brand-700";

export default function Compras() {
  const [params, setParams] = useSearchParams();
  const tab = (["pendientes", "rfq", "caja"].includes(params.get("tab") ?? "") ? params.get("tab") : "pendientes") as Pestana;
  const [rfqs, setRfqs] = useState<Rfq[]>([]);
  const [pendientes, setPendientes] = useState<Requerimiento[]>([]);
  const [compras, setCompras] = useState<CompraCajaChica[]>([]);
  const [regla, setRegla] = useState<ReglaCajaChica | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    Promise.all([listarRfqsAbiertas(), listarPendientesCompra(), listarCajaChica(), obtenerReglaCajaChica()])
      .then(([resRfq, resPend, resCaja, resRegla]) => {
        if (resRfq.ok) setRfqs(resRfq.result ?? []);
        else setError(resRfq.message);
        if (resPend.ok) setPendientes(resPend.result ?? []);
        else setError(resPend.message);
        if (resCaja.ok) setCompras(resCaja.result ?? []);
        if (resRegla.ok) setRegla(resRegla.result);
      })
      .finally(() => setCargando(false));
  }, []);

  const porRegularizar = compras.filter((c) => c.estado === "PENDIENTE_REGULARIZACION" || c.estado === "OBSERVADO").length;
  const pestanas: { id: Pestana; label: string; icon: typeof ShoppingCart; conteo: number }[] = [
    { id: "pendientes", label: "Pendientes de compra", icon: ShoppingCart, conteo: pendientes.length },
    { id: "rfq", label: "RFQ en curso", icon: FileQuestion, conteo: rfqs.length },
    { id: "caja", label: "Caja chica", icon: Wallet, conteo: porRegularizar },
  ];

  return (
    <div>
      <PageHeader
        title="Compras"
        actions={
          <Button href="/compras/ordenes" variant="secondary" icon={<ClipboardList className="size-4" />}>
            Órdenes emitidas
          </Button>
        }
      />

      <div role="tablist" className="mb-4 flex flex-wrap gap-1 border-b border-slate-200">
        {pestanas.map(({ id, label, icon: Icon, conteo }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => setParams(id === "pendientes" ? {} : { tab: id }, { replace: true })}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="size-4" />
            {label}
            {!cargando && conteo > 0 && <Badge tone={id === "caja" ? "amarillo" : "gris"}>{conteo}</Badge>}
          </button>
        ))}
      </div>

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {tab === "caja" ? (
        cargando ? (
          <Card className="overflow-hidden">
            <SkeletonTable cols={5} />
          </Card>
        ) : (
          <PestanaCajaChica
            compras={compras}
            regla={regla}
            onCompra={(c) => setCompras((prev) => prev.map((x) => (x.id === c.id ? c : x)))}
            onRegla={setRegla}
          />
        )
      ) : tab === "rfq" ? (
        <Card className="overflow-hidden">
          {cargando ? (
            <SkeletonTable cols={6} />
          ) : rfqs.length === 0 ? (
            <EmptyState icon={FileQuestion} title="No hay RFQ en curso" />
          ) : (
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className={thClass}>RFQ</th>
                  <th className={thClass}>Proveedor</th>
                  <th className={thClass}>Requerimiento</th>
                  <th className={`${thClass} col-sec`}>Emitida</th>
                  <th className={thClass}>Estado</th>
                  <th className={thClass} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rfqs.map((r) => (
                  <tr key={r.rfqId} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-xs text-slate-700">{r.rfqCode}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(r.proveedorNombre)}>{r.proveedorNombre}</td>
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-xs text-slate-500">{r.requestCode}</td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtDia(r.issueDate)}</td>
                    <td className="px-5 py-3">
                      <RfqEstadoBadge estado={r.status} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/compras/rfq/${r.rfqId}`} className={linkClass}>
                        Cotizaciones
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      ) : (
        <Card className="overflow-hidden">
          {cargando ? (
            <SkeletonTable cols={7} />
          ) : pendientes.length === 0 ? (
            <EmptyState icon={ShoppingCart} title="No hay requerimientos pendientes de compra" />
          ) : (
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className={thClass}>Código</th>
                  <th className={thClass}>CECO</th>
                  <th className={`${thClass} col-sec`}>Solicitante</th>
                  <th className={thClass}>Prioridad</th>
                  <th className={thClass}>Estado</th>
                  <th className={`${thClass} col-sec`}>Creado</th>
                  <th className={thClass} />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pendientes.map((r) => (
                  <tr key={r.id} className="hover:bg-slate-50">
                    <td className="whitespace-nowrap px-5 py-3 font-mono text-xs text-slate-700">{r.code}</td>
                    <td className="col-flex px-5 py-3 text-slate-600" title={String(r.cecoNombre ?? "—")}>{r.cecoNombre ?? "—"}</td>
                    <td className="col-sec col-flex px-5 py-3 text-slate-600" title={String(r.requesterNombre ?? "—")}>
                      {r.requesterNombre ?? "—"}
                    </td>
                    <td className="px-5 py-3">
                      <span className="flex items-center gap-1.5">
                        <PrioridadBadge prioridad={r.priority} />
                        {esParaHoy(r) && <Badge tone="amarillo">Hoy</Badge>}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <EstadoBadge estado={r.status} />
                    </td>
                    <td className="col-sec px-5 py-3 text-xs text-slate-400">{fmtFechaHora(r.createdAt)}</td>
                    <td className="px-5 py-3 text-right">
                      <Link to={`/compras/requerimientos/${r.id}/rfq/nuevo`} className={linkClass}>
                        Gestionar compra
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}
    </div>
  );
}
