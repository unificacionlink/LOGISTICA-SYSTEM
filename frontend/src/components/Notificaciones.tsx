import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Bell, CheckCheck } from "lucide-react";
import {
  listarNotificaciones,
  marcarNotificacionLeida,
  marcarTodasLeidas,
  type Notificacion,
} from "../api/notificaciones";
import { useDatos } from "../api/useDatos";
import Button from "./ui/Button";
import EmptyState from "./ui/EmptyState";
import PanelLateral from "./ui/PanelLateral";
import { Skeleton } from "./ui/Skeleton";

const TONO: Record<Notificacion["tipo"], string> = {
  REQ_APROBADO: "bg-emerald-500",
  REQ_COMPLETO: "bg-emerald-500",
  REQ_PARCIAL: "bg-brand-500",
  DESPACHO_ENVIADO: "bg-brand-500",
  RECEPCION_REGISTRADA: "bg-brand-500",
  REQ_OBSERVADO: "bg-amber-500",
  REQ_RECHAZADO: "bg-rose-500",
};

function hace(iso: string): string {
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return "Ahora";
  if (min < 60) return `Hace ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `Hace ${h} h`;
  return new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "short" });
}

/**
 * Campana del menú (DEC-L076): avisos de los cambios de estado de tus
 * requerimientos. Llegan en vivo (RealtimeProvider invalida "notificaciones")
 * y quedan guardados; abrir uno lo marca como leído y lleva al requerimiento.
 */
export default function Notificaciones({
  claseBoton,
  conEtiqueta = false,
  etiquetaClase = "",
  onAbrir,
}: {
  claseBoton: string;
  conEtiqueta?: boolean;
  etiquetaClase?: string;
  onAbrir?: () => void;
}) {
  const navigate = useNavigate();
  const [abierto, setAbierto] = useState(false);
  const bandeja = useDatos("notificaciones", listarNotificaciones);
  const noLeidas = bandeja.datos?.noLeidas ?? 0;
  const items = bandeja.datos?.items ?? [];

  async function abrir(n: Notificacion) {
    setAbierto(false);
    if (n.requestId) navigate(`/requerimientos?req=${n.requestId}`);
    if (!n.leida) {
      const res = await marcarNotificacionLeida(n.id);
      if (res.ok && res.result) bandeja.mutar(res.result);
    }
  }

  async function leerTodas() {
    const res = await marcarTodasLeidas();
    if (res.ok && res.result) bandeja.mutar(res.result);
  }

  return (
    <>
      <button
        type="button"
        title={noLeidas > 0 ? `Notificaciones (${noLeidas} sin leer)` : "Notificaciones"}
        aria-label={noLeidas > 0 ? `Notificaciones, ${noLeidas} sin leer` : "Notificaciones"}
        data-tutorial="menu-notificaciones"
        onClick={() => {
          onAbrir?.();
          setAbierto(true);
          void bandeja.recargar();
        }}
        className={claseBoton}
      >
        <span className="relative shrink-0">
          <Bell className="size-4.5" strokeWidth={2.1} />
          {noLeidas > 0 && (
            <span className="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 font-mono text-[0.5625rem] font-semibold tabular-nums text-white">
              {noLeidas > 9 ? "9+" : noLeidas}
            </span>
          )}
        </span>
        {conEtiqueta && <span className={etiquetaClase}>Notificaciones</span>}
      </button>

      {abierto && (
        <PanelLateral
          titulo="Notificaciones"
          subtitulo={noLeidas > 0 ? `${noLeidas} sin leer` : "Todo leído"}
          ancho="max-w-md"
          onCerrar={() => setAbierto(false)}
        >
          {noLeidas > 0 && (
            <div className="mb-3 flex justify-end">
              <Button size="sm" variant="ghost" icon={<CheckCheck className="size-4" />} onClick={() => void leerTodas()}>
                Marcar todo como leído
              </Button>
            </div>
          )}
          {bandeja.cargando ? (
            <div className="flex flex-col gap-3">
              {[0, 1, 2, 3].map((k) => (
                <Skeleton key={k} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <EmptyState icon={Bell} title="Sin notificaciones" />
          ) : (
            <ul className="flex flex-col divide-y divide-slate-100 rounded-lg border border-slate-200">
              {items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => void abrir(n)}
                    className={`flex w-full gap-3 px-3.5 py-3 text-left transition-colors hover:bg-slate-50 ${
                      n.leida ? "" : "bg-brand-50/40"
                    }`}
                  >
                    <span
                      aria-hidden="true"
                      className={`mt-1.5 size-2 shrink-0 rounded-full ${n.leida ? "bg-slate-200" : TONO[n.tipo]}`}
                    />
                    <span className="min-w-0 flex-1">
                      <span className={`block truncate text-sm ${n.leida ? "text-slate-600" : "font-medium text-slate-900"}`}>
                        {n.titulo}
                      </span>
                      {n.detalle && (
                        <span className="mt-0.5 line-clamp-2 block whitespace-normal text-xs text-slate-500">{n.detalle}</span>
                      )}
                      <span className="mt-1 block text-[0.6875rem] text-slate-400">{hace(n.creadaEn)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </PanelLateral>
      )}
    </>
  );
}
