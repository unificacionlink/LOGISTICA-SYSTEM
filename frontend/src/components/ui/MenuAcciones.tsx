import { useEffect, useRef, useState } from "react";
import { MoreHorizontal } from "lucide-react";
import Flotante from "./Flotante";

export interface AccionMenu {
  label: string;
  onClick: () => void;
  peligro?: boolean;
  disabled?: boolean;
}

/**
 * Menu "⋯" para acciones ocasionales de una pantalla (ley de Hick): la accion
 * principal queda a la vista y el resto se agrupa aqui.
 */
export default function MenuAcciones({ acciones, etiqueta = "Más acciones" }: { acciones: AccionMenu[]; etiqueta?: string }) {
  const [abierto, setAbierto] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const boton = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    const fuera = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!ref.current?.contains(t) && !panel.current?.contains(t)) setAbierto(false);
    };
    const escape = (e: KeyboardEvent) => e.key === "Escape" && setAbierto(false);
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", escape);
    };
  }, [abierto]);

  if (acciones.length === 0) return null;

  return (
    <div ref={ref} className="relative">
      <button
        ref={boton}
        type="button"
        aria-label={etiqueta}
        aria-haspopup="menu"
        aria-expanded={abierto}
        onClick={() => setAbierto((v) => !v)}
        className="flex size-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 transition-colors hover:border-slate-300 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
      >
        <MoreHorizontal className="size-4" />
      </button>
      {abierto && (
        <Flotante
          ancla={boton}
          panelRef={panel}
          alinear="fin"
          role="menu"
          className="min-w-44 rounded-md border border-slate-200 bg-white py-1 shadow-flotante"
        >
          {acciones.map((a) => (
            <button
              key={a.label}
              type="button"
              role="menuitem"
              disabled={a.disabled}
              onClick={() => {
                setAbierto(false);
                a.onClick();
              }}
              className={`block w-full px-3 py-2 text-left text-sm transition-colors disabled:opacity-50 ${
                a.peligro ? "text-rose-700 hover:bg-rose-50" : "text-slate-700 hover:bg-slate-50"
              }`}
            >
              {a.label}
            </button>
          ))}
        </Flotante>
      )}
    </div>
  );
}
