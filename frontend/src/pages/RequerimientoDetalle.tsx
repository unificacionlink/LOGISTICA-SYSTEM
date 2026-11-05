import { useEffect, useState } from "react";
import { ConsolidadoEstadoBadge } from "../components/compras/etiquetas";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ShoppingCart } from "lucide-react";
import { decidirAprobacion } from "../api/aprobaciones";
import { obtenerDetalleCompra } from "../api/compras";
import { enviarRequerimiento, obtenerRequerimiento, programarEntrega } from "../api/requerimientos";
import type { ComprasDetalle, RequerimientoDetalle as RequerimientoDetalleType } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useRealtime } from "../realtime/RealtimeProvider";
import EstadoBadge from "../components/EstadoBadge";
import Badge from "../components/ui/Badge";
import RfqEstadoBadge from "../components/RfqEstadoBadge";
import TrackingRequerimiento from "../components/TrackingRequerimiento";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody, CardHeader } from "../components/ui/Card";
import { inputClass } from "../components/ui/Field";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { useConfirmar } from "../components/ui/Confirmacion";
import { Skeleton } from "../components/ui/Skeleton";
import BotonDescarga from "../components/BotonDescarga";
import { descargarPdfRequerimiento } from "../api/documentos";
import { soles, textoDesviacion } from "../components/presupuestos/formato";

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

/** Fecha sin hora (yyyy-mm-dd) sin pasar por zona horaria. */
function fmtFecha(iso: string | null): string {
  if (!iso) return "—";
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

function hoyIso(): string {
  const t = new Date();
  return new Date(t.getTime() - t.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

const ESTADOS_PROGRAMABLES = ["VERIFICACION_STOCK", "EN_ATENCION", "ATENCION_PARCIAL"];

export default function RequerimientoDetalle() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const { usuario, tieneAlgunRol } = useAuth();
  const { onMiRequerimientoDecidido } = useRealtime();
  const esGestor = tieneAlgunRol("LOGISTICA", "ADMIN");
  const [actualizadoEnVivo, setActualizadoEnVivo] = useState(false);

  const [detalle, setDetalle] = useState<RequerimientoDetalleType | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [msgEnvio, setMsgEnvio] = useState<string | null>(null);
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null);
  const confirmar = useConfirmar();
  const [decidiendo, setDecidiendo] = useState(false);
  const [programando, setProgramando] = useState(false);
  const [fechaProgramada, setFechaProgramada] = useState("");
  const [guardandoFecha, setGuardandoFecha] = useState(false);
  const [errorFecha, setErrorFecha] = useState<string | null>(null);
  const [errorDecision, setErrorDecision] = useState<string | null>(null);
  const [msgDecision, setMsgDecision] = useState<string | null>(null);

  const [comprasDetalle, setComprasDetalle] = useState<ComprasDetalle | null>(null);
  const [cargandoCompras, setCargandoCompras] = useState(false);

  function cargar() {
    if (!id) return;
    setCargando(true);
    obtenerRequerimiento(id)
      .then((res) => {
        if (res.ok) setDetalle(res.result);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, [id]);

  useEffect(() => {
    return onMiRequerimientoDecidido((actualizado) => {
      if (actualizado.id !== id) return;
      setDetalle((prev) => (prev ? { ...prev, requerimiento: actualizado, pendingApprovalId: null } : prev));
      setActualizadoEnVivo(true);
      // Sin esqueleto: trae el motivo de la observacion y el resto sin tapar la pantalla.
      obtenerRequerimiento(actualizado.id).then((res) => res.ok && res.result && setDetalle(res.result));
    });
  }, [id, onMiRequerimientoDecidido]);

  async function onEnviar() {
    if (!id) return;
    setEnviando(true);
    setErrorEnvio(null);
    try {
      let res = await enviarRequerimiento(id);
      // Control presupuestal (DEC-L038): fuera de presupuesto se envia solo con justificacion.
      if (!res.ok && res.code === "EXCEPCION_PRESUPUESTAL") {
        const r = await confirmar({
          titulo: "Excepción presupuestal",
          mensaje: res.message,
          confirmar: "Enviar con justificación",
          motivo: "Justificación",
        });
        if (!r) return;
        res = await enviarRequerimiento(id, r.motivo);
      }
      if (!res.ok) {
        setErrorEnvio(res.message);
        return;
      }
      setMsgEnvio(res.message);
      cargar();
    } finally {
      setEnviando(false);
    }
  }

  async function onDecidir(decision: "APROBADO" | "OBSERVADO" | "RECHAZADO") {
    if (!detalle?.pendingApprovalId) return;
    const code = detalle.requerimiento.code;
    // RF-047: toda decision se confirma; observar y rechazar exigen motivo (RF-006/064).
    const r = await confirmar(
      decision === "APROBADO"
        ? {
            titulo: `¿Aprobar ${code}?`,
            mensaje: "Se reservará el stock disponible y se pedirá cotización de lo que falte.",
            confirmar: "Aprobar",
          }
        : decision === "OBSERVADO"
          ? {
              titulo: `Observar ${code}`,
              mensaje: "Vuelve al solicitante para que lo corrija y lo reenvíe.",
              confirmar: "Observar",
              motivo: "Qué debe corregir",
            }
          : {
              titulo: `¿Rechazar ${code}?`,
              mensaje: "El requerimiento se cierra y no podrá reenviarse.",
              confirmar: "Rechazar",
              peligro: true,
              motivo: "Motivo del rechazo",
            },
    );
    if (!r) return;
    setDecidiendo(true);
    setErrorDecision(null);
    try {
      const res = await decidirAprobacion(detalle.pendingApprovalId, decision, {
        decisionReason: r.motivo || undefined,
      });
      if (!res.ok) {
        setErrorDecision(res.message);
        return;
      }
      setMsgDecision(res.message);
      cargar();
    } finally {
      setDecidiendo(false);
    }
  }

  async function onProgramar() {
    if (!id || !fechaProgramada) return;
    setGuardandoFecha(true);
    setErrorFecha(null);
    try {
      const res = await programarEntrega(id, fechaProgramada);
      if (!res.ok || !res.result) {
        setErrorFecha(res.message);
        return;
      }
      const actualizado = res.result;
      setDetalle((prev) => (prev ? { ...prev, requerimiento: actualizado } : prev));
      setMsgEnvio(res.message);
      setProgramando(false);
    } finally {
      setGuardandoFecha(false);
    }
  }

  const puedeVerCompras =
    !!id &&
    esGestor &&
    (detalle?.requerimiento.status === "EN_ATENCION" || detalle?.requerimiento.status === "ATENCION_PARCIAL");

  useEffect(() => {
    if (!puedeVerCompras || !id) return;
    let vigente = true;
    setCargandoCompras(true);
    obtenerDetalleCompra(id)
      .then((res) => {
        if (vigente && res.ok && res.result) setComprasDetalle(res.result);
      })
      .finally(() => {
        if (vigente) setCargandoCompras(false);
      });
    return () => {
      vigente = false;
    };
  }, [puedeVerCompras, id]);

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </CardBody>
        </Card>
      </div>
    );
  }
  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!detalle) return null;

  const { requerimiento, items, pendingApprovalId, observacion } = detalle;
  const esDueno = usuario?.id === requerimiento.requesterUserId;
  const observado = requerimiento.status === "OBSERVADO";
  const puedeEnviar = (requerimiento.status === "BORRADOR" || observado) && (esDueno || esGestor);
  const puedeProgramar = esGestor && ESTADOS_PROGRAMABLES.includes(requerimiento.status);
  // El backend solo envia pendingApprovalId a quien le toca decidir (DEC-L030).
  const puedeDecidir = requerimiento.status === "PENDIENTE_APROBACION" && !!pendingApprovalId;
  const mensajeCreacion = searchParams.get("msg");

  return (
    <div>
      <PageHeader
        title={<span className="font-mono">{requerimiento.code}</span>}
        subtitle={
          <>
            {requerimiento.cecoNombre} · Solicitado por {requerimiento.requesterNombre ?? "—"} el{" "}
            {fmtFechaHora(requerimiento.createdAt)}
          </>
        }
        backTo="/requerimientos"
        backLabel="Requerimientos"
        actions={
          <>
            <BotonDescarga descargar={() => descargarPdfRequerimiento(requerimiento.id, requerimiento.code)} />
            <EstadoBadge estado={requerimiento.status} />
          </>
        }
      />

      <div className="mb-4 flex flex-col gap-3">
        {mensajeCreacion && <Alert tone="good">{mensajeCreacion}</Alert>}
        {msgEnvio && <Alert tone="good">{msgEnvio}</Alert>}
        {msgDecision && <Alert tone="good">{msgDecision}</Alert>}
        {actualizadoEnVivo && !msgDecision && (
          <Alert tone="good">El estado de este requerimiento acaba de cambiar.</Alert>
        )}
      </div>

      {observado && observacion && (
        <Alert tone="danger" className="mb-4">
          <p className="font-medium">Observado: {observacion.motivo}</p>
          <p className="mt-0.5 text-xs opacity-80">
            {observacion.observadoPor ?? "—"} · {fmtFechaHora(observacion.fecha)}
          </p>
        </Alert>
      )}

      {detalle.controlPresupuestal && (
        <Alert tone={detalle.controlPresupuestal.estado === "CUBIERTO" ? "good" : "danger"} className="mb-4">
          {detalle.controlPresupuestal.estado === "CUBIERTO" ? (
            <p className="font-medium">
              Cubierto por el presupuesto {detalle.controlPresupuestal.presupuestoCodigo} ({detalle.controlPresupuestal.periodo})
              {detalle.controlPresupuestal.monto != null && ` · ${soles.format(detalle.controlPresupuestal.monto)}`}
            </p>
          ) : (
            <>
              <p className="font-medium">
                Excepción presupuestal {detalle.controlPresupuestal.periodo}:{" "}
                {detalle.controlPresupuestal.desviaciones.map(textoDesviacion).join(" · ")}
              </p>
              <p className="mt-0.5 text-xs opacity-80">Justificación: {detalle.controlPresupuestal.justificacion}</p>
            </>
          )}
        </Alert>
      )}

      {puedeEnviar && (
        <Card className="mb-4">
          <CardBody className="flex flex-wrap items-center gap-2">
            {observado ? (
              <>
                <Button loading={enviando} onClick={onEnviar}>
                  {enviando ? "Reenviando…" : "Reenviar a aprobación"}
                </Button>
                <Button href={`/requerimientos/${id}/editar`} variant="ghost">
                  Corregir
                </Button>
              </>
            ) : (
              <>
                <Button loading={enviando} onClick={onEnviar}>
                  {enviando ? "Enviando…" : "Enviar a aprobación"}
                </Button>
                <Button href={`/requerimientos/${id}/editar`} variant="ghost">
                  Editar
                </Button>
              </>
            )}
            {errorEnvio && <Alert tone="danger" className="w-full">{errorEnvio}</Alert>}
          </CardBody>
        </Card>
      )}

      {puedeDecidir && (
        <Card className="mb-4">
          <CardBody className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Button loading={decidiendo} onClick={() => onDecidir("APROBADO")}>
                {decidiendo ? "Procesando…" : "Aprobar"}
              </Button>
              <Button variant="ghost" disabled={decidiendo} onClick={() => onDecidir("OBSERVADO")}>
                Observar
              </Button>
              <MenuAcciones
                acciones={[{ label: "Rechazar", peligro: true, disabled: decidiendo, onClick: () => onDecidir("RECHAZADO") }]}
              />
            </div>
            {errorDecision && <Alert tone="danger">{errorDecision}</Alert>}
          </CardBody>
        </Card>
      )}

      <Card className="mb-4">
        <CardBody>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="rotulo">Prioridad</div>
              <div className="mt-1 text-sm text-slate-700">{requerimiento.priority}</div>
            </div>
            <div>
              <div className="rotulo">Propósito</div>
              <div className="mt-1 text-sm text-slate-700">{requerimiento.purpose}</div>
            </div>
            <div>
              <div className="rotulo">Fecha requerida</div>
              <div className="mt-1 text-sm text-slate-700">{fmtFecha(requerimiento.requiredDate)}</div>
            </div>
            <div>
              <div className="rotulo">Entrega programada</div>
              {programando ? (
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <input
                    type="date"
                    autoFocus
                    min={hoyIso()}
                    value={fechaProgramada}
                    onChange={(e) => setFechaProgramada(e.target.value)}
                    className={inputClass}
                  />
                  <Button size="sm" loading={guardandoFecha} disabled={!fechaProgramada} onClick={onProgramar}>
                    Guardar
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={guardandoFecha}
                    onClick={() => {
                      setProgramando(false);
                      setErrorFecha(null);
                    }}
                  >
                    Cancelar
                  </Button>
                  {errorFecha && <Alert tone="danger" className="w-full">{errorFecha}</Alert>}
                </div>
              ) : (
                <div className="mt-1 flex items-center gap-2 text-sm text-slate-700">
                  {fmtFecha(requerimiento.scheduledDeliveryDate)}
                  {puedeProgramar && (
                    <button
                      type="button"
                      onClick={() => {
                        setFechaProgramada(requerimiento.scheduledDeliveryDate ?? requerimiento.requiredDate ?? "");
                        setProgramando(true);
                      }}
                      className="text-xs font-medium text-brand-600 hover:text-brand-700"
                    >
                      {requerimiento.scheduledDeliveryDate ? "Cambiar" : "Programar"}
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
          <div className="mt-4">
            <div className="rotulo">Justificación</div>
            <div className="mt-1 text-sm text-slate-700">{requerimiento.justification}</div>
          </div>
        </CardBody>
      </Card>

      <Card className="mb-4 overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Items ({items.length})</div>
        </CardBody>
        <div className="overflow-hidden">
          <table className="tabla">
            <thead>
              <tr className="border-y border-slate-200 cabecera-tabla">
                <th className="px-5 py-2.5">SKU</th>
                <th className="px-5 py-2.5">Producto</th>
                <th className="px-5 py-2.5 text-right">Cantidad</th>
                <th className="px-5 py-2.5">Observación</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((it) => (
                <tr key={it.id}>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante}</td>
                  <td className="col-flex px-5 py-2.5 text-slate-700" title={String(it.nombreProducto)}>
                    {it.nombreProducto}
                    {it.atributo && <span className="text-slate-400"> · {it.atributo}</span>}
                  </td>
                  <td className="px-5 py-2.5 text-right tabular-nums text-slate-700">
                    {it.cantidad} <span className="text-slate-400">{it.unidadMedida}</span>
                  </td>
                  <td className="col-flex px-5 py-2.5 text-slate-600" title={String(it.observacion ?? "—")}>{it.observacion ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {requerimiento.status !== "BORRADOR" && id && (
        // key: si el estado cambia en vivo, el Tracking se vuelve a pedir.
        <TrackingRequerimiento key={requerimiento.status} requestId={id} esGestor={esGestor} />
      )}

      {puedeVerCompras && (
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShoppingCart className="size-4.5 text-slate-500" />
              <h2 className="font-semibold text-slate-900">Compras</h2>
            </div>
            {comprasDetalle && comprasDetalle.items.some((it) => it.cantidadPendiente > 0) && (
              <Button href={`/compras/requerimientos/${id}/caja-chica`} size="sm" variant="secondary">
                Caja chica
              </Button>
            )}
          </CardHeader>
          <CardBody>
            {cargandoCompras ? (
              <Skeleton className="h-4 w-full" />
            ) : !comprasDetalle || (comprasDetalle.necesidades.length === 0 && comprasDetalle.rfqs.length === 0) ? (
              <p className="text-sm text-slate-500">Sin compras: lo pedido se atiende con stock.</p>
            ) : (
              <div className="flex flex-col gap-4">
              {comprasDetalle.necesidades.length > 0 && (
                <ul className="flex flex-col gap-2">
                  {comprasDetalle.necesidades.map((n) => (
                    <li key={n.needId} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                      <span className="min-w-0 flex-1 truncate text-slate-700" title={n.nombreProducto}>
                        <span className="font-mono text-xs text-slate-500">{n.codigoVariante ?? n.skuBase}</span>{" "}
                        {n.nombreProducto} · {n.qty} {n.unidadMedida}
                      </span>
                      {n.consolidationId ? (
                        <Link
                          to={`/compras/consolidados/${n.consolidationId}`}
                          className="flex items-center gap-2 font-mono text-xs font-medium text-brand-600 hover:text-brand-700"
                        >
                          {n.consolidationCode}
                          {n.consolidationStatus && <ConsolidadoEstadoBadge estado={n.consolidationStatus} />}
                        </Link>
                      ) : (
                        <Badge tone="amarillo">{n.status === "CAJA_CHICA" ? "Caja chica" : "Ruta por decidir"}</Badge>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              {comprasDetalle.rfqs.length > 0 && (
              <ul className="flex flex-col gap-2 border-t border-slate-100 pt-3">
                {comprasDetalle.rfqs.map((rfq) => (
                  <li key={rfq.rfqId} className="flex items-center justify-between gap-2">
                    <Link
                      to={`/compras/rfq/${rfq.rfqId}`}
                      className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700"
                    >
                      {rfq.rfqCode}
                    </Link>
                    <span className="flex-1 text-sm text-slate-600">{rfq.proveedorNombre}</span>
                    <RfqEstadoBadge estado={rfq.status} />
                  </li>
                ))}
              </ul>
              )}
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
