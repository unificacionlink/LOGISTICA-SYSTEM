import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { CheckCircle2, Paperclip, Truck } from "lucide-react";
import {
  adjuntarDocumentoEnvio,
  confirmarRecepcionEnvio,
  obtenerNota,
  registrarEnvio,
} from "../api/almacen";
import type { ModalidadEntrega, NotaInventarioDetalle as Detalle, TipoAdjuntoEnvio } from "../api/types";
import {
  ETIQUETA_ADJUNTO,
  ETIQUETA_MODALIDAD,
  EnvioBadge,
  NotaBadge,
  fmtCantidad,
  fmtDia,
  fmtFecha,
  hoyIso,
} from "../components/AlmacenBadges";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import BotonDescarga from "../components/BotonDescarga";
import { descargarPdfNota } from "../api/documentos";
import { useConfirmar } from "../components/ui/Confirmacion";

const labelClass = "rotulo";

function Dato({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className={labelClass}>{label}</div>
      <div className="mt-1 text-sm text-slate-700">{children}</div>
    </div>
  );
}

export default function NotaInventarioDetalle() {
  const confirmar = useConfirmar();
  const { id } = useParams<{ id: string }>();
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [subiendo, setSubiendo] = useState(false);

  const [envio, setEnvio] = useState({
    deliveryMode: "AGENCIA" as ModalidadEntrega,
    carrierName: "",
    trackingCode: "",
    packageCount: "",
    declaredWeight: "",
    currencyCode: "PEN",
    shippingCostNet: "",
    shippingTaxAmount: "",
    shipDate: hoyIso(),
    expectedDate: "",
  });
  const [observacion, setObservacion] = useState("");
  const [tipoAdjunto, setTipoAdjunto] = useState<TipoAdjuntoEnvio>("GUIA");
  const inputArchivo = useRef<HTMLInputElement>(null);

  function recargar() {
    if (!id) return Promise.resolve();
    return obtenerNota(id).then((res) => {
      if (res.ok && res.result) setDetalle(res.result);
      else setError(res.message);
    });
  }

  useEffect(() => {
    recargar().finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function onRegistrarEnvio(e: React.FormEvent) {
    e.preventDefault();
    if (!id) return;
    setError(null);
    setExito(null);
    const num = (v: string) => (v === "" ? undefined : Number(v));
    setEnviando(true);
    try {
      const res = await registrarEnvio(id, {
        deliveryMode: envio.deliveryMode,
        carrierName: envio.carrierName || undefined,
        trackingCode: envio.trackingCode || undefined,
        packageCount: num(envio.packageCount),
        declaredWeight: num(envio.declaredWeight),
        currencyCode: envio.shippingCostNet ? envio.currencyCode : undefined,
        shippingCostNet: num(envio.shippingCostNet),
        shippingTaxAmount: num(envio.shippingTaxAmount),
        shipDate: envio.shipDate || undefined,
        expectedDate: envio.expectedDate || undefined,
      });
      if (res.ok && res.result) {
        setDetalle(res.result);
        setExito("Envío registrado. La nota quedó como despachada.");
      } else {
        setError(res.message);
      }
    } finally {
      setEnviando(false);
    }
  }

  async function onConfirmar() {
    const shipmentId = detalle?.nota.envio?.shipmentId;
    if (!shipmentId) return;
    const r = await confirmar({
      titulo: "¿Confirmar la recepción en destino?",
      mensaje: "El estado de atención del requerimiento se recalcula con esta entrega.",
      confirmar: "Confirmar recepción",
    });
    if (!r) return;
    setError(null);
    setExito(null);
    setConfirmando(true);
    try {
      const res = await confirmarRecepcionEnvio(shipmentId, observacion || undefined);
      if (res.ok && res.result) {
        setDetalle(res.result);
        setExito("Recepción en destino confirmada. El estado del requerimiento se recalculó automáticamente.");
      } else {
        setError(res.message);
      }
    } finally {
      setConfirmando(false);
    }
  }

  async function onAdjuntar(file: File | undefined) {
    const shipmentId = detalle?.nota.envio?.shipmentId;
    if (!file || !shipmentId) return;
    setError(null);
    setSubiendo(true);
    try {
      const res = await adjuntarDocumentoEnvio(shipmentId, tipoAdjunto, file);
      if (res.ok) await recargar();
      else setError(res.message);
    } finally {
      setSubiendo(false);
      if (inputArchivo.current) inputArchivo.current.value = "";
    }
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (error && !detalle) return <Alert tone="danger">{error}</Alert>;
  if (!detalle) return null;

  const { nota, items, documentos } = detalle;
  const e = nota.envio;
  const puedeConfirmar = e && (e.status === "EN_TRANSITO" || e.status === "OBSERVADO");

  return (
    <div>
      <PageHeader
        title={<span className="font-mono">{nota.inventoryNoteCode}</span>}
        subtitle={`Requerimiento ${nota.requestCode} · ${nota.cecoNombre ?? "—"}`}
        backTo="/almacen?tab=notas"
        backLabel="Almacén"
        actions={
          <>
            <BotonDescarga descargar={() => descargarPdfNota(nota.inventoryNoteId, nota.inventoryNoteCode)} />
            <NotaBadge estado={nota.status} />
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      <Card className="mb-4">
        <CardBody className="grid gap-4 sm:grid-cols-4">
          <Dato label="Tipo de movimiento">{nota.movementType}</Dato>
          <Dato label="Picking">
            {fmtDia(nota.pickingDate)} · {nota.pickingUserName}
          </Dato>
          <Dato label="Estado del bien">{nota.itemState}</Dato>
          <Dato label="Bultos">{nota.packageCount ?? "—"}</Dato>
          <Dato label="Unidades">{fmtCantidad(nota.totalItemQty)}</Dato>
          <Dato label="Valor referencial">
            {nota.totalValueSnapshot !== null ? `${fmtCantidad(nota.totalValueSnapshot)} PEN` : "—"}
          </Dato>
          <Dato label="Emitida">{fmtFecha(nota.issuedAt)}</Dato>
          {nota.comments && <Dato label="Comentarios">{nota.comments}</Dato>}
        </CardBody>
      </Card>

      <Card className="mb-4 overflow-hidden">
        <CardBody className="pb-3">
          <div className={labelClass}>Detalle despachado ({items.length})</div>
        </CardBody>
        <div className="overflow-hidden">
          <table className="tabla">
            <thead>
              <tr className="border-y border-slate-200 cabecera-tabla">
                <th className="px-5 py-2.5">SKU</th>
                <th className="px-5 py-2.5">Producto</th>
                <th className="px-5 py-2.5">Cantidad</th>
                <th className="px-5 py-2.5">Costo ref.</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((it, i) => (
                <tr key={i}>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante ?? it.skuBase}</td>
                  <td className="col-flex px-5 py-2.5 text-slate-700" title={it.nombreProducto}>
                    {it.nombreProducto}
                    {it.atributo ? <span className="text-slate-400"> · {it.atributo}</span> : null}
                  </td>
                  <td className="px-5 py-2.5 text-slate-600">{fmtCantidad(it.qty)}</td>
                  <td className="px-5 py-2.5 text-slate-600">{it.purchaseCostReference ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {!e && nota.status === "EMITIDA" && (
        <form onSubmit={onRegistrarEnvio}>
          <Card className="mb-4">
            <CardBody className="grid gap-4 sm:grid-cols-4">
              <div className={`${labelClass} sm:col-span-4`}>Registrar envío</div>
              <Field label="Modalidad" required>
                <select
                  value={envio.deliveryMode}
                  onChange={(ev) => setEnvio({ ...envio, deliveryMode: ev.target.value as ModalidadEntrega })}
                  className={`${inputClass} w-full`}
                >
                  {Object.entries(ETIQUETA_MODALIDAD).map(([v, t]) => (
                    <option key={v} value={v}>{t}</option>
                  ))}
                </select>
              </Field>
              <Field label="Transportista">
                <input
                  value={envio.carrierName}
                  onChange={(ev) => setEnvio({ ...envio, carrierName: ev.target.value })}
                  placeholder="Shalom, Olva…"
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Código de seguimiento">
                <input
                  value={envio.trackingCode}
                  onChange={(ev) => setEnvio({ ...envio, trackingCode: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Bultos">
                <input
                  type="number"
                  min="1"
                  value={envio.packageCount}
                  onChange={(ev) => setEnvio({ ...envio, packageCount: ev.target.value })}
                  placeholder={nota.packageCount ? String(nota.packageCount) : ""}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Costo neto (sin IGV)">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={envio.shippingCostNet}
                  onChange={(ev) => setEnvio({ ...envio, shippingCostNet: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="IGV">
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={envio.shippingTaxAmount}
                  onChange={(ev) => setEnvio({ ...envio, shippingTaxAmount: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Moneda">
                <select
                  value={envio.currencyCode}
                  onChange={(ev) => setEnvio({ ...envio, currencyCode: ev.target.value })}
                  className={`${inputClass} w-full`}
                >
                  <option value="PEN">PEN</option>
                  <option value="USD">USD</option>
                </select>
              </Field>
              <Field label="Peso declarado (kg)">
                <input
                  type="number"
                  min="0"
                  step="any"
                  value={envio.declaredWeight}
                  onChange={(ev) => setEnvio({ ...envio, declaredWeight: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Fecha de envío">
                <input
                  type="date"
                  value={envio.shipDate}
                  onChange={(ev) => setEnvio({ ...envio, shipDate: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Llegada estimada">
                <input
                  type="date"
                  value={envio.expectedDate}
                  onChange={(ev) => setEnvio({ ...envio, expectedDate: ev.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <div className="flex items-end sm:col-span-2">
                <Button type="submit" loading={enviando} icon={<Truck className="size-4" />}>
                  {enviando ? "Registrando…" : "Registrar envío"}
                </Button>
              </div>
            </CardBody>
          </Card>
        </form>
      )}

      {e && (
        <Card className="mb-4">
          <CardBody className="grid gap-4 sm:grid-cols-4">
            <div className="flex items-center gap-3 sm:col-span-4">
              <span className={labelClass}>Envío</span>
              <EnvioBadge estado={e.status} />
            </div>
            <Dato label="Modalidad">{ETIQUETA_MODALIDAD[e.deliveryMode]}</Dato>
            <Dato label="Transportista">{e.carrierName ?? "—"}</Dato>
            <Dato label="Seguimiento">{e.trackingCode ?? "—"}</Dato>
            <Dato label="Bultos">{e.packageCount ?? "—"}</Dato>
            <Dato label="Costo neto / IGV / total">
              {e.shippingCostNet !== null
                ? `${e.shippingCostNet} / ${e.shippingTaxAmount} / ${e.shippingCostTotal} ${e.currencyCode}`
                : "—"}
            </Dato>
            <Dato label="Fecha de envío">{fmtDia(e.shipDate)}</Dato>
            <Dato label="Llegada estimada">{fmtDia(e.expectedDate)}</Dato>
            {e.receivedAt && (
              <Dato label="Recepcionado">
                {fmtFecha(e.receivedAt)} · {e.receivedByName}
              </Dato>
            )}
            {e.deliveryObservation && <Dato label="Observación de entrega">{e.deliveryObservation}</Dato>}

            <div className="border-t border-slate-100 pt-4 sm:col-span-4">
              <div className={labelClass}>Documentos del envío</div>
              {documentos.length > 0 ? (
                <ul className="mt-2 flex flex-col gap-1 text-sm">
                  {documentos.map((d) => (
                    <li key={d.documentId}>
                      <a
                        href={d.fileUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-brand-600 hover:text-brand-700"
                      >
                        {ETIQUETA_ADJUNTO[d.documentType]}: {d.fileName}
                      </a>
                      <span className="ml-2 text-xs text-slate-400">{fmtFecha(d.uploadedAt)}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-1 text-xs text-slate-400">Sin documentos adjuntos.</p>
              )}
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <select
                  value={tipoAdjunto}
                  onChange={(ev) => setTipoAdjunto(ev.target.value as TipoAdjuntoEnvio)}
                  className={`${inputClass} py-1.5 text-xs`}
                  aria-label="Tipo de documento"
                >
                  {Object.entries(ETIQUETA_ADJUNTO).map(([v, t]) => (
                    <option key={v} value={v}>{t}</option>
                  ))}
                </select>
                <input
                  ref={inputArchivo}
                  type="file"
                  accept="application/pdf,image/jpeg,image/png,image/webp"
                  className="hidden"
                  onChange={(ev) => onAdjuntar(ev.target.files?.[0])}
                />
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  loading={subiendo}
                  icon={<Paperclip className="size-3.5" />}
                  onClick={() => inputArchivo.current?.click()}
                >
                  {subiendo ? "Subiendo…" : "Adjuntar archivo"}
                </Button>
              </div>
            </div>

            {puedeConfirmar && (
              <div className="flex flex-wrap items-end gap-3 border-t border-slate-100 pt-4 sm:col-span-4">
                <Field label="Observación de entrega" className="min-w-64 flex-1">
                  <input
                    value={observacion}
                    onChange={(ev) => setObservacion(ev.target.value)}
                    placeholder="Opcional"
                    className={`${inputClass} w-full`}
                  />
                </Field>
                <Button type="button" loading={confirmando} icon={<CheckCircle2 className="size-4" />} onClick={onConfirmar}>
                  {confirmando ? "Confirmando…" : "Confirmar recepción en destino"}
                </Button>
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
