import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Check, Download, Route } from "lucide-react";
import { descargarTrackingExcel, obtenerTracking } from "../api/tracking";
import type { EntregaTracking, Tracking } from "../api/types";
import { EnvioBadge, ETIQUETA_MODALIDAD, fmtCantidad, fmtDia, fmtFecha } from "./AlmacenBadges";
import Alert from "./ui/Alert";
import Badge from "./ui/Badge";
import Button from "./ui/Button";
import { Card, CardBody, CardHeader } from "./ui/Card";
import { Skeleton } from "./ui/Skeleton";
import BotonDescarga from "./BotonDescarga";
import { descargarPdfTracking } from "../api/documentos";

function fmtMonto(n: number | null, moneda?: string | null): string {
  if (n === null || n === undefined) return "—";
  return `${moneda ?? "S/"} ${Number(n).toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function EstadoEntrega({ estado }: { estado: EntregaTracking["estadoEntrega"] }) {
  if (estado === "SIN_ENVIO") return <Badge tone="azul">Por enviar</Badge>;
  return <EnvioBadge estado={estado} />;
}

function Documentos({ e }: { e: EntregaTracking }) {
  const docs = [
    { url: e.urlGuia, texto: "Guía" },
    { url: e.urlTicket, texto: "Ticket" },
    { url: e.urlConformidad, texto: "Conformidad" },
  ].filter((d) => d.url);
  if (docs.length === 0) return <span className="text-slate-400">—</span>;
  return (
    <span className="flex flex-wrap gap-x-2">
      {docs.map((d) => (
        <a key={d.texto} href={d.url!} target="_blank" rel="noreferrer" className="text-brand-600 hover:text-brand-700">
          {d.texto}
        </a>
      ))}
    </span>
  );
}

/** RF-039/081/083: hitos, entregas por NI e historial del requerimiento. */
export default function TrackingRequerimiento({ requestId, esGestor }: { requestId: string; esGestor: boolean }) {
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [descargando, setDescargando] = useState(false);
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null);
  const [verHistorial, setVerHistorial] = useState(false);

  useEffect(() => {
    let vigente = true;
    obtenerTracking(requestId).then((res) => {
      if (!vigente) return;
      if (res.ok) setTracking(res.result);
      else setError(res.message);
    });
    return () => {
      vigente = false;
    };
  }, [requestId]);

  async function onExcel() {
    if (!tracking) return;
    setDescargando(true);
    setErrorDescarga(null);
    try {
      const res = await descargarTrackingExcel(requestId, tracking.requerimiento.code);
      if (!res.ok) setErrorDescarga(res.message);
    } finally {
      setDescargando(false);
    }
  }

  if (error) return <Alert tone="danger" className="mb-4">{error}</Alert>;

  return (
    <Card className="mb-4 overflow-hidden">
      <CardHeader className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Route className="size-4.5 text-slate-500" />
          <h2 className="font-semibold text-slate-900">Tracking</h2>
        </div>
        {tracking && (
          <div className="flex items-center gap-2">
            <BotonDescarga descargar={() => descargarPdfTracking(requestId, tracking.requerimiento.code)} />
            {tracking.entregas.length > 0 && (
              <Button size="sm" variant="secondary" loading={descargando} onClick={onExcel}>
                <Download className="size-4" />
                Excel
              </Button>
            )}
          </div>
        )}
      </CardHeader>

      {!tracking ? (
        <CardBody className="flex flex-col gap-3">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-16 w-full" />
        </CardBody>
      ) : (
        <>
          <CardBody className="flex flex-col gap-5">
            {errorDescarga && <Alert tone="danger">{errorDescarga}</Alert>}

            <div>
              <div className="mb-1.5 flex items-baseline justify-between text-sm">
                <span className="text-slate-600">
                  Entregado {fmtCantidad(tracking.unidadesEntregadas)} de {fmtCantidad(tracking.unidadesSolicitadas)} u.
                </span>
                <span className="font-semibold text-slate-900">{tracking.porcentajeAtencion}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                <div
                  className="h-full rounded-full bg-brand-600 transition-[width] duration-700 ease-(--ease-suave)"
                  style={{ width: `${tracking.porcentajeAtencion}%` }}
                />
              </div>
            </div>

            <ol className="grid gap-3 sm:grid-cols-5">
              {tracking.hitos.map((h) => (
                <li
                  key={h.codigo}
                  className={`rounded-lg border px-3 py-2.5 ${
                    h.cumplido ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-white"
                  }`}
                >
                  <div className="flex items-center gap-1.5">
                    <span
                      className={`flex size-4.5 shrink-0 items-center justify-center rounded-full ${
                        h.cumplido ? "bg-emerald-600 text-white" : "border border-slate-300"
                      }`}
                    >
                      {h.cumplido && <Check className="size-3" strokeWidth={3} />}
                    </span>
                    <span className={`text-xs font-semibold uppercase tracking-wide ${h.cumplido ? "text-emerald-800" : "text-slate-500"}`}>
                      {h.etiqueta}
                    </span>
                  </div>
                  <div className="mt-1 text-xs text-slate-600">{h.detalle}</div>
                  {h.fecha && <div className="mt-0.5 text-xs text-slate-400">{fmtFecha(h.fecha)}</div>}
                </li>
              ))}
            </ol>

            <div className="grid gap-4 text-sm sm:grid-cols-3">
              <div>
                <div className="rotulo">Entrega programada</div>
                <div className="mt-1 text-slate-700">{fmtDia(tracking.entregaProgramada)}</div>
              </div>
              <div>
                <div className="rotulo">CECO que recepciona</div>
                <div className="mt-1 text-slate-700">
                  ({tracking.cecoCodigo}) {tracking.requerimiento.cecoNombre}
                </div>
              </div>
              <div>
                <div className="rotulo">Dirección de envío</div>
                <div className="mt-1 text-slate-700">{tracking.direccionEnvio ?? "—"}</div>
              </div>
            </div>
          </CardBody>

          <div className="overflow-x-auto">
            <table className="tabla">
              <thead>
                <tr className="border-y border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className="px-5 py-2.5">NI</th>
                  <th className="px-5 py-2.5">Envío</th>
                  <th className="px-5 py-2.5">Transportista</th>
                  <th className="px-5 py-2.5">Ítems</th>
                  <th className="px-5 py-2.5">Costo s/IGV</th>
                  <th className="px-5 py-2.5">Estado</th>
                  <th className="px-5 py-2.5">Documentos</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {tracking.entregas.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-5 py-4 text-sm text-slate-500">
                      Aún no hay despachos para este requerimiento.
                    </td>
                  </tr>
                ) : (
                  tracking.entregas.map((e) => (
                    <tr key={e.inventoryNoteId}>
                      <td className="px-5 py-2.5 font-mono text-xs">
                        {esGestor ? (
                          <Link to={`/almacen/notas/${e.inventoryNoteId}`} className="font-medium text-brand-600 hover:text-brand-700">
                            {e.notaCodigo}
                          </Link>
                        ) : (
                          e.notaCodigo
                        )}
                      </td>
                      <td className="px-5 py-2.5 text-slate-600">{fmtDia(e.fechaEnvio)}</td>
                      <td className="px-5 py-2.5 text-slate-600">
                        {e.modalidad ? ETIQUETA_MODALIDAD[e.modalidad] : "—"}
                        {e.transportista && <span className="text-slate-400"> · {e.transportista}</span>}
                        {e.codigoSeguimiento && <div className="font-mono text-xs text-slate-400">{e.codigoSeguimiento}</div>}
                      </td>
                      <td className="col-flex px-5 py-2.5 text-slate-600" title={e.itemsDespachados}>
                        {e.itemsDespachados}
                      </td>
                      <td className="px-5 py-2.5 text-slate-700">{fmtMonto(e.costoEnvioNeto, e.moneda)}</td>
                      <td className="px-5 py-2.5">
                        <EstadoEntrega estado={e.estadoEntrega} />
                      </td>
                      <td className="px-5 py-2.5 text-sm">
                        <Documentos e={e} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <CardBody className="border-t border-slate-100">
            <button
              type="button"
              onClick={() => setVerHistorial((v) => !v)}
              className="text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              {verHistorial ? "Ocultar historial" : `Ver historial (${tracking.eventos.length})`}
            </button>
            {verHistorial && (
              <ol className="mt-3 flex flex-col gap-2.5 border-l border-slate-200 pl-4">
                {tracking.eventos.map((ev, i) => (
                  <li key={i} className="text-sm">
                    <div className="flex flex-wrap items-baseline gap-x-2">
                      <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">{ev.etapa}</span>
                      <span className="text-slate-800">{ev.accion}</span>
                      {ev.referencia && <span className="font-mono text-xs text-slate-500">{ev.referencia}</span>}
                    </div>
                    <div className="text-xs text-slate-400">
                      {fmtFecha(ev.fecha)}
                      {ev.usuario && ` · ${ev.usuario}`}
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </CardBody>
        </>
      )}
    </Card>
  );
}
