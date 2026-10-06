import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { progreso, type TipoActividad } from "../api/progreso";

// Si la respuesta llega antes, no se muestra nada (evita parpadeos).
const RETRASO_MS = 250;
// Una vez visible, se queda al menos esto (evita un destello de un cuadro).
const MINIMO_VISIBLE_MS = 200;

const TEXTO: Record<TipoActividad, string> = {
  cargando: "Cargando",
  guardando: "Guardando",
  descargando: "Descargando",
};

type Fase = "oculta" | "visible" | "saliendo";

/**
 * Indicador global de peticiones en curso. La barra avanza sola hacia el 90%
 * (no sabemos cuanto falta) y completa el 100% al terminar.
 * - Guardando: capa tenue sobre la pantalla con tarjeta central; bloquea
 *   clics para que no se envie dos veces un formulario.
 * - Cargando/descargando: solo una barra fina arriba, sin bloquear. Cada
 *   pantalla ya muestra sus esqueletos; tapar todo al navegar o al abrir un
 *   detalle se sentia como una espera extra.
 */
export default function CapaCarga() {
  const actividad = useSyncExternalStore(progreso.suscribir, progreso.actividad);
  const [fase, setFase] = useState<Fase>("oculta");
  const [avance, setAvance] = useState(0);
  // El texto se congela al salir, para que no cambie mientras se desvanece.
  const [tipo, setTipo] = useState<TipoActividad>("cargando");
  const visibleDesde = useRef(0);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    const limpiar = () => {
      timers.current.forEach((t) => clearTimeout(t));
      timers.current = [];
    };
    limpiar();
    const programar = (fn: () => void, ms: number) => timers.current.push(window.setTimeout(fn, ms));

    if (actividad) {
      const mostrar = () => {
        visibleDesde.current = Date.now();
        setTipo(actividad);
        setFase("visible");
        setAvance((a) => (a >= 1 ? 0.1 : Math.max(a, 0.1)));
        // clearTimeout tambien cancela este setInterval (comparten ids en el navegador).
        timers.current.push(window.setInterval(() => setAvance((a) => a + (0.9 - a) * 0.08), 200));
      };
      // Si ya estaba en pantalla (otra peticion encadenada), sigue sin esperar.
      programar(mostrar, fase === "oculta" ? RETRASO_MS : 0);
    } else if (fase !== "oculta") {
      const restante = Math.max(0, MINIMO_VISIBLE_MS - (Date.now() - visibleDesde.current));
      programar(() => {
        setAvance(1);
        programar(() => setFase("saliendo"), 120);
        programar(() => {
          setFase("oculta");
          setAvance(0);
        }, 300);
      }, restante);
    }
    return limpiar;
    // "fase" se lee solo para decidir el retraso; no debe reiniciar el efecto.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [actividad]);

  if (fase === "oculta") return null;

  if (tipo !== "guardando") {
    return (
      <div
        role="status"
        aria-live="polite"
        aria-label={TEXTO[tipo]}
        className={`pointer-events-none fixed inset-x-0 top-0 z-60 h-0.5 transition-opacity duration-200 ${
          fase === "visible" ? "opacity-100" : "opacity-0"
        }`}
      >
        <div
          className="h-full origin-left bg-brand-600 transition-transform duration-300 ease-(--ease-suave)"
          style={{ transform: `scaleX(${avance})` }}
        />
      </div>
    );
  }

  const texto = TEXTO[tipo];
  return (
    <div
      role="status"
      aria-live="polite"
      className={`fixed inset-0 z-60 flex items-center justify-center bg-slate-100/60 backdrop-blur-[3px] transition-opacity duration-150 ${
        fase === "visible" ? "animate-capa-in opacity-100" : "opacity-0"
      }`}
    >
      <div className="w-72 rounded-xl border border-slate-200 bg-white px-6 py-5 shadow-flotante">
        <div className="mb-3 flex items-baseline justify-between">
          <span className="text-sm font-medium text-slate-900">{texto}…</span>
          <span className="font-mono text-xs tabular-nums text-slate-400">{Math.round(avance * 100)}%</span>
        </div>
        <div className="h-1 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full origin-left rounded-full bg-brand-600 transition-transform duration-300 ease-(--ease-suave)"
            style={{ transform: `scaleX(${avance})` }}
          />
        </div>
      </div>
    </div>
  );
}
