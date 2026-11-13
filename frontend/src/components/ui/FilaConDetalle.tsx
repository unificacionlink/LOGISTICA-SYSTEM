import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

const RETARDO = 250;
const DESPLAZAMIENTO = 14;
const MARGEN = 8;

/**
 * Fila de tabla que muestra su detalle en una tarjeta junto al cursor al
 * dejar el mouse en cualquier parte de ella (DEC-L058). La tabla expone solo
 * lo esencial y el resto va aqui. La tarjeta no recibe clics (no tapa la fila
 * siguiente ni los controles) y se cierra al salir de la fila o al hacer scroll.
 * Lo que se pueda hacer con el detalle debe estar tambien en la pantalla de la
 * fila: en celulares no hay mouse.
 */
export default function FilaConDetalle({
  detalle,
  children,
  className = "",
}: {
  detalle: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  const [visible, setVisible] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  const raton = useRef({ x: 0, y: 0 });
  const panel = useRef<HTMLDivElement>(null);

  function ubicar() {
    const p = panel.current;
    if (!p) return;
    const { x, y } = raton.current;
    const ancho = p.offsetWidth;
    const alto = p.offsetHeight;
    const anchoVista = document.documentElement.clientWidth;
    const altoVista = window.innerHeight;
    let left = x + DESPLAZAMIENTO;
    if (left + ancho > anchoVista - MARGEN) left = x - ancho - DESPLAZAMIENTO;
    let top = y + DESPLAZAMIENTO;
    if (top + alto > altoVista - MARGEN) top = y - alto - DESPLAZAMIENTO;
    p.style.left = `${Math.max(MARGEN, left)}px`;
    p.style.top = `${Math.max(MARGEN, top)}px`;
    p.style.visibility = "visible";
  }

  useLayoutEffect(() => {
    if (visible) ubicar();
  }, [visible]);

  useEffect(() => {
    if (!visible) return;
    const cerrar = () => setVisible(false);
    window.addEventListener("scroll", cerrar, true);
    return () => window.removeEventListener("scroll", cerrar, true);
  }, [visible]);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  return (
    <tr
      className={className}
      onMouseEnter={(e) => {
        raton.current = { x: e.clientX, y: e.clientY };
        window.clearTimeout(timer.current);
        timer.current = window.setTimeout(() => setVisible(true), RETARDO);
      }}
      onMouseMove={(e) => {
        raton.current = { x: e.clientX, y: e.clientY };
        if (visible) ubicar();
      }}
      onMouseLeave={() => {
        window.clearTimeout(timer.current);
        setVisible(false);
      }}
    >
      {children}
      {visible &&
        createPortal(
          <div
            ref={panel}
            role="tooltip"
            style={{ position: "fixed", top: 0, left: 0, visibility: "hidden" }}
            className="pointer-events-none z-[70] w-max max-w-[26rem] rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-600 shadow-flotante"
          >
            {detalle}
          </div>,
          document.body,
        )}
    </tr>
  );
}
