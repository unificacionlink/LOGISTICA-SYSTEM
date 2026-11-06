import { Client } from "@stomp/stompjs";
import { useEffect, useRef, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import { API_URL } from "../api/http";
import type { AprobacionPendiente, Requerimiento } from "../api/types";
import { RealtimeContext, type Listener, type RealtimeContextValue } from "./useRealtime";

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
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!usuario) {
      clientRef.current?.deactivate();
      clientRef.current = null;
      return;
    }

    const esAprobador = tieneAlgunRol("APROBADOR", "GERENCIA", "ADMIN");

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 4000,
      onConnect: () => {
        client.subscribe("/user/queue/mis-requerimientos", (msg) => {
          busDecision.emit(JSON.parse(msg.body) as Requerimiento);
        });
        if (esAprobador) {
          client.subscribe("/topic/aprobaciones", (msg) => {
            busAprobacion.emit(JSON.parse(msg.body) as AprobacionPendiente);
          });
          // REQ de un CECO con aprobador asignado: solo le llega a ese usuario (DEC-L030).
          client.subscribe("/user/queue/aprobaciones", (msg) => {
            busAprobacion.emit(JSON.parse(msg.body) as AprobacionPendiente);
          });
        }
      },
    });
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario?.id]);

  const value: RealtimeContextValue = {
    onNuevaAprobacion: busAprobacion.subscribe,
    onMiRequerimientoDecidido: busDecision.subscribe,
  };

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}
