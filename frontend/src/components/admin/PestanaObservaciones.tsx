import { useEffect, useState } from "react";
import { Bug, ExternalLink, ImageOff, Search } from "lucide-react";
import { Link } from "react-router-dom";
import {
  codigoReporte,
  descargarReportesExcel,
  ESTADOS_REPORTE,
  listarReportes,
  revisarReporte,
  verCapturaReporte,
  type EstadoReporte,
  type ReporteFila,
} from "../../api/reportes";
import { useDatos } from "../../api/useDatos";
import BotonDescarga from "../BotonDescarga";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";
import { SkeletonTable } from "../ui/Skeleton";
import { fechaHora, navegadorCorto } from "./registro/formato";

/**
 * Observaciones reportadas desde la pantalla (DEC-L104): el administrador las
 * revisa, responde (el usuario ve la respuesta en "Mis reportes") y las
 * exporta a Excel para consolidarlas.
 */

const FILTROS: { valor: string; label: string }[] = [
  { valor: "ABIERTOS", label: "Abiertas" },
  { valor: "", label: "Todas" },
  ...Object.entries(ESTADOS_REPORTE).map(([valor, e]) => ({ valor, label: e.label })),
];

function lugar(r: ReporteFila): string {
  if (!r.modulo) return r.pantalla ?? r.ruta;
  return r.pantalla && r.pantalla !== r.modulo ? `${r.modulo} · ${r.pantalla}` : r.modulo;
}

export default function PestanaObservaciones() {
  const [estado, setEstado] = useState("ABIERTOS");
  const [escrito, setEscrito] = useState("");
  const [q, setQ] = useState("");
  const [extra, setExtra] = useState<{ clave: string; filas: ReporteFila[]; pagina: number; hayMas: boolean } | null>(null);
  const [masCargando, setMasCargando] = useState(false);
  const [abierto, setAbierto] = useState<ReporteFila | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setQ(escrito), 350);
    return () => window.clearTimeout(t);
  }, [escrito]);

  const clave = `reportes:${estado}:${q}`;
  const datos = useDatos(clave, () => listarReportes(estado, q, 0));
  const pagina = datos.datos;
  const masDeEsta = extra?.clave === clave ? extra : null;
  const filas = [...(pagina?.filas ?? []), ...(masDeEsta?.filas ?? [])];
  const hayMas = masDeEsta ? masDeEsta.hayMas : !!pagina?.hayMas;

  async function cargarMas() {
    setMasCargando(true);
    try {
      const siguiente = (masDeEsta?.pagina ?? 0) + 1;
      const res = await listarReportes(estado, q, siguiente);
      if (res.ok && res.result) {
        const nuevas = res.result;
        setExtra({ clave, filas: [...(masDeEsta?.filas ?? []), ...nuevas.filas], pagina: siguiente, hayMas: nuevas.hayMas });
      }
    } finally {
      setMasCargando(false);
    }
  }

  function aplicarRevision(fila: ReporteFila) {
    setAbierto(fila);
    // Sale de "Abiertas" si se resolvio o descarto: se vuelve a pedir la lista.
    void datos.recargar();
    setExtra(null);
  }

  const conteo = pagina?.conteo;

  return (
    <div className="flex flex-col gap-4">
      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 px-4 py-3">
          <div className="relative min-w-[14rem] flex-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <input
              value={escrito}
              onChange={(e) => setEscrito(e.target.value)}
              placeholder="Buscar pantalla, zona, descripción, usuario, N°…"
              aria-label="Buscar observaciones"
              className={`${inputClass} w-full pl-8`}
            />
          </div>
          <select aria-label="Estado" value={estado} onChange={(e) => setEstado(e.target.value)} className={`${inputClass} w-auto`}>
            {FILTROS.map((f) => (
              <option key={f.valor} value={f.valor}>
                {f.label}
                {f.valor === "PENDIENTE" && conteo ? ` (${conteo.pendientes})` : ""}
                {f.valor === "EN_REVISION" && conteo ? ` (${conteo.enRevision})` : ""}
              </option>
            ))}
          </select>
          <BotonDescarga tipo="excel" etiqueta="Excel" descargar={() => descargarReportesExcel(estado, q)} />
        </div>

        {datos.error && (
          <div className="px-4 pb-3">
            <Alert tone="danger">{datos.error}</Alert>
          </div>
        )}

        {!pagina ? (
          !datos.error && <SkeletonTable cols={6} />
        ) : filas.length === 0 ? (
          <EmptyState icon={Bug} title="Sin observaciones" />
        ) : (
          <div className="overflow-x-auto">
            <table className="tabla">
              <thead>
                <tr className="cabecera-tabla border-b border-slate-200">
                  <th className="px-4 py-2.5">N°</th>
                  <th className="px-4 py-2.5">Fecha</th>
                  <th className="px-4 py-2.5">Usuario</th>
                  <th className="px-4 py-2.5">Pantalla</th>
                  <th className="col-flex px-4 py-2.5">Observación</th>
                  <th className="px-4 py-2.5">Estado</th>
                </tr>
              </thead>
              <tbody>
                {filas.map((r) => (
                  <tr
                    key={r.id}
                    onClick={() => setAbierto(r)}
                    className="cursor-pointer border-b border-slate-100 last:border-b-0 hover:bg-slate-50"
                  >
                    <td className="px-4 py-2.5 font-mono text-xs text-slate-500">{codigoReporte(r.numero)}</td>
                    <td className="px-4 py-2.5 text-slate-600">{fechaHora(r.en)}</td>
                    <td className="px-4 py-2.5 text-slate-800">{r.usuario}</td>
                    <td className="px-4 py-2.5 text-slate-600">{lugar(r)}</td>
                    <td className="col-flex px-4 py-2.5 text-slate-800" title={r.descripcion}>
                      {r.descripcion}
                    </td>
                    <td className="px-4 py-2.5">
                      <Badge tone={ESTADOS_REPORTE[r.estado].tono}>{ESTADOS_REPORTE[r.estado].label}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {hayMas && (
          <div className="flex justify-center border-t border-slate-100 py-3">
            <Button size="sm" variant="secondary" loading={masCargando} onClick={cargarMas}>
              Ver más
            </Button>
          </div>
        )}
      </Card>

      {abierto && <DetalleReporte key={abierto.id} reporte={abierto} onCerrar={() => setAbierto(null)} onRevisado={aplicarRevision} />}
    </div>
  );
}

function DetalleReporte({
  reporte: r,
  onCerrar,
  onRevisado,
}: {
  reporte: ReporteFila;
  onCerrar: () => void;
  onRevisado: (fila: ReporteFila) => void;
}) {
  const [estado, setEstado] = useState<EstadoReporte>(r.estado === "PENDIENTE" ? "EN_REVISION" : r.estado);
  const [respuesta, setRespuesta] = useState(r.respuesta ?? "");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [captura, setCaptura] = useState<string | null>(null);
  const [sinCaptura, setSinCaptura] = useState(false);

  useEffect(() => {
    if (!r.tieneCaptura) return;
    let url: string | null = null;
    let vivo = true;
    void verCapturaReporte(r.id).then((u) => {
      url = u;
      if (!vivo) {
        if (u) URL.revokeObjectURL(u);
        return;
      }
      if (u) setCaptura(u);
      else setSinCaptura(true);
    });
    return () => {
      vivo = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [r.id, r.tieneCaptura]);

  async function guardar() {
    setGuardando(true);
    setError(null);
    const res = await revisarReporte(r.id, estado, respuesta);
    setGuardando(false);
    if (res.ok && res.result) onRevisado(res.result);
    else setError(res.message);
  }

  return (
    <PanelLateral
      titulo={`${codigoReporte(r.numero)} · ${lugar(r)}`}
      subtitulo={`${r.usuario} · ${fechaHora(r.en)}`}
      onCerrar={onCerrar}
      pie={
        <Button onClick={guardar} loading={guardando}>
          Guardar
        </Button>
      }
    >
      <SeccionPanel
        titulo="Zona señalada"
        accion={
          <Link to={r.ruta} className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline">
            <ExternalLink className="size-3.5" />
            Ir a la pantalla
          </Link>
        }
      >
        <p className="whitespace-normal text-sm text-slate-800">{r.zona ?? "Toda la pantalla"}</p>
      </SeccionPanel>
      <SeccionPanel titulo="Observación">
        <p className="whitespace-pre-wrap text-sm text-slate-800">{r.descripcion}</p>
      </SeccionPanel>
      {r.tieneCaptura && (
        <SeccionPanel titulo="Captura">
          {captura ? (
            <a href={captura} target="_blank" rel="noopener noreferrer">
              <img src={captura} alt="Captura del reporte" className="max-h-72 w-full rounded-md border border-slate-200 object-contain" />
            </a>
          ) : sinCaptura ? (
            <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
              <ImageOff className="size-4" /> No se pudo cargar
            </span>
          ) : (
            <div className="h-40 animate-pulse rounded-md bg-slate-100" />
          )}
        </SeccionPanel>
      )}
      <SeccionPanel titulo="Revisión">
        <div className="flex flex-col gap-3">
          <select
            aria-label="Estado del reporte"
            value={estado}
            onChange={(e) => setEstado(e.target.value as EstadoReporte)}
            className={`${inputClass} w-full`}
          >
            {Object.entries(ESTADOS_REPORTE).map(([valor, e]) => (
              <option key={valor} value={valor}>
                {e.label}
              </option>
            ))}
          </select>
          <textarea
            rows={3}
            maxLength={1000}
            value={respuesta}
            onChange={(e) => setRespuesta(e.target.value)}
            placeholder="Respuesta para el usuario (opcional)"
            className={`${inputClass} w-full resize-y`}
          />
          {error && <Alert tone="danger">{error}</Alert>}
          {r.revisadoPor && (
            <p className="text-xs text-slate-500">
              Última revisión: {r.revisadoPor}
              {r.revisadoEn ? ` · ${fechaHora(r.revisadoEn)}` : ""}
            </p>
          )}
        </div>
      </SeccionPanel>
      <SeccionPanel titulo="Contexto">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
          <dt className="text-slate-500">Ruta</dt>
          <dd className="truncate font-mono text-slate-700" title={r.ruta}>{r.ruta}</dd>
          <dt className="text-slate-500">Rol</dt>
          <dd className="text-slate-700">{r.roles ?? "—"}</dd>
          <dt className="text-slate-500">Navegador</dt>
          <dd className="text-slate-700">{navegadorCorto(r.navegador)}</dd>
          <dt className="text-slate-500">Pantalla</dt>
          <dd className="text-slate-700">{r.viewport ?? "—"}</dd>
        </dl>
      </SeccionPanel>
    </PanelLateral>
  );
}
