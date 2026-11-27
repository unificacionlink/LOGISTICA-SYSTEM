import { lazy, Suspense, useCallback, useEffect, useState } from "react";
import { Bug, CheckCircle2 } from "lucide-react";
import { conRecarga } from "../../paginas";

// Diferido (DEC-L051): el esqueleto y el formulario bajan recien al primer clic.
const ModoReporte = lazy(conRecarga(() => import("./ModoReporte")));

/**
 * Boton fijo para reportar una observacion desde cualquier pantalla
 * (DEC-L104). Al tocarlo la pantalla se vuelve un esqueleto donde se senala
 * la zona.
 */
export default function BotonReporte() {
  const [activo, setActivo] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  const salir = useCallback((mensaje?: string) => {
    setActivo(false);
    if (mensaje) setAviso(mensaje);
  }, []);

  useEffect(() => {
    if (!aviso) return;
    const t = setTimeout(() => setAviso(null), 5000);
    return () => clearTimeout(t);
  }, [aviso]);

  return (
    <>
      {!activo && (
        <div data-esqueleto-ignorar className="fixed bottom-4 right-4 z-40 flex items-center gap-2">
          {aviso && (
            <span
              role="status"
              className="animate-toast-in flex items-center gap-1.5 rounded-full border border-emerald-200 bg-white px-3 py-1.5 text-xs font-medium text-emerald-700 shadow-flotante"
            >
              <CheckCircle2 className="size-4" />
              {aviso}
            </span>
          )}
          <button
            type="button"
            title="Reportar una observación de esta pantalla"
            data-tutorial="boton-reporte"
            onClick={() => {
              setAviso(null);
              setActivo(true);
            }}
            className="flex h-10 items-center gap-2 rounded-full border border-slate-200 bg-white px-3 text-[0.8125rem] font-medium text-slate-700 shadow-flotante transition-colors hover:border-brand-200 hover:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          >
            <Bug className="size-4.5 text-brand-600" strokeWidth={2.1} />
            <span className="hidden sm:inline">Reportar</span>
          </button>
        </div>
      )}
      {activo && (
        <Suspense fallback={null}>
          <ModoReporte onSalir={salir} />
        </Suspense>
      )}
    </>
  );
}
