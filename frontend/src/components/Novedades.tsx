import { lazy, Suspense, useState } from "react";
import { VERSION_ACTUAL } from "../version";

// El historial completo (novedades.ts) se descarga recien al abrir la ventana.
const cargarVentana = () => import("./VentanaNovedades");
const VentanaNovedades = lazy(cargarVentana);

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

/**
 * Pie discreto al final de cada pantalla con la version actual: abre el
 * historial de novedades. Muestra un punto mientras el usuario no haya visto
 * la version actual. No va en el menu: no es un modulo.
 */
export default function PieVersion() {
  const [abierto, setAbierto] = useState(false);
  const [nueva, setNueva] = useState(() => leerVista() !== VERSION_ACTUAL);

  function abrir() {
    marcarVista();
    setNueva(false);
    setAbierto(true);
  }

  return (
    <>
      <footer className="flex justify-center px-4 pb-6 pt-2">
        <button
          type="button"
          onClick={abrir}
          onPointerEnter={() => void cargarVentana()}
          onFocus={() => void cargarVentana()}
          className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[11px] text-slate-400 transition-colors hover:bg-slate-200/50 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
        >
          {nueva && <span aria-hidden="true" className="size-1.5 rounded-full bg-brand-600" />}
          <span className="font-mono">v{VERSION_ACTUAL}</span>
          <span aria-hidden="true">·</span>
          <span>{nueva ? "Ver novedades" : "Novedades"}</span>
        </button>
      </footer>
      {abierto && (
        <Suspense fallback={null}>
          <VentanaNovedades onCerrar={() => setAbierto(false)} />
        </Suspense>
      )}
    </>
  );
}
