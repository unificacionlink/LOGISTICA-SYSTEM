import { useEffect } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, X } from "lucide-react";
import { NOVEDADES, type VersionApp } from "../novedades";
import { VERSION_ACTUAL } from "../version";

/** Historial de versiones; se carga bajo demanda desde el pie (Novedades.tsx). */
export default function VentanaNovedades({ onCerrar }: { onCerrar: () => void }) {
  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      anterior?.focus?.();
    };
    // Solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [actual, ...anteriores] = NOVEDADES;

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="novedades-titulo"
        className="flex max-h-[85vh] w-full max-w-lg flex-col rounded-xl border border-slate-200 bg-white shadow-flotante"
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 id="novedades-titulo" className="font-semibold text-slate-900">
              Novedades
            </h2>
            <p className="text-xs text-slate-500">Versión actual {VERSION_ACTUAL}</p>
          </div>
          <button
            type="button"
            autoFocus
            onClick={onCerrar}
            aria-label="Cerrar"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <X className="size-4.5" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <Version v={actual} />
          {anteriores.length > 0 && (
            <div className="mt-5 border-t border-slate-100 pt-3">
              <p className="rotulo mb-1">Versiones anteriores</p>
              {anteriores.map((v) => (
                <details key={v.version} className="group border-b border-slate-100 last:border-b-0">
                  <summary className="flex cursor-pointer list-none items-center gap-2 py-2.5 text-sm">
                    <ChevronDown className="size-3.5 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
                    <span className="font-mono text-xs text-slate-500">{v.version}</span>
                    <span className="min-w-0 flex-1 truncate text-slate-700">{v.titulo}</span>
                  </summary>
                  <ListaCambios cambios={v.cambios} className="pb-3 pl-5" />
                </details>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}

function Version({ v }: { v: VersionApp }) {
  return (
    <section>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-medium text-slate-900">{v.titulo}</h3>
        <span className="font-mono text-xs text-slate-400">{v.version}</span>
      </div>
      <ListaCambios cambios={v.cambios} className="mt-2" />
    </section>
  );
}

function ListaCambios({ cambios, className = "" }: { cambios: string[]; className?: string }) {
  return (
    <ul className={`flex list-disc flex-col gap-1.5 pl-4 text-sm text-slate-600 marker:text-slate-300 ${className}`}>
      {cambios.map((c) => (
        <li key={c}>{c}</li>
      ))}
    </ul>
  );
}
