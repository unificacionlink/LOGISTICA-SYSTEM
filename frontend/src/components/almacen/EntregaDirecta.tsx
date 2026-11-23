import { useId, useState } from "react";
import { AlertTriangle, CheckCircle2, Truck } from "lucide-react";
import { entregasDirectasDelRequerimiento, registrarEntregaDirecta, type EntregaDirectaInput } from "../../api/almacen";
import { invalidar } from "../../api/cache";
import type { AccionProveedor, EntregaDirecta, LineaEntrega, TipoIncidencia } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import { RecepcionBadge } from "../AlmacenBadges";
import { ETIQUETA_ACCION_PROVEEDOR, ETIQUETA_TIPO_INCIDENCIA, fmtCantidad, fmtFecha, hoyIso } from "../formato";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import CampoNumero from "../ui/CampoNumero";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";

const ENLACE = /^https?:\/\/\S+$/i;

interface FilaForm {
  qty: string;
  conProblema: boolean;
  tipo: TipoIncidencia;
  qtyAfectada: string;
  descripcion: string;
  accion: AccionProveedor | "";
}

function clave(l: LineaEntrega) {
  return `${l.orderItemId}|${l.needId}|${l.requestItemId}`;
}

function filaInicial(l: LineaEntrega): FilaForm {
  return { qty: String(l.pendiente), conProblema: false, tipo: "FALTANTE", qtyAfectada: "", descripcion: "", accion: "REPONER" };
}

/**
 * Recepción de una entrega directa del proveedor en un CECO (ALM-04, DEC-L084):
 * una por CECO, guía obligatoria y constancia firmada (archivo o enlace). Lo que
 * llega mal se reporta como incidencia y queda pendiente para que el proveedor lo reponga.
 */
export function FormEntregaDirecta({
  entrega,
  onGuardada,
}: {
  entrega: EntregaDirecta;
  onGuardada: (e: EntregaDirecta) => void;
}) {
  const destinos = entrega.destinos
    .map((d) => ({ ...d, lineas: d.lineas.filter((l) => l.puedeRegistrar && l.pendiente > 0) }))
    .filter((d) => d.lineas.length > 0);
  const [cecoId, setCecoId] = useState(destinos[0]?.cecoId ?? "");
  const destino = destinos.find((d) => d.cecoId === cecoId) ?? destinos[0];
  const [filas, setFilas] = useState<Record<string, FilaForm>>({});
  const [guia, setGuia] = useState({ series: "", number: "", issueDate: hoyIso() });
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enlace, setEnlace] = useState("");
  const [observacion, setObservacion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const base = useId();

  if (!destino) return null;
  if (entrega.bloqueo) {
    return (
      <Alert tone="info" className="mb-4">
        {entrega.bloqueo}
      </Alert>
    );
  }

  const fila = (l: LineaEntrega) => filas[clave(l)] ?? filaInicial(l);
  function cambiar(l: LineaEntrega, cambio: Partial<FilaForm>) {
    setFilas((prev) => ({ ...prev, [clave(l)]: { ...(prev[clave(l)] ?? filaInicial(l)), ...cambio } }));
    setError(null);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!destino) return;
    const lineas: EntregaDirectaInput["lineas"] = [];
    for (const l of destino.lineas) {
      const f = fila(l);
      const qty = Number(f.qty || 0);
      if (qty > l.pendiente) {
        setError(`"${l.descripcion}" supera lo pendiente (${fmtCantidad(l.pendiente)}).`);
        return;
      }
      if (f.conProblema && !f.descripcion.trim()) {
        setError(`Describe el problema de "${l.descripcion}".`);
        return;
      }
      if (qty === 0 && !f.conProblema) continue;
      lineas.push({
        orderItemId: l.orderItemId,
        needId: l.needId,
        requestItemId: l.requestItemId,
        qty,
        incidencia: f.conProblema
          ? {
              issueType: f.tipo,
              qtyAffected: f.qtyAfectada ? Number(f.qtyAfectada) : null,
              description: f.descripcion.trim(),
              supplierAction: f.accion || null,
            }
          : null,
      });
    }
    if (lineas.length === 0) {
      setError("Indica al menos una cantidad recibida o un problema.");
      return;
    }
    if (!guia.series.trim() || !guia.number.trim() || !guia.issueDate) {
      setError("Registra la guía de remisión del proveedor: serie, número y fecha.");
      return;
    }
    if (!archivo && !ENLACE.test(enlace.trim())) {
      setError("Adjunta la constancia firmada o registra su enlace.");
      return;
    }
    setError(null);
    setOcupado(true);
    try {
      const res = await registrarEntregaDirecta(
        entrega.orderId,
        {
          cecoId: destino.cecoId,
          guia: { series: guia.series.trim(), number: guia.number.trim(), issueDate: guia.issueDate },
          enlaceConformidad: archivo ? undefined : enlace.trim(),
          observation: observacion.trim() || undefined,
          lineas,
        },
        archivo,
      );
      if (res.ok && res.result) {
        setFilas({});
        setGuia({ series: "", number: "", issueDate: hoyIso() });
        setArchivo(null);
        setEnlace("");
        setObservacion("");
        // El estado de los requerimientos pudo cambiar (atención parcial o completa).
        invalidar("req", "bandeja", "tablero", "tracking", "avance", "mi-resumen", "almacen-recibir");
        onGuardada(res.result);
      } else {
        setError(res.message);
      }
    } finally {
      setOcupado(false);
    }
  }

  return (
    <Card className="mb-4 overflow-hidden">
      <form onSubmit={onSubmit} noValidate>
        <CardBody className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="rotulo">Recibir en proyecto</div>
            {destinos.length > 1 && (
              <div role="radiogroup" aria-label="CECO que recibe" className="inline-flex rounded-md border border-slate-200 bg-white p-0.5">
                {destinos.map((d) => (
                  <button
                    key={d.cecoId}
                    type="button"
                    role="radio"
                    aria-checked={d.cecoId === destino.cecoId}
                    onClick={() => {
                      setCecoId(d.cecoId);
                      setError(null);
                    }}
                    className={`rounded px-3 py-1 text-[0.8125rem] transition-colors ${
                      d.cecoId === destino.cecoId ? "bg-brand-700 font-medium text-white" : "text-slate-600 hover:text-slate-900"
                    }`}
                    title={d.cecoNombre}
                  >
                    {d.cecoCode}
                  </button>
                ))}
              </div>
            )}
          </div>
          <p className="-mt-2 text-sm text-slate-600">
            {destino.cecoCode} · {destino.cecoNombre}
          </p>
        </CardBody>

        <div className="divide-y divide-slate-100 border-y border-slate-200">
          {destino.lineas.map((l) => {
            const f = fila(l);
            const id = `${base}-${clave(l)}`;
            return (
              <div key={clave(l)} className="px-5 py-3">
                <div className="flex flex-wrap items-center gap-3">
                  <label htmlFor={id} className="min-w-0 flex-1 text-sm" title={`${l.sku} · ${l.descripcion}`}>
                    <span className="block truncate text-slate-800">
                      <span className="font-mono text-xs text-slate-500">{l.sku}</span> {l.descripcion}
                    </span>
                    <span className="text-xs text-slate-500">
                      {l.requestCode ? `${l.requestCode} · ` : ""}pendiente {fmtCantidad(l.pendiente)}
                      {l.unidad ? ` ${l.unidad}` : ""}
                    </span>
                  </label>
                  <CampoNumero
                    id={id}
                    decimales={0}
                    max={l.pendiente}
                    value={f.qty}
                    onChange={(v) => cambiar(l, { qty: v })}
                    aria-label={`Cantidad recibida de ${l.descripcion}`}
                    className={`${inputClass} w-24 text-right`}
                  />
                  <button
                    type="button"
                    onClick={() => cambiar(l, { conProblema: !f.conProblema })}
                    className={`text-xs font-medium ${f.conProblema ? "text-rose-700" : "text-slate-500 hover:text-slate-800"}`}
                  >
                    {f.conProblema ? "Quitar problema" : "Reportar problema"}
                  </button>
                </div>
                {f.conProblema && (
                  <div className="mt-3 grid gap-3 sm:grid-cols-4">
                    <Field label="Problema">
                      <select
                        className={`${inputClass} w-full`}
                        value={f.tipo}
                        onChange={(e) => cambiar(l, { tipo: e.target.value as TipoIncidencia })}
                      >
                        {Object.entries(ETIQUETA_TIPO_INCIDENCIA).map(([v, t]) => (
                          <option key={v} value={v}>
                            {t}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Cantidad afectada">
                      <CampoNumero
                        aria-label="Cantidad afectada"
                        decimales={0}
                        value={f.qtyAfectada}
                        onChange={(v) => cambiar(l, { qtyAfectada: v })}
                        className={`${inputClass} w-full text-right`}
                      />
                    </Field>
                    <Field label="Qué hace el proveedor">
                      <select
                        className={`${inputClass} w-full`}
                        value={f.accion}
                        onChange={(e) => cambiar(l, { accion: e.target.value as AccionProveedor | "" })}
                      >
                        <option value="">Por definir</option>
                        {Object.entries(ETIQUETA_ACCION_PROVEEDOR).map(([v, t]) => (
                          <option key={v} value={v}>
                            {t}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Descripción">
                      <input
                        aria-label="Descripción del problema"
                        className={`${inputClass} w-full`}
                        maxLength={500}
                        value={f.descripcion}
                        onChange={(e) => cambiar(l, { descripcion: e.target.value })}
                      />
                    </Field>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <CardBody className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Guía · serie" required>
              <input
                aria-label="Serie de la guía"
                className={`${inputClass} w-full uppercase`}
                maxLength={10}
                value={guia.series}
                onChange={(e) => setGuia({ ...guia, series: e.target.value })}
              />
            </Field>
            <Field label="Guía · número" required>
              <input
                className={`${inputClass} w-full`}
                maxLength={20}
                aria-label="Número de la guía"
                inputMode="numeric"
                value={guia.number}
                onChange={(e) => setGuia({ ...guia, number: e.target.value })}
              />
            </Field>
            <Field label="Guía · fecha" required>
              <input
                type="date"
                aria-label="Fecha de la guía"
                className={`${inputClass} w-full`}
                value={guia.issueDate}
                max={hoyIso()}
                onChange={(e) => setGuia({ ...guia, issueDate: e.target.value })}
              />
            </Field>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Constancia firmada">
              <input
                type="file"
                aria-label="Constancia firmada"
                accept="application/pdf,image/png,image/jpeg"
                onChange={(e) => {
                  setArchivo(e.target.files?.[0] ?? null);
                  setError(null);
                }}
                className="text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:text-slate-700 hover:file:bg-slate-200"
              />
            </Field>
            <Field label="O enlace de la constancia">
              <input
                type="url"
                aria-label="Enlace de la constancia firmada"
                inputMode="url"
                maxLength={500}
                autoComplete="off"
                spellCheck={false}
                placeholder="https://"
                disabled={!!archivo}
                value={enlace}
                onChange={(e) => {
                  setEnlace(e.target.value);
                  setError(null);
                }}
                className={`${inputClass} w-full`}
              />
            </Field>
          </div>
          <Field label="Observación">
            <textarea
              aria-label="Observación"
              rows={2}
              maxLength={1000}
              value={observacion}
              onChange={(e) => setObservacion(e.target.value)}
              className={`${inputClass} w-full whitespace-normal`}
            />
          </Field>
          {error && (
            <p role="alert" className="text-sm text-rose-700">
              {error}
            </p>
          )}
          <div>
            <Button type="submit" loading={ocupado} icon={<CheckCircle2 className="size-4" />}>
              Registrar recepción en {destino.cecoCode}
            </Button>
          </div>
        </CardBody>
      </form>
    </Card>
  );
}

/** Recepciones en proyecto e incidencias de una entrega directa (solo lectura). */
function HistorialEntrega({ entrega }: { entrega: EntregaDirecta }) {
  if (entrega.recepciones.length === 0 && entrega.incidencias.length === 0) return null;
  return (
    <div className="divide-y divide-slate-100 border-t border-slate-200">
      {entrega.recepciones.map((r) => (
        <div key={r.receiptId} className="px-5 py-3 text-sm">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="font-medium text-slate-800">Recepción #{r.receiptNumber}</span>
            {/* Parcial/completa es de la OC entera (otros CECO): aquí solo importa si hubo incidencia. */}
            {r.status === "OBSERVADA" && <RecepcionBadge estado={r.status} />}
            <span className="text-xs text-slate-500">
              {r.cecoDestino} · {fmtFecha(r.receiptDate)} · {r.receivedByName}
              {r.guia ? ` · Guía ${r.guia}` : ""}
            </span>
            {r.constanciaUrl && (
              <a href={r.constanciaUrl} target="_blank" rel="noopener noreferrer" className="text-xs text-brand-700 hover:underline">
                Constancia
              </a>
            )}
          </div>
          <ul className="mt-1 flex flex-col gap-0.5 text-xs text-slate-600">
            {r.items.map((i, k) => (
              <li key={`${i.orderItemId}-${k}`}>
                {fmtCantidad(i.qtyReceived)} × {i.description}
                {i.requestCode ? ` · ${i.requestCode}` : ""}
              </li>
            ))}
          </ul>
        </div>
      ))}
      {entrega.incidencias.map((inc) => (
        <div key={inc.receiptIssueId} className="flex items-start gap-2 px-5 py-3 text-sm">
          <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
          <div className="min-w-0">
            <p className="text-slate-800">
              {ETIQUETA_TIPO_INCIDENCIA[inc.issueType]}
              {inc.qtyAffected ? ` · ${fmtCantidad(inc.qtyAffected)} u.` : ""} — {inc.itemDescription}
            </p>
            <p className="text-xs text-slate-500">
              {inc.description}
              {inc.supplierAction ? ` · ${ETIQUETA_ACCION_PROVEEDOR[inc.supplierAction]}` : ""} · Recepción #{inc.receiptNumber}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Lo que el proveedor entrega directo en proyecto para este requerimiento: avance,
 * formulario (dueño, Logística, Gerencia) e historial. No se muestra si no hay.
 */
export function EntregasDirectasRequerimiento({ requestId }: { requestId: string }) {
  const datos = useDatos(`entrega-directa:${requestId}`, () => entregasDirectasDelRequerimiento(requestId));
  const entregas = datos.datos ?? [];
  if (entregas.length === 0) return null;

  return (
    <div id="entrega-directa" className="scroll-mt-20">
      {entregas.map((e) => {
        const lineas = e.destinos.flatMap((d) => d.lineas);
        return (
          <div key={e.orderId}>
            <Card className="mb-4 overflow-hidden">
              <CardBody className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Truck className="size-4.5 text-slate-500" />
                <h2 className="font-semibold text-slate-900">Entrega directa en proyecto</h2>
                <span className="font-mono text-xs text-slate-600">{e.orderCode}</span>
                <span className="text-sm text-slate-500">{e.proveedor}</span>
              </CardBody>
              <ul className="divide-y divide-slate-100 border-t border-slate-200">
                {lineas.map((l) => (
                  <li key={clave(l)} className="flex items-center gap-3 px-5 py-2.5 text-sm">
                    <span className="min-w-0 flex-1 truncate text-slate-700" title={`${l.sku} · ${l.descripcion}`}>
                      <span className="font-mono text-xs text-slate-500">{l.sku}</span> {l.descripcion}
                    </span>
                    <span className="tabular-nums text-slate-800">
                      {fmtCantidad(l.recibido)} / {fmtCantidad(l.asignado)}
                    </span>
                  </li>
                ))}
              </ul>
              <HistorialEntrega entrega={e} />
            </Card>
            <FormEntregaDirecta key={`${e.orderId}-${e.recepciones.length}`} entrega={e} onGuardada={() => void datos.recargar()} />
          </div>
        );
      })}
    </div>
  );
}
