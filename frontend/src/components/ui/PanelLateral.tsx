import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

/**
 * Panel lateral derecho (mismo patron que la ficha de usuario en
 * Administracion): formularios y detalle sin salir de la pantalla. Se cierra
 * con Escape, con la X o con un clic en el fondo. `pie` queda fijo abajo (la
 * accion principal del panel).
 */
export default function PanelLateral({
  titulo,
  subtitulo,
  ancho = "max-w-xl",
  pie,
  onCerrar,
  children,
}: {
  titulo: ReactNode;
  subtitulo?: ReactNode;
  ancho?: string;
  pie?: ReactNode;
  onCerrar: () => void;
  children: ReactNode;
}) {
  const idTitulo = useId();
  // En una ref: si el padre pasa una funcion nueva en cada render, el efecto
  // no debe re-ejecutarse (devolveria el foco afuera mientras se escribe).
  const cerrar = useRef(onCerrar);
  useEffect(() => {
    cerrar.current = onCerrar;
  });

  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const escape = (e: KeyboardEvent) => e.key === "Escape" && cerrar.current();
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("keydown", escape);
      anterior?.focus?.();
    };
  }, []);

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        className={`flex h-full w-full ${ancho} flex-col border-l border-slate-200 bg-white shadow-flotante`}
      >
        <header className="flex items-start gap-3 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0 flex-1">
            <h2 id={idTitulo} className="truncate text-base font-semibold text-slate-900">
              {titulo}
            </h2>
            {subtitulo && <div className="mt-0.5 truncate text-sm text-slate-500">{subtitulo}</div>}
          </div>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X className="size-5" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto">{children}</div>
        {pie && <footer className="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3">{pie}</footer>}
      </aside>
    </div>,
    document.body,
  );
}

/** Bloque titulado dentro de un panel lateral. */
export function SeccionPanel({ titulo, children, accion }: { titulo: string; children: ReactNode; accion?: ReactNode }) {
  return (
    <section className="border-b border-slate-100 px-5 py-4 last:border-b-0">
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="rotulo">{titulo}</h3>
        {accion}
      </div>
      {children}
    </section>
  );
}
