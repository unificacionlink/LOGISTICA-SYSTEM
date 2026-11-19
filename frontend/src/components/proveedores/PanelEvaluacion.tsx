import { useEffect, useMemo, useState } from "react";
import { obtenerConfiguracionProveedores, registrarEvaluacionProveedor, subirDocumentoProveedor } from "../../api/proveedores";
import type { PlantillaEvaluacion, TipoEvaluacion } from "../../api/types";
import { hoyIso } from "../formato";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import CampoNumero from "../ui/CampoNumero";
import Field, { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";
import { Skeleton } from "../ui/Skeleton";
import { fmtNota, TIPO_EVALUACION } from "./estados";

function sumarMeses(iso: string, meses: number): string {
  const [a, m, d] = iso.split("-").map(Number);
  const f = new Date(a, m - 1 + meses, 1);
  const ultimo = new Date(f.getFullYear(), f.getMonth() + 1, 0).getDate();
  f.setDate(Math.min(d, ultimo));
  return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, "0")}-${String(f.getDate()).padStart(2, "0")}`;
}

/**
 * Ficha de seleccion (F-LOG-03) o evaluacion de desempeno (F-LOG-07) con la
 * plantilla vigente (DEC-L070). La nota se calcula en vivo igual que en el
 * servidor: Σ peso × puntaje / 100. El resultado no cambia el estado del proveedor.
 */
export default function PanelEvaluacion({
  supplierId,
  proveedor,
  kind,
  onCerrar,
  onRegistrada,
}: {
  supplierId: string;
  proveedor: string;
  kind: TipoEvaluacion;
  onCerrar: () => void;
  onRegistrada: (mensaje: string) => void;
}) {
  const [plantilla, setPlantilla] = useState<PlantillaEvaluacion | null>(null);
  const [puntajes, setPuntajes] = useState<Record<string, string>>({});
  const [fecha, setFecha] = useState(hoyIso());
  const [area, setArea] = useState("");
  const [alcance, setAlcance] = useState("");
  const [revisor, setRevisor] = useState("");
  const [confLogistica, setConfLogistica] = useState<"" | "SI" | "NO">("");
  const [confArea, setConfArea] = useState<"" | "SI" | "NO">("");
  const [observaciones, setObservaciones] = useState("");
  const [proxima, setProxima] = useState("");
  const [evidencia, setEvidencia] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    obtenerConfiguracionProveedores().then((res) => {
      if (res.ok && res.result) setPlantilla(res.result.plantillas.find((p) => p.kind === kind) ?? null);
      else setError(res.message);
    });
  }, [kind]);

  const nota = useMemo(() => {
    if (!plantilla) return null;
    let suma = 0;
    for (const c of plantilla.criterios) {
      const v = puntajes[c.criterionId];
      if (v === undefined || v === "") return null;
      suma += c.weight * Number(v);
    }
    return Math.round(suma) / 100;
  }, [plantilla, puntajes]);

  const proximaSugerida = plantilla && fecha ? sumarMeses(fecha, plantilla.validityMonths) : "";
  const completo = nota !== null && confLogistica !== "" && confArea !== "" && fecha !== "";

  async function registrar() {
    if (!plantilla || !completo) return;
    setGuardando(true);
    setError(null);
    try {
      const res = await registrarEvaluacionProveedor(supplierId, {
        kind,
        evaluatedAt: fecha,
        area: area || undefined,
        scope: alcance || undefined,
        userAreaReviewer: revisor || undefined,
        logisticsConformity: confLogistica as "SI" | "NO",
        userAreaConformity: confArea as "SI" | "NO",
        observations: observaciones || undefined,
        nextEvaluationDate: proxima || undefined,
        criterios: plantilla.criterios.map((c) => ({ criterionId: c.criterionId, score: Number(puntajes[c.criterionId]) })),
      });
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      let mensaje = res.message;
      if (evidencia) {
        const doc = await subirDocumentoProveedor(supplierId, evidencia, { evaluationId: res.result.evaluationId });
        if (!doc.ok) mensaje += ` No se pudo adjuntar la evidencia: ${doc.message}`;
      }
      onRegistrada(mensaje);
    } finally {
      setGuardando(false);
    }
  }

  const aprobada = nota !== null && plantilla ? nota >= plantilla.approvalThreshold : null;

  return (
    <PanelLateral
      titulo={TIPO_EVALUACION[kind]}
      subtitulo={plantilla ? `${proveedor} · ${plantilla.code} v${plantilla.version}` : proveedor}
      ancho="max-w-2xl"
      onCerrar={onCerrar}
      pie={
        <>
          <span className="mr-auto text-sm text-slate-600">
            Nota{" "}
            <span className={`font-semibold tabular-nums ${aprobada === false ? "text-rose-700" : "text-slate-900"}`}>
              {nota === null ? "—" : fmtNota(nota)}
            </span>
            {plantilla && <span className="text-slate-400"> / {fmtNota(plantilla.scaleMax)} · aprueba con {fmtNota(plantilla.approvalThreshold)}</span>}
          </span>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button loading={guardando} disabled={!completo} onClick={registrar}>
            Registrar
          </Button>
        </>
      }
    >
      {error && <Alert tone="danger" className="mx-5 mt-4">{error}</Alert>}
      {!plantilla ? (
        <div className="space-y-3 px-5 py-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : (
        <>
          <SeccionPanel titulo="Datos">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label="Fecha de evaluación" required>
                <input type="date" className={`${inputClass} w-full`} max={hoyIso()} value={fecha} onChange={(e) => setFecha(e.target.value)} />
              </Field>
              <Field label="Área">
                <input className={`${inputClass} w-full`} maxLength={120} value={area} onChange={(e) => setArea(e.target.value)} />
              </Field>
              <Field label="Alcance" className="sm:col-span-2">
                <input className={`${inputClass} w-full`} maxLength={500} value={alcance} onChange={(e) => setAlcance(e.target.value)} />
              </Field>
              <Field label="Revisor del área usuaria">
                <input className={`${inputClass} w-full`} maxLength={150} value={revisor} onChange={(e) => setRevisor(e.target.value)} />
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="Conf. Logística" required>
                  <select className={`${inputClass} w-full`} value={confLogistica} onChange={(e) => setConfLogistica(e.target.value as "" | "SI" | "NO")}>
                    <option value="">—</option>
                    <option value="SI">Sí</option>
                    <option value="NO">No</option>
                  </select>
                </Field>
                <Field label="Conf. área" required>
                  <select className={`${inputClass} w-full`} value={confArea} onChange={(e) => setConfArea(e.target.value as "" | "SI" | "NO")}>
                    <option value="">—</option>
                    <option value="SI">Sí</option>
                    <option value="NO">No</option>
                  </select>
                </Field>
              </div>
            </div>
          </SeccionPanel>

          <SeccionPanel titulo="Criterios">
            <ol className="space-y-3">
              {plantilla.criterios.map((c, i) => (
                <li key={c.criterionId} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5">
                  <p className="whitespace-normal text-sm text-slate-800">
                    <span className="text-slate-400">{i + 1}.</span> {c.name}
                  </p>
                  <span className="text-xs font-medium tabular-nums text-slate-500">{fmtNota(c.weight)}%</span>
                  <div className="col-span-2">
                    {c.opciones.length > 0 ? (
                      <select
                        className={`${inputClass} w-full`}
                        value={puntajes[c.criterionId] ?? ""}
                        onChange={(e) => setPuntajes((p) => ({ ...p, [c.criterionId]: e.target.value }))}
                        aria-label={c.name}
                      >
                        <option value="">Elige una respuesta</option>
                        {[...c.opciones]
                          .sort((a, b) => b.score - a.score)
                          .map((o) => (
                            <option key={o.score} value={o.score}>
                              {fmtNota(o.score)} · {o.label}
                            </option>
                          ))}
                      </select>
                    ) : (
                      <CampoNumero
                        className={`${inputClass} w-28`}
                        decimales={2}
                        maxEnteros={3}
                        max={plantilla.scaleMax}
                        value={puntajes[c.criterionId] ?? ""}
                        onChange={(v) => setPuntajes((p) => ({ ...p, [c.criterionId]: v }))}
                        aria-label={c.name}
                      />
                    )}
                  </div>
                </li>
              ))}
            </ol>
          </SeccionPanel>

          <SeccionPanel titulo="Cierre">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label="Observaciones y reclamos" className="sm:col-span-2">
                <textarea
                  className={`${inputClass} w-full whitespace-normal`}
                  rows={3}
                  maxLength={2000}
                  value={observaciones}
                  onChange={(e) => setObservaciones(e.target.value)}
                />
              </Field>
              <Field label="Próxima evaluación">
                <input
                  type="date"
                  className={`${inputClass} w-full`}
                  min={fecha}
                  value={proxima || proximaSugerida}
                  onChange={(e) => setProxima(e.target.value)}
                />
              </Field>
              <Field label="Evidencia">
                <input
                  type="file"
                  accept="application/pdf,image/jpeg,image/png,image/webp"
                  className="text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium"
                  onChange={(e) => setEvidencia(e.target.files?.[0] ?? null)}
                />
              </Field>
            </div>
          </SeccionPanel>
        </>
      )}
    </PanelLateral>
  );
}
