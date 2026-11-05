import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, Sparkles, X } from "lucide-react";
import { NOVEDADES, VERSION_ACTUAL, type VersionApp } from "../novedades";

// Ultima version que este usuario abrio en este navegador (solo para el aviso).
const CLAVE_VISTA = "logistica.novedades.vista";

function leerVista(): string | null {
  try {
    return localStorage.getItem(CLAVE_VISTA);
  } catch {
    return null;
  }
}

function marcarVista() {
  try {
    localStorage.setItem(CLAVE_VISTA, VERSION_ACTUAL);
  } catch {
    // Sin almacenamiento (modo privado): el aviso simplemente vuelve a aparecer.
  }
}

function fecha(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString("es-PE", { day: "numeric", month: "short", year: "numeric" });
}

/**
 * Boton del menu con la version actual; abre el historial de novedades.
 * Muestra un punto mientras el usuario no haya visto la version actual.
 */
export default function BotonNovedades({ etiquetaClase, onAbrir }: { etiquetaClase: string; onAbrir?: () => void }) {
  const [abierto, setAbierto] = useState(false);
  const [nueva, setNueva] = useState(() => leerVista() !== VERSION_ACTUAL);

  function abrir() {
    marcarVista();
    setNueva(false);
    setAbierto(true);
    onAbrir?.();
  }

  return (
    <>
      <button
        type="button"
        title={`Novedades · versión ${VERSION_ACTUAL}`}
        onClick={abrir}
        className="flex h-9 items-center gap-3 rounded-md px-[11px] text-[13px] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
      >
        <span className="relative shrink-0">
          <Sparkles className="size-4.5" strokeWidth={2.1} />
          {nueva && (
            <>
              <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 size-2 rounded-full bg-brand-600 ring-2 ring-slate-50" />
              <span className="sr-only">Hay novedades</span>
            </>
          )}
        </span>
        <span className={`flex flex-1 items-baseline justify-between gap-2 ${etiquetaClase}`}>
          Novedades
          <span className="font-mono text-[10.5px] text-slate-400">v{VERSION_ACTUAL}</span>
        </span>
      </button>
      {abierto && <VentanaNovedades onCerrar={() => setAbierto(false)} />}
    </>
  );
}

function VentanaNovedades({ onCerrar }: { onCerrar: () => void }) {
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
                    <span className="shrink-0 text-xs text-slate-400">{fecha(v.fecha)}</span>
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
        <span className="text-xs text-slate-400">
          <span className="font-mono">{v.version}</span> · {fecha(v.fecha)}
        </span>
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
