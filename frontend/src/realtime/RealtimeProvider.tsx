import { Client } from "@stomp/stompjs";
import { createContext, useContext, useEffect, useRef, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";
import type { AprobacionPendiente, Requerimiento } from "../api/types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;

type Listener<T> = (payload: T) => void;

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

interface RealtimeContextValue {
  onNuevaAprobacion: (cb: Listener<AprobacionPendiente>) => () => void;
  onMiRequerimientoDecidido: (cb: Listener<Requerimiento>) => () => void;
}

const RealtimeContext = createContext<RealtimeContextValue | undefined>(undefined);

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

    const esAprobador = tieneAlgunRol("APROBADOR", "ADMIN");

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

export function useRealtime() {
  const ctx = useContext(RealtimeContext);
  if (!ctx) throw new Error("useRealtime debe usarse dentro de RealtimeProvider.");
  return ctx;
}
