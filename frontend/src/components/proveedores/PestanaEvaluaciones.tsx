import { useState } from "react";
import { ChevronDown, ClipboardCheck } from "lucide-react";
import { anularEvaluacionProveedor } from "../../api/proveedores";
import type { EvaluacionProveedor, TipoEvaluacion } from "../../api/types";
import { fmtDia } from "../formato";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";
import EmptyState from "../ui/EmptyState";
import { Skeleton } from "../ui/Skeleton";
import { fmtNota } from "./estados";
import { ResultadoBadge } from "./Insignias";

function Detalle({ e }: { e: EvaluacionProveedor }) {
  return (
    <div className="border-t border-slate-100 bg-slate-50/60 px-5 py-4 text-sm">
      <dl className="mb-3 grid grid-cols-2 gap-x-6 gap-y-1 sm:grid-cols-4">
        <div>
          <dt className="text-xs text-slate-500">Área</dt>
          <dd className="text-slate-800">{e.area ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Revisor del área</dt>
          <dd className="text-slate-800">{e.userAreaReviewer ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Conformidad Logística</dt>
          <dd className="text-slate-800">{e.logisticsConformity === "SI" ? "Sí" : e.logisticsConformity === "NO" ? "No" : "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500">Conformidad área</dt>
          <dd className="text-slate-800">{e.userAreaConformity === "SI" ? "Sí" : e.userAreaConformity === "NO" ? "No" : "—"}</dd>
        </div>
      </dl>
      {e.scope && <p className="mb-3 whitespace-normal text-slate-700">Alcance: {e.scope}</p>}
      <table className="tabla text-sm">
        <thead>
          <tr className="cabecera-tabla border-b border-slate-200">
            <th className="py-2 pr-3">Criterio</th>
            <th className="py-2 pr-3 text-right">Peso</th>
            <th className="py-2 pr-3">Respuesta</th>
            <th className="py-2 text-right">Aporte</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {e.criterios.map((c) => (
            <tr key={c.sortOrder}>
              <td className="col-flex py-2 pr-3 text-slate-800" title={c.criterionName}>
                {c.criterionName}
              </td>
              <td className="py-2 pr-3 text-right tabular-nums text-slate-600">{fmtNota(c.weight)}%</td>
              <td className="max-w-[14rem] truncate py-2 pr-3 text-slate-600" title={c.scoreLabel ?? undefined}>
                {fmtNota(c.score)}
                {c.scoreLabel ? ` · ${c.scoreLabel}` : ""}
              </td>
              <td className="py-2 text-right tabular-nums text-slate-800">{fmtNota((c.weight * c.score) / 100)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {e.observations && <p className="mt-3 whitespace-normal text-slate-700">Observaciones: {e.observations}</p>}
      {e.status === "ANULADA" && (
        <p className="mt-3 whitespace-normal text-rose-700">Anulada: {e.voidReason}</p>
      )}
    </div>
  );
}

/**
 * Historial de fichas de un tipo (F-LOG-03 o F-LOG-07), con detalle plegable
 * por criterio. Anular conserva la ficha con su motivo (DEC-L070).
 */
export default function PestanaEvaluaciones({
  kind,
  evaluaciones,
  puedeRegistrar,
  motivoNoRegistrar,
  onRegistrar,
  onCambio,
}: {
  kind: TipoEvaluacion;
  evaluaciones: EvaluacionProveedor[] | null;
  puedeRegistrar: boolean;
  motivoNoRegistrar?: string;
  onRegistrar: () => void;
  onCambio: (mensaje: string) => void;
}) {
  const confirmar = useConfirmar();
  const [abierta, setAbierta] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const lista = (evaluaciones ?? []).filter((e) => e.kind === kind);
  const titulo = kind === "SELECCION" ? "Registrar selección" : "Nueva evaluación";

  async function anular(e: EvaluacionProveedor) {
    const r = await confirmar({
      titulo: "¿Anular esta ficha?",
      mensaje: "Queda en el historial como anulada y deja de contar para la vigencia.",
      confirmar: "Anular",
      peligro: true,
      motivo: "Motivo de la anulación",
    });
    if (!r) return;
    const res = await anularEvaluacionProveedor(e.evaluationId, r.motivo);
    if (res.ok) onCambio(res.message);
    else setError(res.message);
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3">
        <p className="text-sm font-semibold text-slate-800">{kind === "SELECCION" ? "Selección F-LOG-03" : "Evaluaciones F-LOG-07"}</p>
        <span title={puedeRegistrar ? undefined : motivoNoRegistrar}>
          <Button size="sm" icon={<ClipboardCheck className="size-4" />} onClick={onRegistrar} disabled={!puedeRegistrar}>
            {titulo}
          </Button>
        </span>
      </div>
      {error && <Alert tone="danger" className="m-4">{error}</Alert>}
      {evaluaciones === null ? (
        <div className="space-y-3 p-5">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10" />
          ))}
        </div>
      ) : lista.length === 0 ? (
        <EmptyState
          icon={ClipboardCheck}
          title={kind === "SELECCION" ? "Sin ficha de selección" : "Sin evaluaciones de desempeño"}
          description={puedeRegistrar ? undefined : motivoNoRegistrar}
        />
      ) : (
        <ul className="divide-y divide-slate-100">
          {lista.map((e) => (
            <li key={e.evaluationId} className={e.status === "ANULADA" ? "opacity-70" : ""}>
              <div className="flex items-center gap-4 px-5 py-3">
                <button
                  type="button"
                  aria-expanded={abierta === e.evaluationId}
                  onClick={() => setAbierta((a) => (a === e.evaluationId ? null : e.evaluationId))}
                  className="flex min-w-0 flex-1 items-center gap-4 text-left"
                >
                  <ChevronDown
                    className={`size-4 shrink-0 text-slate-400 transition-transform ${abierta === e.evaluationId ? "rotate-180" : ""}`}
                  />
                  <span className="w-20 shrink-0 tabular-nums text-slate-700">{fmtDia(e.evaluatedAt)}</span>
                  <span className="w-16 shrink-0 font-serif text-xl tabular-nums text-slate-950">{fmtNota(e.finalScore)}</span>
                  <span className="shrink-0">
                    {e.status === "ANULADA" ? <Badge tone="gris">Anulada</Badge> : <ResultadoBadge resultado={e.result} />}
                  </span>
                  <span className="min-w-0 truncate text-sm text-slate-500">
                    {e.evaluador ?? "—"} · {e.templateCode} v{e.templateVersion}
                  </span>
                  <span className="ml-auto hidden shrink-0 text-sm text-slate-500 sm:inline">
                    Próxima: {fmtDia(e.nextEvaluationDate)}
                  </span>
                </button>
                {e.status === "REGISTRADA" && (
                  <Button size="sm" variant="ghost" onClick={() => anular(e)}>
                    Anular
                  </Button>
                )}
              </div>
              {abierta === e.evaluationId && <Detalle e={e} />}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
