import { useId, useRef, useState } from "react";
import { CheckCircle2, Circle, PackageCheck, ScanLine } from "lucide-react";
import { confirmarPickingNota, embalarNota } from "../../api/almacen";
import type { NotaInventarioDetalle } from "../../api/types";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import CampoNumero from "../ui/CampoNumero";
import { inputClass } from "../ui/Field";
import { fmtDia, fmtFecha } from "../formato";

/**
 * Preparacion de la Nota de Inventario antes del envio (ALM-02, DEC-L062):
 * picking → embalaje (bultos + enlace de evidencia, V38.7) → envio. Una sola accion principal
 * segun el paso; el formulario de envio de la pagina aparece al terminar.
 */
export default function PreparacionNota({
  detalle,
  onCambio,
  onError,
}: {
  detalle: NotaInventarioDetalle;
  onCambio: (d: NotaInventarioDetalle) => void;
  onError: (mensaje: string) => void;
}) {
  const { nota, evidenciaEmbalaje } = detalle;
  const [ocupado, setOcupado] = useState(false);
  const [corrigiendo, setCorrigiendo] = useState(false);
  const [bultos, setBultos] = useState(nota.packageCount ? String(nota.packageCount) : "");
  const [enlace, setEnlace] = useState(nota.packingEvidenceUrl ?? "");
  const [errorCampo, setErrorCampo] = useState<string | null>(null);
  const bultosRef = useRef<HTMLInputElement>(null);
  const enlaceRef = useRef<HTMLInputElement>(null);
  const ids = { bultos: useId(), enlace: useId(), error: useId() };

  const enviada = !!nota.envio || nota.status !== "EMITIDA";
  const paso = !nota.pickedAt ? 0 : !nota.packedAt ? 1 : 2;
  const pasos = [
    { label: "Picking", hecho: !!nota.pickedAt, fecha: nota.pickedAt ? fmtFecha(nota.pickedAt) : null },
    { label: "Embalaje", hecho: !!nota.packedAt, fecha: nota.packedAt ? fmtFecha(nota.packedAt) : null },
    { label: "Envío", hecho: enviada, fecha: nota.envio?.shipDate ? fmtDia(nota.envio.shipDate) : null },
  ];

  async function ejecutar(accion: () => ReturnType<typeof confirmarPickingNota>) {
    setOcupado(true);
    try {
      const res = await accion();
      if (res.ok && res.result) {
        onCambio(res.result);
        setCorrigiendo(false);
      } else onError(res.message);
    } finally {
      setOcupado(false);
    }
  }

  async function onEmbalar(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(bultos);
    if (!bultos || n <= 0) {
      setErrorCampo("Indica cuántos bultos salen.");
      bultosRef.current?.focus();
      return;
    }
    if (!/^https?:\/\/\S+$/i.test(enlace.trim())) {
      setErrorCampo("Registra el enlace de la evidencia del embalaje (http o https).");
      enlaceRef.current?.focus();
      return;
    }
    setErrorCampo(null);
    await ejecutar(() => embalarNota(nota.inventoryNoteId, n, enlace.trim()));
  }

  const mostrarFormulario = !enviada && nota.pickedAt && (!nota.packedAt || corrigiendo);

  return (
    <Card className="mb-4" data-tutorial="preparacion-nota">
      <CardBody className="flex flex-col gap-4">
        <ol aria-label="Preparación de la nota" className="grid gap-3 sm:grid-cols-3">
          {pasos.map((p, i) => (
            <li key={p.label} aria-current={!enviada && i === paso ? "step" : undefined} className="flex min-w-0 items-start gap-2">
              {p.hecho ? (
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden="true" />
              ) : (
                <Circle className={`mt-0.5 size-4 shrink-0 ${i === paso ? "text-brand-600" : "text-slate-300"}`} aria-hidden="true" />
              )}
              <div className="min-w-0">
                <div className="rotulo">{p.label}</div>
                <div className={`mt-0.5 truncate text-sm ${p.hecho ? "text-emerald-700" : "text-slate-500"}`}>
                  {p.hecho ? (p.fecha ? `Hecho · ${p.fecha}` : "Hecho") : "Pendiente"}
                </div>
              </div>
            </li>
          ))}
        </ol>

        {nota.packedAt && !corrigiendo && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-slate-600">
            <span>{nota.packageCount} {nota.packageCount === 1 ? "bulto" : "bultos"}</span>
            {(nota.packingEvidenceUrl ?? evidenciaEmbalaje?.fileUrl) && (
              <a
                href={nota.packingEvidenceUrl ?? evidenciaEmbalaje?.fileUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-brand-700 hover:underline"
              >
                Evidencia del embalaje
              </a>
            )}
            {!enviada && (
              <button type="button" className="text-slate-500 hover:text-slate-700 hover:underline" onClick={() => setCorrigiendo(true)}>
                Corregir embalaje
              </button>
            )}
          </div>
        )}

        {!enviada && !nota.pickedAt && (
          <div>
            <Button icon={<ScanLine className="size-4" />} loading={ocupado} onClick={() => ejecutar(() => confirmarPickingNota(nota.inventoryNoteId))}>
              Confirmar picking
            </Button>
          </div>
        )}

        {mostrarFormulario && (
          <form onSubmit={onEmbalar} aria-label="Registrar embalaje" className="flex flex-wrap items-end gap-3" noValidate>
            <div className="flex w-28 flex-col gap-1.5">
              <label htmlFor={ids.bultos} className="text-[0.8125rem] font-medium text-slate-700">
                Bultos <span className="text-rose-600" aria-hidden="true">*</span>
              </label>
              <CampoNumero
                id={ids.bultos}
                ref={bultosRef}
                name="bultos"
                required
                decimales={0}
                maxEnteros={4}
                value={bultos}
                onChange={(v) => {
                  setBultos(v);
                  setErrorCampo(null);
                }}
                aria-invalid={errorCampo !== null && !bultos ? true : undefined}
                aria-describedby={errorCampo ? ids.error : undefined}
                className={`${inputClass} w-full`}
              />
            </div>
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
              <label htmlFor={ids.enlace} className="text-[0.8125rem] font-medium text-slate-700">
                Enlace de la evidencia <span className="text-rose-600" aria-hidden="true">*</span>
              </label>
              <input
                id={ids.enlace}
                ref={enlaceRef}
                name="evidencia"
                type="url"
                inputMode="url"
                required
                maxLength={500}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="https://"
                value={enlace}
                onChange={(e) => {
                  setEnlace(e.target.value);
                  setErrorCampo(null);
                }}
                aria-describedby={errorCampo ? ids.error : undefined}
                className={`${inputClass} w-full`}
              />
            </div>
            <Button type="submit" icon={<PackageCheck className="size-4" />} loading={ocupado}>
              Registrar embalaje
            </Button>
            {corrigiendo && (
              <Button type="button" variant="secondary" onClick={() => setCorrigiendo(false)}>
                Cancelar
              </Button>
            )}
            {errorCampo && (
              <p id={ids.error} role="alert" className="w-full text-sm text-rose-700">
                {errorCampo}
              </p>
            )}
          </form>
        )}
      </CardBody>
    </Card>
  );
}
