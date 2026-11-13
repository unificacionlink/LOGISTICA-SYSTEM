import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ChevronDown, FileText, Mail, Plus, ScanText, Upload, X } from "lucide-react";
import {
  cerrarRfq,
  confirmarEnvioRfq,
  descartarCotizacion,
  extraerOcrCotizacion,
  listarMonedas,
  obtenerEnlaceRfq,
  obtenerRfq,
  registrarCotizacion,
  subirDocumentoCotizacion,
} from "../api/compras";
import type { EnlaceCotizacion, Moneda, OcrCotizacion, RfqDetalle } from "../api/types";
import EnlaceProveedor from "../components/compras/EnlaceProveedor";
import { useRealtime } from "../realtime/useRealtime";
import CotizacionEstadoBadge from "../components/CotizacionEstadoBadge";
import RfqEstadoBadge from "../components/RfqEstadoBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody, CardHeader } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import BotonDescarga from "../components/BotonDescarga";
import MenuAcciones from "../components/ui/MenuAcciones";
import { descargarPdfRfq } from "../api/documentos";
import { useConfirmar } from "../components/ui/Confirmacion";
import { cantidad } from "../components/compras/formato";

import CampoNumero from "../components/ui/CampoNumero";
/** Texto de la solicitud para el borrador de correo (no constituye orden de compra). */
function mensajeSolicitud(d: RfqDetalle, urlEnlace: string | null): string {
  const lineas = d.items.map(
    (it) =>
      `- ${it.codigoVariante ?? it.skuBase} | ${it.nombreProducto}${it.atributo ? ` (${it.atributo})` : ""} | ${cantidad(it.qty)} ${it.unidadMedida}` +
      (it.technicalSpecification ? `\n  Especificación: ${it.technicalSpecification}` : ""),
  );
  return [
    `Solicitud de cotización ${d.rfq.rfqCode} — ${d.empresaCode}`,
    "",
    ...lineas,
    "",
    ...(urlEnlace ? ["Puede cotizar en línea, sobre estas mismas líneas, aquí:", urlEnlace, ""] : []),
    "Indicar precio unitario por producto, moneda, condición de pago, plazo y lugar de entrega, y vigencia de la oferta.",
    "Esta solicitud no constituye una orden de compra.",
  ].join("\n");
}

interface FilaCotizacion {
  unitCost: string;
  offeredBrand: string;
  offeredModel: string;
}

export default function ComprasRfqDetalle() {
  const confirmar = useConfirmar();
  const { id } = useParams<{ id: string }>();
  // WhatsApp no va en esta version: se registra como Correo u Otro.
  const [canal, setCanal] = useState<"CORREO" | "OTRO">("CORREO");

  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detalle, setDetalle] = useState<RfqDetalle | null>(null);
  const [enlace, setEnlace] = useState<EnlaceCotizacion | null>(null);
  const { onCotizacionPorEnlace } = useRealtime();
  const [monedas, setMonedas] = useState<Moneda[]>([]);

  const [supplierQuoteNumber, setSupplierQuoteNumber] = useState("");
  const [issueDate, setIssueDate] = useState("");
  const [currencyCode, setCurrencyCode] = useState("PEN");
  const [paymentCondition, setPaymentCondition] = useState("CONTADO");
  const [deliveryMode, setDeliveryMode] = useState("AGENCIA");
  const [leadDays, setLeadDays] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [filas, setFilas] = useState<Record<string, FilaCotizacion>>({});
  const [errorForm, setErrorForm] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [ocr, setOcr] = useState<OcrCotizacion | null>(null);
  const [extrayendo, setExtrayendo] = useState(false);
  const [itemsSugeridosIds, setItemsSugeridosIds] = useState<Set<string>>(new Set());

  const [procesandoId, setProcesandoId] = useState<string | null>(null);
  // Formulario plegado cuando ya hay cotizaciones; abierto si aun no llega ninguna.
  const [formAbierto, setFormAbierto] = useState<boolean | null>(null);
  const [conMarca, setConMarca] = useState(false);

  function cargar() {
    if (!id) return;
    return obtenerRfq(id).then((res) => {
      if (res.ok && res.result) {
        setDetalle(res.result);
        setFilas((prev) => {
          const siguiente: Record<string, FilaCotizacion> = {};
          for (const it of res.result!.items) {
            siguiente[it.rfqItemId] = prev[it.rfqItemId] ?? { unitCost: "", offeredBrand: "", offeredModel: "" };
          }
          return siguiente;
        });
      } else {
        setError(res.message);
      }
    });
  }

  function cargarEnlace() {
    if (!id) return;
    return obtenerEnlaceRfq(id).then((res) => res.ok && setEnlace(res.result));
  }

  useEffect(() => {
    Promise.all([
      cargar(),
      cargarEnlace(),
      listarMonedas().then((res) => res.ok && setMonedas(res.result ?? [])),
    ]).finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // El proveedor respondio por su enlace: se recarga sin perder la URL recien generada.
  useEffect(
    () =>
      onCotizacionPorEnlace((e) => {
        if (e.rfqId !== id) return;
        cargar();
        setEnlace((prev) => (prev ? { ...prev, respondidoEl: new Date().toISOString() } : prev));
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [id, onCotizacionPorEnlace],
  );

  const items = useMemo(() => detalle?.items ?? [], [detalle]);

  async function onRegistrarCotizacion(e: React.FormEvent) {
    e.preventDefault();
    if (!id) return;
    const itemsInput = items
      .map((it) => ({ item: it, fila: filas[it.rfqItemId] }))
      .filter(({ fila }) => fila && Number(fila.unitCost) > 0)
      .map(({ item, fila }) => ({
        rfqItemId: item.rfqItemId,
        qty: item.qty,
        unitCost: Number(fila.unitCost),
        offeredBrand: fila.offeredBrand || undefined,
        offeredModel: fila.offeredModel || undefined,
      }));
    if (itemsInput.length === 0) {
      setErrorForm("Ingresa el costo unitario de al menos un item.");
      return;
    }
    setErrorForm(null);
    setGuardando(true);
    try {
      let documentId: string | undefined;
      if (archivo) {
        const subida = await subirDocumentoCotizacion(archivo);
        if (!subida.ok || !subida.result) {
          setErrorForm(subida.message);
          return;
        }
        documentId = subida.result.documentId;
      }
      const res = await registrarCotizacion(id, {
        supplierQuoteNumber: supplierQuoteNumber || undefined,
        issueDate: issueDate || undefined,
        currencyCode,
        paymentCondition,
        deliveryMode,
        leadDays: leadDays ? Number(leadDays) : undefined,
        documentId,
        items: itemsInput,
      });
      if (!res.ok) {
        setErrorForm(res.message);
        return;
      }
      setSupplierQuoteNumber("");
      setIssueDate("");
      setArchivo(null);
      setOcr(null);
      setFilas((prev) => {
        const limpio: Record<string, FilaCotizacion> = {};
        for (const key of Object.keys(prev)) limpio[key] = { unitCost: "", offeredBrand: "", offeredModel: "" };
        return limpio;
      });
      setFormAbierto(false);
      await cargar();
    } finally {
      setGuardando(false);
    }
  }

  async function onExtraerOcr() {
    if (!archivo || !id) return;
    setExtrayendo(true);
    setErrorForm(null);
    try {
      const res = await extraerOcrCotizacion(id, archivo);
      if (!res.ok || !res.result) {
        setErrorForm(res.message);
        return;
      }
      setOcr(res.result);
      if (res.result.monedaDetectada) setCurrencyCode(res.result.monedaDetectada);
      if (res.result.fechaDetectada) setIssueDate(res.result.fechaDetectada);
      if (res.result.plazoDetectadoDias) setLeadDays(String(res.result.plazoDetectadoDias));

      // Precarga el costo unitario sugerido por item (sin pisar lo que el usuario ya haya tipeado).
      const nuevosIds = new Set<string>();
      setFilas((prev) => {
        const siguiente = { ...prev };
        for (const sugerencia of res.result!.itemsSugeridos) {
          const actual = siguiente[sugerencia.rfqItemId];
          if (actual && !actual.unitCost) {
            siguiente[sugerencia.rfqItemId] = { ...actual, unitCost: String(sugerencia.costoSugerido) };
            nuevosIds.add(sugerencia.rfqItemId);
          }
        }
        return siguiente;
      });
      setItemsSugeridosIds(nuevosIds);
    } finally {
      setExtrayendo(false);
    }
  }

  async function onCerrarRfq() {
    if (!id) return;
    const r = await confirmar({
      titulo: "¿Cerrar esta RFQ sin emitir orden de compra?",
      confirmar: "Cerrar RFQ",
      peligro: true,
      motivo: "Motivo del cierre",
    });
    if (!r) return;
    setProcesandoId("__cerrar__");
    try {
      const res = await cerrarRfq(id, r.motivo);
      if (res.ok) await cargar();
      else setError(res.message);
    } finally {
      setProcesandoId(null);
    }
  }

  async function onDescartar(cotizacionId: string) {
    const r = await confirmar({
      titulo: "¿Descartar esta cotización?",
      mensaje: "Deja de compararse; si ganó productos, quedarán sin adjudicar.",
      confirmar: "Descartar",
      peligro: true,
    });
    if (!r) return;
    setProcesandoId(cotizacionId);
    try {
      const res = await descartarCotizacion(cotizacionId);
      if (res.ok) await cargar();
      else setError(res.message);
    } finally {
      setProcesandoId(null);
    }
  }

  async function onConfirmarEnvio() {
    if (!id) return;
    const r = await confirmar({
      titulo: "¿Confirmas que ya enviaste la solicitud al proveedor?",
      confirmar: "Confirmar envío",
    });
    if (!r) return;
    setProcesandoId("__enviar__");
    try {
      const res = await confirmarEnvioRfq(id, canal);
      if (res.ok) await cargar();
      else setError(res.message);
    } finally {
      setProcesandoId(null);
    }
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
          </CardBody>
        </Card>
      </div>
    );
  }
  if (error && !detalle) return <Alert tone="danger">{error}</Alert>;
  if (!detalle) return null;

  const { rfq, cotizaciones } = detalle;
  const rfqCerrada = rfq.status === "CERRADA";
  const porEnviar = rfq.status === "BORRADOR";
  const mostrarForm = !rfqCerrada && !porEnviar && (formAbierto ?? cotizaciones.length === 0);
  const mensaje = mensajeSolicitud(detalle, enlace?.url ?? null);
  const consolidados = Array.from(
    new Map(items.filter((it) => it.consolidationId).map((it) => [it.consolidationId!, it])).values(),
  );

  return (
    <div>
      <PageHeader
        title={<span className="font-mono">{rfq.rfqCode}</span>}
        subtitle={`${rfq.proveedorNombre} · ${rfq.empresaCode}${rfq.requestCode ? ` · ${rfq.requestCode}` : ""}`}
        backTo="/compras?tab=solicitudes"
        backLabel="Solicitudes"
        actions={
          <>
            <RfqEstadoBadge estado={rfq.status} />
            <BotonDescarga descargar={() => descargarPdfRfq(rfq.rfqId, rfq.rfqCode)} />
            {!rfqCerrada && (
              <MenuAcciones
                acciones={[
                  { label: "Cerrar RFQ", onClick: onCerrarRfq, peligro: true, disabled: procesandoId === "__cerrar__" },
                ]}
              />
            )}
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {!rfqCerrada && (
        <EnlaceProveedor
          rfqId={rfq.rfqId}
          rfqCode={rfq.rfqCode}
          enlace={enlace}
          onCambio={(e) => {
            setError(null);
            setEnlace(e);
          }}
          onError={setError}
        />
      )}

      {porEnviar && (
        <Card className="mb-6">
          <CardBody className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0 text-sm">
              <p className="font-medium text-slate-900">{detalle.proveedorContacto ?? rfq.proveedorNombre}</p>
              <p className="text-xs text-slate-500">
                {[detalle.proveedorEmail, detalle.proveedorTelefono].filter(Boolean).join(" · ") || "Sin datos de contacto"}
              </p>
              <div className="mt-2 flex flex-wrap gap-3 text-xs font-medium">
                {detalle.proveedorEmail && (
                  <a
                    href={`mailto:${detalle.proveedorEmail}?subject=${encodeURIComponent(`Solicitud de cotización ${rfq.rfqCode}`)}&body=${encodeURIComponent(mensaje)}`}
                    className="inline-flex items-center gap-1 text-brand-600 hover:text-brand-700"
                    onClick={() => setCanal("CORREO")}
                  >
                    <Mail className="size-3.5" />
                    Abrir correo
                  </a>
                )}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <select
                aria-label="Canal de envío"
                value={canal}
                onChange={(e) => setCanal(e.target.value as typeof canal)}
                className={`${inputClass} py-1.5`}
              >
                <option value="CORREO">Correo</option>
                <option value="OTRO">Otro</option>
              </select>
              <Button size="sm" loading={procesandoId === "__enviar__"} onClick={onConfirmarEnvio}>
                Confirmar envío
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      <Card className="mb-6 overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Items ({items.length})</div>
        </CardBody>
        <div className="overflow-hidden">
          <table className="tabla">
            <thead>
              <tr className="border-y border-slate-200 cabecera-tabla">
                <th className="px-5 py-2.5">SKU</th>
                <th className="px-5 py-2.5">Producto</th>
                <th className="px-5 py-2.5">Cantidad</th>
                <th className="px-5 py-2.5 col-sec">Especificación</th>
                <th className="px-5 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((it) => (
                <tr key={it.rfqItemId}>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante}</td>
                  <td className="col-flex px-5 py-2.5 text-slate-700" title={String(it.nombreProducto)}>{it.nombreProducto}</td>
                  <td className="px-5 py-2.5 text-slate-600">
                    {it.qty} {it.unidadMedida}
                  </td>
                  <td className="col-sec col-flex px-5 py-2.5 text-slate-500" title={String(it.technicalSpecification ?? "—")}>{it.technicalSpecification ?? "—"}</td>
                  <td className="whitespace-nowrap px-5 py-2.5 text-right">
                    {it.consolidationId && (
                      <Link
                        to={`/compras/consolidados/${it.consolidationId}`}
                        className="text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        Comparar
                      </Link>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card className="mb-6">
        <CardHeader className="flex items-center justify-between">
          <h2 className="font-semibold text-slate-900">Cotizaciones recibidas</h2>
          {!rfqCerrada && !porEnviar && !mostrarForm && (
            <Button size="sm" variant="secondary" icon={<Plus className="size-4" />} onClick={() => setFormAbierto(true)}>
              Registrar cotización
            </Button>
          )}
        </CardHeader>
        <CardBody className="flex flex-col gap-4">
          {cotizaciones.length === 0 ? (
            <p className="text-sm text-slate-500">
              {porEnviar ? "Confirma el envío para registrar la respuesta." : "Todavía no se registró ninguna cotización."}
            </p>
          ) : (
            cotizaciones.map((c) => (
              <div key={c.quotationId} className="rounded-lg border border-slate-200 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <CotizacionEstadoBadge estado={c.status} />
                    {c.source === "ENLACE" && (
                      <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">Por enlace</span>
                    )}
                    <span className="text-sm font-medium text-slate-800">
                      {c.total} {c.currencyCode}
                    </span>
                    {c.supplierQuoteNumber && (
                      <span className="text-xs text-slate-400">N° {c.supplierQuoteNumber}</span>
                    )}
                    {c.documentoUrl && (
                      <a
                        href={c.documentoUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        <FileText className="size-3.5" />
                        Ver documento
                      </a>
                    )}
                  </div>
                  {c.status !== "SELECCIONADA" && c.status !== "DESCARTADA" && (
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={procesandoId === c.quotationId}
                      onClick={() => onDescartar(c.quotationId)}
                    >
                      Descartar
                    </Button>
                  )}
                </div>
                <ul className="mt-3 flex flex-col gap-1 text-xs text-slate-500">
                  {c.items.map((ci) => (
                    <li key={ci.quotationItemId}>
                      {ci.codigoVariante} — {ci.qty} × {ci.unitCost} = {ci.lineTotal}
                      {ci.offeredBrand ? ` · ${ci.offeredBrand}` : ""}
                      {ci.offeredModel ? ` ${ci.offeredModel}` : ""}
                    </li>
                  ))}
                </ul>
              </div>
            ))
          )}
          {cotizaciones.length > 0 && consolidados.length === 1 && (
            <Link
              to={`/compras/consolidados/${consolidados[0].consolidationId}`}
              className="self-start text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              Comparar y adjudicar
            </Link>
          )}
        </CardBody>
      </Card>

      {mostrarForm && (
        <Card>
          <CardHeader className="flex items-center justify-between">
            <h2 className="font-semibold text-slate-900">Registrar cotización</h2>
            {cotizaciones.length > 0 && (
              <button
                type="button"
                onClick={() => setFormAbierto(false)}
                title="Cerrar"
                className="text-slate-400 hover:text-slate-700"
              >
                <X className="size-4" />
              </button>
            )}
          </CardHeader>
          <CardBody>
            <form onSubmit={onRegistrarCotizacion} className="flex flex-col gap-4">
              <Field
                label="Adjuntar cotización (PDF, JPG o PNG, opcional)"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-600 hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700">
                    <Upload className="size-4" />
                    {archivo ? archivo.name : "Seleccionar archivo"}
                    <input
                      type="file"
                      accept="application/pdf,image/jpeg,image/png"
                      className="hidden"
                      onChange={(e) => {
                        setArchivo(e.target.files?.[0] ?? null);
                        setOcr(null);
                      }}
                    />
                  </label>
                  {archivo && (
                    <>
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        loading={extrayendo}
                        onClick={onExtraerOcr}
                        icon={<ScanText className="size-3.5" />}
                      >
                        {extrayendo ? "Extrayendo…" : "Extraer datos (OCR)"}
                      </Button>
                      <button
                        type="button"
                        onClick={() => {
                          setArchivo(null);
                          setOcr(null);
                        }}
                        title="Quitar archivo"
                        className="shrink-0 text-slate-400 hover:text-rose-600"
                      >
                        <X className="size-4" />
                      </button>
                    </>
                  )}
                </div>
              </Field>

              {ocr && (
                <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">
                  <p className="mb-3 font-medium">Datos detectados — revisá antes de guardar</p>
                  <dl className="grid grid-cols-2 gap-x-4 gap-y-2.5 sm:grid-cols-3">
                    <div>
                      <dt className="text-xs text-blue-700">Proveedor</dt>
                      <dd className="truncate">{ocr.proveedorDetectado ?? "—"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-blue-700">RUC</dt>
                      <dd className="font-mono">{ocr.rucDetectado ?? "—"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-blue-700">Fecha</dt>
                      <dd>{ocr.fechaDetectada ?? "—"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-blue-700">Total</dt>
                      <dd>
                        {ocr.totalDetectado != null ? `${ocr.totalDetectado} ${ocr.monedaDetectada ?? ""}` : "—"}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs text-blue-700">Plazo de entrega</dt>
                      <dd>{ocr.plazoDetectadoDias != null ? `${ocr.plazoDetectadoDias} días` : "—"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-blue-700">Costos sugeridos por ítem</dt>
                      <dd>
                        {ocr.itemsSugeridos.length} de {items.length}
                      </dd>
                    </div>
                  </dl>
                  <details className="mt-3 text-xs">
                    <summary className="cursor-pointer text-blue-700">
                      Texto extraído
                    </summary>
                    <pre className="mt-1 max-h-40 overflow-y-auto whitespace-pre-wrap rounded bg-white/60 p-2 text-slate-500">
                      {ocr.textoDetectado || "(sin texto)"}
                    </pre>
                  </details>
                </div>
              )}

              <div className="overflow-hidden rounded-lg border border-slate-200">
                <table className="tabla">
                  <thead>
                    <tr className="border-b border-slate-200 cabecera-tabla">
                      <th className="px-4 py-2.5">Producto</th>
                      <th className="px-4 py-2.5">Costo unitario</th>
                      {conMarca && <th className="px-4 py-2.5">Marca</th>}
                      {conMarca && <th className="px-4 py-2.5">Modelo</th>}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {items.map((it) => (
                      <tr key={it.rfqItemId}>
                        <td className="col-flex px-4 py-2.5">
                          <p className="text-sm text-slate-700">{it.nombreProducto}</p>
                          <p className="font-mono text-xs text-slate-500">
                            {it.codigoVariante} · {it.qty} {it.unidadMedida}
                          </p>
                        </td>
                        <td className="px-4 py-2.5">
                          <div className="flex items-center gap-1.5">
                            <CampoNumero
                              decimales={4} maxEnteros={9}
                              value={filas[it.rfqItemId]?.unitCost ?? ""}
                              onChange={(v) => {
                                setFilas((prev) => ({
                                  ...prev,
                                  [it.rfqItemId]: { ...prev[it.rfqItemId], unitCost: v },
                                }));
                                setItemsSugeridosIds((prev) => {
                                  if (!prev.has(it.rfqItemId)) return prev;
                                  const siguiente = new Set(prev);
                                  siguiente.delete(it.rfqItemId);
                                  return siguiente;
                                });
                              }}
                              className={`${inputClass} w-28 py-1.5`}
                            />
                            {itemsSugeridosIds.has(it.rfqItemId) && (
                              <span title="Sugerido por OCR — revisá antes de guardar">
                                <ScanText className="size-3.5 shrink-0 text-brand-500" />
                              </span>
                            )}
                          </div>
                        </td>
                        {conMarca && <td className="px-4 py-2.5">
                          <input
                            value={filas[it.rfqItemId]?.offeredBrand ?? ""}
                            onChange={(e) =>
                              setFilas((prev) => ({
                                ...prev,
                                [it.rfqItemId]: { ...prev[it.rfqItemId], offeredBrand: e.target.value },
                              }))
                            }
                            className={`${inputClass} w-32 py-1.5`}
                          />
                        </td>}
                        {conMarca && <td className="px-4 py-2.5">
                          <input
                            value={filas[it.rfqItemId]?.offeredModel ?? ""}
                            onChange={(e) =>
                              setFilas((prev) => ({
                                ...prev,
                                [it.rfqItemId]: { ...prev[it.rfqItemId], offeredModel: e.target.value },
                              }))
                            }
                            className={`${inputClass} w-32 py-1.5`}
                          />
                        </td>}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {!conMarca && (
                <button
                  type="button"
                  onClick={() => setConMarca(true)}
                  className="self-start text-xs font-medium text-brand-600 hover:text-brand-700"
                >
                  + Agregar marca y modelo ofrecidos
                </button>
              )}

              <div className="grid gap-4 sm:grid-cols-4">
                <Field label="Moneda" required>
                  <select
                    required
                    value={currencyCode}
                    onChange={(e) => setCurrencyCode(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    {monedas.map((m) => (
                      <option key={m.currencyCode} value={m.currencyCode}>
                        {m.currencyCode} — {m.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Condición de pago" required>
                  <select
                    required
                    value={paymentCondition}
                    onChange={(e) => setPaymentCondition(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="CONTADO">Contado</option>
                    <option value="ANTICIPO">Anticipo</option>
                    <option value="CREDITO_30">Crédito 30 días</option>
                  </select>
                </Field>
                <Field label="Modalidad de entrega" required>
                  <select
                    required
                    value={deliveryMode}
                    onChange={(e) => setDeliveryMode(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="AGENCIA">Agencia</option>
                    <option value="ALMACEN_PROVEEDOR">Almacén del proveedor</option>
                    <option value="ALMACEN_CLIENTE">Almacén del cliente</option>
                  </select>
                </Field>
                <Field label="Días de entrega">
                  <CampoNumero
                    decimales={0} maxEnteros={3}
                    value={leadDays}
                    onChange={(v) => setLeadDays(v)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              <details className="group">
                <summary className="flex cursor-pointer list-none items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-800">
                  <ChevronDown className="size-3.5 transition-transform group-open:rotate-180" />
                  Más datos de la cotización
                </summary>
                <div className="mt-3 grid gap-4 sm:grid-cols-3">
                  <Field label="N° de cotización del proveedor">
                    <input
                      value={supplierQuoteNumber}
                      onChange={(e) => setSupplierQuoteNumber(e.target.value)}
                      className={`${inputClass} w-full`}
                    />
                  </Field>
                  <Field label="Fecha de emisión">
                    <input
                      type="date"
                      value={issueDate}
                      onChange={(e) => setIssueDate(e.target.value)}
                      className={`${inputClass} w-full`}
                    />
                  </Field>
                </div>
              </details>

              {errorForm && <Alert tone="danger">{errorForm}</Alert>}

              <Button type="submit" loading={guardando} className="self-start">
                {guardando ? "Guardando…" : "Registrar cotización"}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}
    </div>
  );
}
