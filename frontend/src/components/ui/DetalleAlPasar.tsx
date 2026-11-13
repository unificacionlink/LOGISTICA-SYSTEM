import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import Flotante from "./Flotante";

const RETARDO_CIERRE = 120;

/**
 * Muestra un resumen corto y, al pasar el mouse (o con el foco del teclado),
 * una tarjeta flotante con el detalle. En pantallas tactiles se abre con un
 * toque. Sirve para tablas que solo exponen lo esencial (ley de Hick).
 */
export default function DetalleAlPasar({
  children,
  detalle,
  className = "",
  alinear = "inicio",
}: {
  /** Lo que se ve en la celda (una linea). */
  children: ReactNode;
  /** Contenido de la tarjeta. */
  detalle: ReactNode;
  className?: string;
  alinear?: "inicio" | "fin";
}) {
  const ancla = useRef<HTMLSpanElement>(null);
  const cierre = useRef<number | undefined>(undefined);
  const [abierto, setAbierto] = useState(false);
  const id = useId();

  useEffect(() => () => window.clearTimeout(cierre.current), []);

  function abrir() {
    window.clearTimeout(cierre.current);
    setAbierto(true);
  }

  function cerrarLuego() {
    window.clearTimeout(cierre.current);
    cierre.current = window.setTimeout(() => setAbierto(false), RETARDO_CIERRE);
  }

  return (
    <>
      <span
        ref={ancla}
        tabIndex={0}
        aria-describedby={abierto ? id : undefined}
        onMouseEnter={abrir}
        onMouseLeave={cerrarLuego}
        onFocus={abrir}
        onBlur={cerrarLuego}
        onClick={() => setAbierto((v) => !v)}
        onKeyDown={(e) => e.key === "Escape" && setAbierto(false)}
        className={`cursor-default rounded-sm underline decoration-slate-300 decoration-dotted underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 ${className}`}
      >
        {children}
      </span>
      {abierto && (
        <Flotante ancla={ancla} alinear={alinear} altoMaximo={320}>
          <div
            id={id}
            role="tooltip"
            onMouseEnter={abrir}
            onMouseLeave={cerrarLuego}
            className="animate-capa-in max-w-[26rem] rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-600 shadow-flotante"
          >
            {detalle}
          </div>
        </Flotante>
      )}
    </>
  );
}
