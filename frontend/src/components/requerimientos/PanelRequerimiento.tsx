import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { ChevronDown, Clock3, ExternalLink, X } from "lucide-react";
import { decidirAprobacion } from "../../api/aprobaciones";
import { obtenerDetalleCompra } from "../../api/compras";
import { enviarRequerimiento, obtenerAvance, obtenerRequerimiento } from "../../api/requerimientos";
import { obtenerTracking } from "../../api/tracking";
import type {
  AvanceItem,
  ComprasDetalle,
  EtapaCompraItem,
  FilaBandeja,
  RequerimientoDetalle,
  Tracking,
} from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import { EnvioBadge } from "../AlmacenBadges";
import { fmtCantidad, fmtDia } from "../formato";
import EstadoBadge from "../EstadoBadge";
import { EMPRESAS } from "../empresas";
import PrioridadBadge from "../PrioridadBadge";
import RfqEstadoBadge from "../RfqEstadoBadge";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { useConfirmar } from "../ui/Confirmacion";
import { Skeleton } from "../ui/Skeleton";
import { etiquetaAccion, rutaAccion } from "./acciones";
import BarraAvance from "./BarraAvance";
import { diasParaVencer, textoVencimiento, TRAMOS } from "./bandeja";

const ETAPA: Record<EtapaCompraItem, string> = {
  RUTA: "Ruta por decidir",
  CAJA_CHICA: "Caja chica",
  POR_COTIZAR: "Por cotizar",
  EN_COTIZACION: "En cotización",
  ADJUDICADO: "Adjudicado, OC por emitir",
  OC: "OC emitida",
};

const PROPOSITO: Record<string, string> = {
  CONSUMO: "Consumo",
  VENTA: "Venta",
  PRESTAMO: "Préstamo",
  IMPLEMENTACION: "Implementación",
};

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

function avanceDe(i: AvanceItem) {
  const q = i.solicitado || 1;
  return { entregado: i.entregado / q, enCamino: i.enCamino / q, listo: i.listo / q, enCompra: i.enCompra / q };
}

function Seccion({ titulo, children, extra }: { titulo: string; children: React.ReactNode; extra?: React.ReactNode }) {
  return (
    <section className="border-b border-slate-100 px-5 py-4 last:border-b-0">
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <h3 className="rotulo">{titulo}</h3>
        {extra}
      </div>
      {children}
    </section>
  );
}

function Dato({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="rotulo">{label}</dt>
      <dd className="mt-0.5 truncate text-sm text-slate-800">{children}</dd>
    </div>
  );
}

function Producto({ i, esGestor }: { i: AvanceItem; esGestor: boolean }) {
  const cant = (n: number) => `${fmtCantidad(n)}`;
  const partes: { label: string; valor: number; tono: string }[] = [
    { label: "Entregado", valor: i.entregado, tono: "text-emerald-700" },
    { label: "En camino", valor: i.enCamino, tono: "text-blue-700" },
    { label: "Listo", valor: i.listo, tono: "text-sky-700" },
    { label: "En compra", valor: i.enCompra, tono: "text-amber-700" },
  ].filter((p) => p.valor > 0);
  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-slate-900">
            {i.nombreProducto}
            {i.atributo && <span className="text-slate-400"> · {i.atributo}</span>}
          </p>
          <p className="font-mono text-[0.6875rem] text-slate-400">{i.codigo}</p>
        </div>
        <span className="shrink-0 text-sm tabular-nums text-slate-700">
          {cant(i.solicitado)} <span className="text-xs text-slate-400">{i.unidadMedida}</span>
        </span>
      </div>
      <BarraAvance avance={avanceDe(i)} className="mt-2" />
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        {partes.length === 0 ? (
          <span className="text-slate-400">Sin atender</span>
        ) : (
          partes.map((p) => (
            <span key={p.label} className="text-slate-500">
              {p.label} <b className={`font-medium tabular-nums ${p.tono}`}>{cant(p.valor)}</b>
            </span>
          ))
        )}
        {i.recibidoCompra > 0 && (
          <span className="text-slate-500">
            Recibido de compra <b className="font-medium tabular-nums text-slate-700">{cant(i.recibidoCompra)}</b>
          </span>
        )}
        {i.etapaCompra && i.enCompra > 0 && (
          <span className="ml-auto flex items-center gap-1.5">
            <Badge tone="amarillo">
              {i.etapaCompra === "OC" && i.orderCode ? `OC ${i.orderCode}` : ETAPA[i.etapaCompra]}
              {i.etapaCompra === "EN_COTIZACION" && ` · ${i.ofertas} oferta${i.ofertas === 1 ? "" : "s"}`}
            </Badge>
            {esGestor && i.orderId ? (
              <Link to={`/compras/ordenes/${i.orderId}`} className="text-brand-600 hover:text-brand-700" title="Ver OC">
                <ExternalLink className="size-3.5" />
              </Link>
            ) : esGestor && i.consolidationId ? (
              <Link
                to={`/compras/comparativo?ids=${i.consolidationId}`}
                className="text-brand-600 hover:text-brand-700"
                title="Ver comparativo"
              >
                <ExternalLink className="size-3.5" />
              </Link>
            ) : null}
          </span>
        )}
      </div>
    </li>
  );
}

/**
 * Panel lateral de un requerimiento (DEC-L048): se consulta sin salir de la
 * bandeja. Productos con su avance, datos, compras vinculadas, entregas,
 * historial y las acciones que el usuario puede hacer.
 */
export default function PanelRequerimiento({
  id,
  fila,
  esGestor,
  onCerrar,
  onCambio,
}: {
  id: string;
  fila: FilaBandeja | null;
  esGestor: boolean;
  onCerrar: () => void;
  onCambio: () => void;
}) {
  const { usuario } = useAuth();
  const confirmar = useConfirmar();
  const [detalle, setDetalle] = useState<RequerimientoDetalle | null>(null);
  const [avance, setAvance] = useState<AvanceItem[] | null>(null);
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [compras, setCompras] = useState<ComprasDetalle | null>(null);
  const [verCompras, setVerCompras] = useState(false);
  const [verHistorial, setVerHistorial] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [procesando, setProcesando] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let vigente = true;
    Promise.all([obtenerRequerimiento(id), obtenerAvance(id), obtenerTracking(id)]).then(([d, a, t]) => {
      if (!vigente) return;
      if (d.ok && d.result) setDetalle(d.result);
      else setError(d.message);
      if (a.ok && a.result) setAvance(a.result.items);
      if (t.ok && t.result) setTracking(t.result);
    });
    return () => {
      vigente = false;
    };
  }, [id, version]);

  useEffect(() => {
    if (!verCompras || compras) return;
    obtenerDetalleCompra(id).then((res) => res.ok && res.result && setCompras(res.result));
  }, [verCompras, compras, id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar]);

  function recargar(mensaje: string) {
    setAviso(mensaje);
    setError(null);
    setDetalle(null);
    setAvance(null);
    setTracking(null);
    setCompras(null);
    setVersion((v) => v + 1);
    onCambio();
  }

  async function onDecidir(decision: "APROBADO" | "OBSERVADO" | "RECHAZADO") {
    if (!detalle?.pendingApprovalId) return;
    const code = detalle.requerimiento.code;
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
    setProcesando(true);
    try {
      const res = await decidirAprobacion(detalle.pendingApprovalId, decision, {
        decisionReason: r.motivo || undefined,
      });
      if (res.ok) recargar(res.message);
      else setError(res.message);
    } finally {
      setProcesando(false);
    }
  }

  async function onEnviar() {
    setProcesando(true);
    try {
      let res = await enviarRequerimiento(id);
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
      if (res.ok) recargar(res.message);
      else setError(res.message);
    } finally {
      setProcesando(false);
    }
  }

  const r = detalle?.requerimiento;
  const empresa = EMPRESAS.find((e) => e.code === (r?.empresaCode ?? fila?.empresaCode));
  const items = detalle?.items ?? [];
  const tituloPanel = items.length > 0 ? items[0].nombreProducto : (fila?.resumenItems?.split(" · ")[0] ?? "");
  const esDueno = !!r && r.requesterUserId === usuario?.id;
  const editable = esDueno && (r?.status === "BORRADOR" || r?.status === "OBSERVADO");
  const operativas = esGestor ? (fila?.acciones ?? []).filter((a) => !a.espera) : [];
  const esperas = (fila?.acciones ?? []).filter((a) => a.espera);
  const venc = r ? textoVencimiento(diasParaVencer(r)) : null;
  const eventos = tracking?.eventos ?? [];
  const eventosVisibles = verHistorial ? eventos : eventos.slice(-6);
  const conCompras = esGestor && !!r && ["VERIFICACION_STOCK", "EN_ATENCION", "ATENCION_PARCIAL"].includes(r.status);

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="panel-req-titulo"
        className="flex h-full w-full max-w-2xl flex-col border-l border-slate-200 bg-white shadow-flotante"
      >
        <header className="border-b border-slate-200 px-5 py-4">
          <div className="flex items-start gap-3">
            <div className="min-w-0 flex-1">
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500">
                <span className="flex items-center gap-1 font-medium text-slate-600">
                  <span className={`size-1.5 rounded-full ${empresa?.marca ?? "bg-slate-400"}`} aria-hidden="true" />
                  {empresa?.label ?? r?.empresaCode ?? fila?.empresaCode}
                </span>
                <span className="font-mono text-[0.6875rem] text-slate-400">{r?.code ?? fila?.code}</span>
              </p>
              <h2 id="panel-req-titulo" className="mt-1 text-base font-semibold text-slate-900">
                {tituloPanel || <Skeleton className="h-5 w-64" />}
                {items.length > 1 && <span className="font-normal text-slate-400"> +{items.length - 1} más</span>}
              </h2>
              {r && (
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <EstadoBadge estado={r.status} />
                  {r.priority !== "NORMAL" && <PrioridadBadge prioridad={r.priority} />}
                  {venc && <span className={`text-xs font-medium ${venc.tono}`}>{venc.texto}</span>}
                </div>
              )}
            </div>
            <button
              type="button"
              onClick={onCerrar}
              aria-label="Cerrar"
              className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
            >
              <X className="size-5" />
            </button>
          </div>

          {r && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {detalle?.pendingApprovalId && (
                <>
                  <Button size="sm" loading={procesando} onClick={() => onDecidir("APROBADO")}>
                    Aprobar
                  </Button>
                  <Button size="sm" variant="secondary" disabled={procesando} onClick={() => onDecidir("OBSERVADO")}>
                    Observar
                  </Button>
                  <Button size="sm" variant="ghost" disabled={procesando} onClick={() => onDecidir("RECHAZADO")}>
                    Rechazar
                  </Button>
                </>
              )}
              {editable && (
                <>
                  <Button size="sm" loading={procesando} onClick={onEnviar}>
                    {r.status === "OBSERVADO" ? "Reenviar" : "Enviar a aprobación"}
                  </Button>
                  <Button size="sm" variant="secondary" href={`/requerimientos/${id}/editar`}>
                    Editar
                  </Button>
                </>
              )}
              {operativas.map((a, k) => (
                <Button key={a.codigo} size="sm" variant={k === 0 ? "primary" : "secondary"} href={rutaAccion(a, id)}>
                  {etiquetaAccion(a)}
                </Button>
              ))}
              {esperas.map((a) => (
                <Link
                  key={a.codigo}
                  to={rutaAccion(a, id)}
                  className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-800"
                >
                  <Clock3 className="size-3.5" />
                  {etiquetaAccion(a)}
                </Link>
              ))}
              <Button size="sm" variant="ghost" href={`/requerimientos/${id}`} className="ml-auto">
                Ficha completa
              </Button>
            </div>
          )}
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {aviso && (
            <Alert tone="good" className="mx-5 mt-4">
              {aviso}
            </Alert>
          )}
          {error && (
            <Alert tone="danger" className="mx-5 mt-4">
              {error}
            </Alert>
          )}
          {detalle?.observacion && (
            <Alert tone="info" className="mx-5 mt-4">
              Observado: {detalle.observacion.motivo}
            </Alert>
          )}

          <Seccion
            titulo={avance ? `Productos (${avance.length})` : "Productos"}
            extra={
              <span className="hidden items-center gap-2.5 text-[0.6875rem] text-slate-400 sm:flex">
                {TRAMOS.map((t) => (
                  <span key={t.clave} className="flex items-center gap-1">
                    <span className={`size-2 rounded-sm ${t.color}`} aria-hidden="true" />
                    {t.label}
                  </span>
                ))}
              </span>
            }
          >
            {!avance ? (
              <div className="flex flex-col gap-3">
                {[0, 1].map((k) => (
                  <div key={k} className="flex flex-col gap-2">
                    <Skeleton className="h-4 w-2/3" />
                    <Skeleton className="h-1.5 w-full" />
                    <Skeleton className="h-3 w-1/2" />
                  </div>
                ))}
              </div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {avance.map((i) => (
                  <Producto key={i.requestItemId} i={i} esGestor={esGestor} />
                ))}
              </ul>
            )}
          </Seccion>

          <Seccion titulo="Datos generales">
            {!r ? (
              <div className="grid grid-cols-2 gap-3">
                {[0, 1, 2, 3].map((k) => (
                  <Skeleton key={k} className="h-8 w-full" />
                ))}
              </div>
            ) : (
              <>
                <dl className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-3">
                  <Dato label="CECO">{r.cecoNombre ?? "—"}</Dato>
                  <Dato label="Solicitante">{r.requesterNombre ?? "—"}</Dato>
                  <Dato label="Propósito">{PROPOSITO[r.purpose] ?? r.purpose}</Dato>
                  <Dato label="Fecha requerida">{fmtDia(r.requiredDate)}</Dato>
                  <Dato label="Entrega programada">{fmtDia(r.scheduledDeliveryDate)}</Dato>
                  <Dato label="Creado">{fmtFechaHora(r.createdAt)}</Dato>
                </dl>
                {r.justification && <p className="mt-3 text-sm text-slate-600">{r.justification}</p>}
              </>
            )}
          </Seccion>

          {conCompras && (
            <Seccion
              titulo="Compras vinculadas"
              extra={
                <button
                  type="button"
                  onClick={() => setVerCompras((v) => !v)}
                  aria-expanded={verCompras}
                  className="flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-800"
                >
                  {verCompras ? "Ocultar" : "Ver"}
                  <ChevronDown className={`size-3.5 transition-transform ${verCompras ? "rotate-180" : ""}`} />
                </button>
              }
            >
              {!verCompras ? null : !compras ? (
                <Skeleton className="h-4 w-full" />
              ) : compras.rfqs.length === 0 && compras.necesidades.length === 0 ? (
                <p className="text-sm text-slate-500">Sin compras: se atiende con stock.</p>
              ) : (
                <ul className="flex flex-col gap-2 text-sm">
                  {compras.rfqs.map((q) => (
                    <li key={q.rfqId} className="flex items-center gap-2">
                      <Link to={`/compras/rfq/${q.rfqId}`} className="font-mono text-xs font-medium text-brand-600">
                        {q.rfqCode}
                      </Link>
                      <span className="min-w-0 flex-1 truncate text-slate-600">{q.proveedorNombre}</span>
                      <RfqEstadoBadge estado={q.status} />
                    </li>
                  ))}
                  {compras.rfqs.length === 0 && <li className="text-slate-500">Aún sin solicitudes de cotización.</li>}
                </ul>
              )}
            </Seccion>
          )}

          {tracking && tracking.entregas.length > 0 && (
            <Seccion titulo={`Entregas (${tracking.entregas.length})`}>
              <ul className="flex flex-col gap-2.5">
                {tracking.entregas.map((e) => (
                  <li key={e.inventoryNoteId} className="flex items-start justify-between gap-3 text-sm">
                    <div className="min-w-0">
                      {esGestor ? (
                        <Link
                          to={`/almacen/notas/${e.inventoryNoteId}`}
                          className="font-mono text-xs font-medium text-brand-600"
                        >
                          {e.notaCodigo}
                        </Link>
                      ) : (
                        <span className="font-mono text-xs text-slate-600">{e.notaCodigo}</span>
                      )}
                      <p className="truncate text-xs text-slate-500" title={e.itemsDespachados}>
                        {e.itemsDespachados}
                        {e.transportista && ` · ${e.transportista}`}
                        {e.codigoSeguimiento && ` · ${e.codigoSeguimiento}`}
                      </p>
                    </div>
                    {e.estadoEntrega === "SIN_ENVIO" ? (
                      <Badge tone="gris">Sin envío</Badge>
                    ) : (
                      <EnvioBadge estado={e.estadoEntrega} />
                    )}
                  </li>
                ))}
              </ul>
            </Seccion>
          )}

          <Seccion
            titulo="Historial"
            extra={
              eventos.length > 6 ? (
                <button
                  type="button"
                  onClick={() => setVerHistorial((v) => !v)}
                  className="text-xs font-medium text-slate-500 hover:text-slate-800"
                >
                  {verHistorial ? "Ver menos" : `Ver todo (${eventos.length})`}
                </button>
              ) : null
            }
          >
            {!tracking ? (
              <div className="flex flex-col gap-2">
                <Skeleton className="h-3 w-3/4" />
                <Skeleton className="h-3 w-2/3" />
              </div>
            ) : eventos.length === 0 ? (
              <p className="text-sm text-slate-500">Sin movimientos.</p>
            ) : (
              <ol className="relative flex flex-col gap-3 border-l border-slate-200 pl-4">
                {eventosVisibles.map((ev, k) => (
                  <li key={k} className="relative text-sm">
                    <span
                      aria-hidden="true"
                      className="absolute -left-[1.3125rem] top-1.5 size-2 rounded-full border-2 border-white bg-slate-400"
                    />
                    <p className="text-slate-800">
                      {ev.accion}
                      {ev.referencia && <span className="font-mono text-xs text-slate-500"> · {ev.referencia}</span>}
                    </p>
                    <p className="text-xs text-slate-500">
                      {fmtFechaHora(ev.fecha)} · {ev.etapa}
                      {ev.usuario && ` · ${ev.usuario}`}
                    </p>
                  </li>
                ))}
              </ol>
            )}
          </Seccion>
        </div>
      </aside>
    </div>,
    document.body,
  );
}
