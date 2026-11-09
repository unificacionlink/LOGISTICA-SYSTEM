import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from "react";
import { createPortal } from "react-dom";

const SEPARACION = 6;
const MARGEN = 8;

/**
 * Capa flotante para menus y listas de combobox. Se monta en <body> con
 * position: fixed, asi ninguna tarjeta o tabla con overflow la recorta (antes
 * el menu "⋯" quedaba cortado al fondo de una Card). Sigue al ancla al hacer
 * scroll o cambiar el tamano de la ventana y se abre hacia arriba si abajo no
 * cabe. Va sobre los dialogos (z-50/60) para funcionar tambien dentro de ellos.
 *
 * Los clics dentro de la capa no son "fuera" del componente para React (los
 * eventos sinteticos cruzan el portal), pero si para un listener nativo del
 * document: quien cierre con mousedown en document debe revisar tambien
 * {@link panelRef}.
 */
export default function Flotante({
  ancla,
  children,
  alinear = "inicio",
  anchoAncla = false,
  className = "",
  altoMaximo,
  panelRef,
  ...resto
}: {
  ancla: RefObject<HTMLElement | null>;
  children: ReactNode;
  /** "inicio": borde izquierdo con el del ancla; "fin": borde derecho con el del ancla. */
  alinear?: "inicio" | "fin";
  /** Mismo ancho que el ancla (listas de combobox). */
  anchoAncla?: boolean;
  className?: string;
  /** Alto maximo en px (ademas del espacio libre en pantalla). */
  altoMaximo?: number;
  panelRef?: RefObject<HTMLDivElement | null>;
  role?: string;
  id?: string;
  "aria-label"?: string;
}) {
  const propio = useRef<HTMLDivElement>(null);
  const panel = panelRef ?? propio;
  const [estilo, setEstilo] = useState<CSSProperties>({ position: "fixed", visibility: "hidden", top: 0, left: 0 });

  useLayoutEffect(() => {
    function ubicar() {
      const a = ancla.current;
      const p = panel.current;
      if (!a || !p) return;
      const r = a.getBoundingClientRect();
      const anchoVista = document.documentElement.clientWidth;
      const altoVista = window.innerHeight;
      const abajo = altoVista - r.bottom - SEPARACION - MARGEN;
      const arriba = r.top - SEPARACION - MARGEN;
      const alto = p.scrollHeight;
      const haciaArriba = alto > abajo && arriba > abajo;
      const ancho = anchoAncla ? r.width : p.offsetWidth;
      let left = alinear === "fin" ? r.right - ancho : r.left;
      left = Math.max(MARGEN, Math.min(left, anchoVista - ancho - MARGEN));
      setEstilo({
        position: "fixed",
        left,
        width: anchoAncla ? r.width : undefined,
        maxHeight: Math.min(altoMaximo ?? Infinity, Math.max(120, haciaArriba ? arriba : abajo)),
        ...(haciaArriba ? { bottom: altoVista - r.top + SEPARACION } : { top: r.bottom + SEPARACION }),
      });
    }
    ubicar();
    window.addEventListener("scroll", ubicar, true);
    window.addEventListener("resize", ubicar);
    const observador = new ResizeObserver(ubicar);
    if (panel.current) observador.observe(panel.current);
    return () => {
      window.removeEventListener("scroll", ubicar, true);
      window.removeEventListener("resize", ubicar);
      observador.disconnect();
    };
  }, [ancla, panel, alinear, anchoAncla, altoMaximo]);

  return createPortal(
    <div ref={panel} style={estilo} className={`z-[70] overflow-y-auto ${className}`} {...resto}>
      {children}
    </div>,
    document.body,
  );
}
