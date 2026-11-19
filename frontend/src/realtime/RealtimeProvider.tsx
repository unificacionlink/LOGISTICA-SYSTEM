import type { Client } from "@stomp/stompjs";
import { useEffect, useMemo, useRef, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import { invalidar } from "../api/cache";
import { API_URL } from "../api/http";
import type { AprobacionPendiente, Orden, Requerimiento } from "../api/types";
import {
  RealtimeContext,
  type AvisoCajaChica,
  type CotizacionPorEnlace,
  type Listener,
  type RealtimeContextValue,
} from "./useRealtime";

const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;

function crearBus<T>() {
  const listeners = new Set<Listener<T>>();
  return {
    emit(payload: T) {
      listeners.forEach((l) => l(payload));
    },
    subscribe(listener: Listener<T>) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  };
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { usuario, tieneAlgunRol } = useAuth();
  const busAprobacion = useRef(crearBus<AprobacionPendiente>()).current;
  const busDecision = useRef(crearBus<Requerimiento>()).current;
  const busCotizacion = useRef(crearBus<CotizacionPorEnlace>()).current;
  const busCajaChica = useRef(crearBus<AvisoCajaChica>()).current;
  const busOrden = useRef(crearBus<Orden>()).current;
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!usuario) {
      clientRef.current?.deactivate();
      clientRef.current = null;
      return;
    }

    const esAprobador = tieneAlgunRol("APROBADOR", "GERENCIA", "ADMIN");
    const esLogistica = tieneAlgunRol("LOGISTICA", "ADMIN");
    const decideOrdenes = tieneAlgunRol("GERENCIA", "LOGISTICA", "ADMIN");
    let cancelado = false;
    let client: Client | null = null;

    // La libreria STOMP (~22 KB) se descarga recien con sesion: el login no la necesita.
    import("@stomp/stompjs").then(({ Client }) => {
      if (cancelado) return;
      const c = new Client({
        brokerURL: WS_URL,
        reconnectDelay: 4000,
        onConnect: () => {
          // Cada aviso marca como viejas las listas que cambia: si alguna esta en
          // pantalla se actualiza sola; si no, se pide al volver (DEC-L073).
          c.subscribe("/user/queue/mis-requerimientos", (msg) => {
            const req = JSON.parse(msg.body) as Requerimiento;
            invalidar("bandeja", "tablero", `avance:${req.id}`, `tracking:${req.id}`);
            busDecision.emit(req);
          });
          c.subscribe("/user/queue/caja-chica", (msg) => {
            invalidar("cajachica-lista", "compras-necesidades");
            busCajaChica.emit(JSON.parse(msg.body) as AvisoCajaChica);
          });
          if (esLogistica) {
            c.subscribe("/user/queue/cotizaciones", (msg) => {
              invalidar("compras-rfqs", "compras-consolidados");
              busCotizacion.emit(JSON.parse(msg.body) as CotizacionPorEnlace);
            });
          }
          if (decideOrdenes) {
            c.subscribe("/topic/ordenes-compra", (msg) => {
              invalidar("compras-ordenes", "compras-por-emitir", "almacen-recibir");
              busOrden.emit(JSON.parse(msg.body) as Orden);
            });
          }
          if (esAprobador) {
            c.subscribe("/topic/aprobaciones", (msg) => {
              invalidar("bandeja", "tablero");
              busAprobacion.emit(JSON.parse(msg.body) as AprobacionPendiente);
            });
            // REQ de un CECO con aprobador asignado: solo le llega a ese usuario (DEC-L030).
            c.subscribe("/user/queue/aprobaciones", (msg) => {
              invalidar("bandeja", "tablero");
              busAprobacion.emit(JSON.parse(msg.body) as AprobacionPendiente);
            });
          }
        },
      });
      c.activate();
      client = c;
      clientRef.current = c;
    });

    return () => {
      cancelado = true;
      client?.deactivate();
      clientRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario?.id]);

  // Los buses no cambian: el valor del contexto tampoco (no re-renderiza a quien lo usa).
  const value = useMemo<RealtimeContextValue>(
    () => ({
      onNuevaAprobacion: busAprobacion.subscribe,
      onMiRequerimientoDecidido: busDecision.subscribe,
      onCotizacionPorEnlace: busCotizacion.subscribe,
      onCajaChica: busCajaChica.subscribe,
      onOrdenDeCompra: busOrden.subscribe,
    }),
    [busAprobacion, busDecision, busCotizacion, busCajaChica, busOrden],
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}
