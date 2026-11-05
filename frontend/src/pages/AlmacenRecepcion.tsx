import { useEffect, useState } from "react";
import { etiquetaRequerimientos } from "../components/compras/etiquetas";
import { useParams } from "react-router-dom";
import { AlertTriangle, PackageOpen } from "lucide-react";
import {
  actualizarIncidencia,
  obtenerRecepcionOrden,
  registrarRecepcion,
  type RecepcionItemInput,
} from "../api/almacen";
import type {
  AccionProveedor,
  CondicionRecepcion,
  EstadoIncidencia,
  Incidencia,
  RecepcionOrden,
  TipoIncidencia,
} from "../api/types";
import {
  ETIQUETA_ACCION_PROVEEDOR,
  ETIQUETA_TIPO_INCIDENCIA,
  RecepcionBadge,
  fmtCantidad,
  fmtFecha,
  hoyIso,
} from "../components/AlmacenBadges";
import OrdenEstadoBadge from "../components/OrdenEstadoBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

interface FilaForm {
  qty: string;
  // Condicion, observacion e incidencia solo se muestran si hay un problema (ley de Hick).
  conProblema: boolean;
  condicion: CondicionRecepcion;
  observacion: string;
  conIncidencia: boolean;
  tipo: TipoIncidencia;
  qtyAfectada: string;
  descripcion: string;
  accion: AccionProveedor | "";
}

const ESTADOS_RECIBIBLES = ["EMITIDA", "EN_ATENCION", "RECEPCION_PARCIAL"];
const labelClass = "rotulo";

function filaInicial(pendiente: number): FilaForm {
  return {
    qty: String(pendiente),
    conProblema: false,
    condicion: "CONFORME",
    observacion: "",
    conIncidencia: false,
    tipo: "FALTANTE",
    qtyAfectada: "",
    descripcion: "",
    accion: "",
  };
}

export default function AlmacenRecepcion() {
  const { id } = useParams<{ id: string }>();
  const [datos, setDatos] = useState<RecepcionOrden | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const [filas, setFilas] = useState<Record<string, FilaForm>>({});
  const [observacion, setObservacion] = useState("");
  const [conObservacion, setConObservacion] = useState(false);
  const [guia, setGuia] = useState({ series: "", number: "", issueDate: hoyIso() });

  function aplicar(d: RecepcionOrden) {
    setDatos(d);
    const nuevas: Record<string, FilaForm> = {};
    for (const it of d.items) {
      if (it.qtyPending > 0) nuevas[it.orderItemId] = filaInicial(Number(it.qtyPending));
    }
    setFilas(nuevas);
  }

  useEffect(() => {
    if (!id) return;
    obtenerRecepcionOrden(id)
      .then((res) => {
        if (res.ok && res.result) aplicar(res.result);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, [id]);

  function actualizarFila(orderItemId: string, cambio: Partial<FilaForm>) {
    setFilas((prev) => ({ ...prev, [orderItemId]: { ...prev[orderItemId], ...cambio } }));
  }

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !datos) return;
    setError(null);
    setExito(null);

    const items: RecepcionItemInput[] = [];
    for (const it of datos.items) {
      const f = filas[it.orderItemId];
      if (!f) continue;
      const qty = Number(f.qty || 0);
      if (Number.isNaN(qty) || qty < 0) {
        setError(`Cantidad inválida para "${it.description}".`);
        return;
      }
      if (qty > it.qtyPending) {
        setError(`"${it.description}": no puedes recibir más de lo pendiente (${fmtCantidad(it.qtyPending)}).`);
        return;
      }
      if (f.conIncidencia && !f.descripcion.trim()) {
        setError(`Describe la incidencia de "${it.description}".`);
        return;
      }
      if (qty === 0 && !f.conIncidencia) continue;
      items.push({
        orderItemId: it.orderItemId,
        qtyReceived: qty,
        conditionStatus: f.condicion,
        observation: f.observacion || undefined,
        incidencia: f.conIncidencia
          ? {
              issueType: f.tipo,
              qtyAffected: f.qtyAfectada ? Number(f.qtyAfectada) : undefined,
              description: f.descripcion,
              supplierAction: f.accion || undefined,
            }
          : undefined,
      });
    }
    if (items.length === 0) {
      setError("Indica al menos una cantidad recibida o una incidencia.");
      return;
    }
    const conGuia = guia.series.trim() || guia.number.trim();

    setGuardando(true);
    try {
      const res = await registrarRecepcion(id, {
        items,
        observation: observacion || undefined,
        guia: conGuia ? guia : undefined,
      });
      if (res.ok && res.result) {
        aplicar(res.result);
        setObservacion("");
        setConObservacion(false);
        setGuia({ series: "", number: "", issueDate: hoyIso() });
        setExito(res.message || "Recepción registrada.");
      } else {
        setError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  async function onCambiarIncidencia(inc: Incidencia, status: EstadoIncidencia, accion: AccionProveedor | "") {
    const res = await actualizarIncidencia(inc.receiptIssueId, status, accion);
    if (res.ok && res.result && datos) {
      setDatos({
        ...datos,
        incidencias: datos.incidencias.map((i) => (i.receiptIssueId === inc.receiptIssueId ? res.result! : i)),
      });
    } else {
      setError(res.message);
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
  if (error && !datos) return <Alert tone="danger">{error}</Alert>;
  if (!datos) return null;

  const { orden, items, recepciones, incidencias } = datos;
  const recibible = ESTADOS_RECIBIBLES.includes(orden.status) && items.some((i) => i.qtyPending > 0);

  return (
    <div>
      <PageHeader
        title={
          <span>
            Recepción · <span className="font-mono">{orden.orderCode}</span>
          </span>
        }
        subtitle={`${orden.proveedorNombre} (RUC ${datos.proveedorRuc}) · ${etiquetaRequerimientos(orden.requestCode)}`}
        backTo="/almacen?tab=recibir"
        backLabel="Almacén"
        actions={<OrdenEstadoBadge estado={orden.status} />}
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      <form onSubmit={onGuardar}>
        <Card className="mb-4 overflow-hidden">
          <CardBody className="pb-3">
            <div className={labelClass}>Items de la orden</div>
          </CardBody>
          <div className="divide-y divide-slate-100 border-t border-slate-200">
            {items.map((it) => {
              const f = filas[it.orderItemId];
              return (
                <div key={it.orderItemId} className="px-5 py-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm text-slate-800">{it.description}</p>
                      <p className="mt-0.5 font-mono text-xs text-slate-500">{it.codigoVariante ?? it.skuBase ?? ""}</p>
                    </div>
                    <div className="flex gap-4 text-xs text-slate-500">
                      <span>
                        Pedido <b className="text-slate-700">{fmtCantidad(it.qtyOrdered)}</b>
                      </span>
                      <span>
                        Recibido <b className="text-slate-700">{fmtCantidad(it.qtyReceived)}</b>
                      </span>
                      <span>
                        Pendiente <b className={it.qtyPending > 0 ? "text-amber-600" : "text-emerald-600"}>{fmtCantidad(it.qtyPending)}</b>
                      </span>
                    </div>
                  </div>

                  {recibible && f && (
                    <div className="mt-3 flex flex-wrap items-end gap-3">
                      <Field label="Cantidad aceptada">
                        <input
                          type="number"
                          min="0"
                          step="any"
                          max={it.qtyPending}
                          value={f.qty}
                          onChange={(e) => actualizarFila(it.orderItemId, { qty: e.target.value })}
                          className={`${inputClass} w-32`}
                        />
                      </Field>
                      {f.conProblema ? (
                        <button
                          type="button"
                          onClick={() => actualizarFila(it.orderItemId, { ...filaInicial(0), qty: f.qty })}
                          className="pb-2 text-xs font-medium text-slate-500 hover:text-slate-800"
                        >
                          Quitar problema
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => actualizarFila(it.orderItemId, { conProblema: true })}
                          className="inline-flex items-center gap-1.5 pb-2 text-xs font-medium text-amber-700 hover:text-amber-800"
                        >
                          <AlertTriangle className="size-3.5" />
                          Reportar problema
                        </button>
                      )}
                    </div>
                  )}

                  {recibible && f?.conProblema && (
                    <div className="mt-3 grid gap-3 rounded-lg border border-amber-200 bg-amber-50/40 p-3 sm:grid-cols-[160px_1fr]">
                      <Field label="Condición">
                        <select
                          value={f.condicion}
                          onChange={(e) => actualizarFila(it.orderItemId, { condicion: e.target.value as CondicionRecepcion })}
                          className={`${inputClass} w-full`}
                        >
                          <option value="CONFORME">Conforme</option>
                          <option value="OBSERVADO">Observado</option>
                        </select>
                      </Field>
                      <Field label="Observación">
                        <input
                          value={f.observacion}
                          onChange={(e) => actualizarFila(it.orderItemId, { observacion: e.target.value })}
                          className={`${inputClass} w-full`}
                        />
                      </Field>
                      <label className="flex items-center gap-2 text-sm text-slate-600 sm:col-span-2">
                        <input
                          type="checkbox"
                          checked={f.conIncidencia}
                          onChange={(e) => actualizarFila(it.orderItemId, { conIncidencia: e.target.checked })}
                        />
                        Registrar incidencia (faltante, daño, producto errado)
                      </label>
                      {f.conIncidencia && (
                        <div className="grid gap-3 rounded-lg border border-amber-200 bg-amber-50/50 p-3 sm:col-span-2 sm:grid-cols-[160px_120px_1fr_170px]">
                          <Field label="Tipo">
                            <select
                              value={f.tipo}
                              onChange={(e) => actualizarFila(it.orderItemId, { tipo: e.target.value as TipoIncidencia })}
                              className={`${inputClass} w-full`}
                            >
                              {Object.entries(ETIQUETA_TIPO_INCIDENCIA).map(([v, t]) => (
                                <option key={v} value={v}>{t}</option>
                              ))}
                            </select>
                          </Field>
                          <Field label="Cant. afectada">
                            <input
                              type="number"
                              min="0"
                              step="any"
                              value={f.qtyAfectada}
                              onChange={(e) => actualizarFila(it.orderItemId, { qtyAfectada: e.target.value })}
                              className={`${inputClass} w-full`}
                            />
                          </Field>
                          <Field label="Descripción" required>
                            <input
                              value={f.descripcion}
                              onChange={(e) => actualizarFila(it.orderItemId, { descripcion: e.target.value })}
                              placeholder="Qué pasó"
                              className={`${inputClass} w-full`}
                            />
                          </Field>
                          <Field label="Acción del proveedor">
                            <select
                              value={f.accion}
                              onChange={(e) => actualizarFila(it.orderItemId, { accion: e.target.value as AccionProveedor | "" })}
                              className={`${inputClass} w-full`}
                            >
                              <option value="">Por definir</option>
                              {Object.entries(ETIQUETA_ACCION_PROVEEDOR).map(([v, t]) => (
                                <option key={v} value={v}>{t}</option>
                              ))}
                            </select>
                          </Field>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </Card>

        {recibible && (
          <Card className="mb-4">
            <CardBody className="grid gap-4 sm:grid-cols-[1fr_1fr_1fr]">
              <Field label="Guía de remisión (serie)">
                <input
                  value={guia.series}
                  onChange={(e) => setGuia({ ...guia, series: e.target.value })}
                  placeholder="T001"
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Número">
                <input
                  value={guia.number}
                  onChange={(e) => setGuia({ ...guia, number: e.target.value })}
                  placeholder="000123"
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Fecha de la guía">
                <input
                  type="date"
                  value={guia.issueDate}
                  onChange={(e) => setGuia({ ...guia, issueDate: e.target.value })}
                  className={`${inputClass} w-full`}
                />
              </Field>
              {conObservacion ? (
                <Field label="Observación general" className="sm:col-span-3">
                  <textarea
                    value={observacion}
                    onChange={(e) => setObservacion(e.target.value)}
                    rows={2}
                    autoFocus
                    className={`${inputClass} w-full`}
                  />
                </Field>
              ) : (
                <button
                  type="button"
                  onClick={() => setConObservacion(true)}
                  className="justify-self-start text-xs font-medium text-brand-600 hover:text-brand-700 sm:col-span-3"
                >
                  + Agregar observación general
                </button>
              )}
              <div className="sm:col-span-3">
                <Button type="submit" loading={guardando} icon={<PackageOpen className="size-4" />}>
                  {guardando ? "Registrando…" : "Registrar recepción"}
                </Button>
              </div>
            </CardBody>
          </Card>
        )}
      </form>

      {incidencias.length > 0 && (
        <Card className="mb-4 overflow-hidden">
          <CardBody className="pb-3">
            <div className={labelClass}>Incidencias ({incidencias.length})</div>
          </CardBody>
          <div className="divide-y divide-slate-100 border-t border-slate-200">
            {incidencias.map((inc) => (
              <div key={inc.receiptIssueId} className="flex flex-wrap items-center gap-3 px-5 py-3 text-sm">
                <div className="min-w-0 flex-1">
                  <p className="text-slate-800">
                    {ETIQUETA_TIPO_INCIDENCIA[inc.issueType]}
                    {inc.qtyAffected ? ` · ${fmtCantidad(inc.qtyAffected)} u.` : ""} — {inc.itemDescription}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {inc.description} · Recepción #{inc.receiptNumber} · {fmtFecha(inc.createdAt)}
                  </p>
                </div>
                <select
                  value={inc.supplierAction ?? ""}
                  onChange={(e) => onCambiarIncidencia(inc, inc.status, e.target.value as AccionProveedor | "")}
                  className={`${inputClass} py-1.5 text-xs`}
                  aria-label="Acción del proveedor"
                >
                  <option value="">Acción por definir</option>
                  {Object.entries(ETIQUETA_ACCION_PROVEEDOR).map(([v, t]) => (
                    <option key={v} value={v}>{t}</option>
                  ))}
                </select>
                <select
                  value={inc.status}
                  onChange={(e) => onCambiarIncidencia(inc, e.target.value as EstadoIncidencia, inc.supplierAction ?? "")}
                  className={`${inputClass} py-1.5 text-xs`}
                  aria-label="Estado de la incidencia"
                >
                  <option value="ABIERTA">Abierta</option>
                  <option value="EN_GESTION">En gestión</option>
                  <option value="CERRADA">Cerrada</option>
                </select>
              </div>
            ))}
          </div>
        </Card>
      )}

      {recepciones.length > 0 && (
        <Card className="overflow-hidden">
          <CardBody className="pb-3">
            <div className={labelClass}>Recepciones registradas ({recepciones.length})</div>
          </CardBody>
          <div className="divide-y divide-slate-100 border-t border-slate-200">
            {recepciones.map((r) => (
              <div key={r.receiptId} className="px-5 py-3 text-sm">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="font-medium text-slate-800">Recepción #{r.receiptNumber}</span>
                  <RecepcionBadge estado={r.status} />
                  <span className="text-xs text-slate-500">
                    {fmtFecha(r.receiptDate)} · {r.receivedByName}
                    {r.guia ? ` · Guía ${r.guia}` : ""}
                  </span>
                </div>
                {r.items.length > 0 && (
                  <ul className="mt-1.5 flex flex-col gap-0.5 text-xs text-slate-600">
                    {r.items.map((i) => (
                      <li key={i.orderItemId}>
                        {fmtCantidad(i.qtyReceived)} × {i.description}
                        {i.conditionStatus === "OBSERVADO" ? " (observado)" : ""}
                        {i.observation ? ` — ${i.observation}` : ""}
                      </li>
                    ))}
                  </ul>
                )}
                {r.observation && <p className="mt-1 text-xs text-slate-500">{r.observation}</p>}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
