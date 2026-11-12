import { useEffect, useMemo, useRef, useState } from "react";
import type { OcrLinea } from "../../api/types";
import { inputClass } from "../ui/Field";
import Flotante from "../ui/Flotante";

function normalizar(t: string): string {
  return t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/**
 * Revision asistida (DEC-L053): que linea del documento del proveedor
 * corresponde a este producto. Elegirla propone el precio y, al guardar, el
 * sistema recuerda como llama ese proveedor al producto.
 */
export default function LineaDocumento({
  lineas,
  indice,
  reconocida,
  producto,
  onElegir,
}: {
  lineas: OcrLinea[];
  indice: number | null;
  /** Vino de lo aprendido de este proveedor. */
  reconocida: boolean;
  producto: string;
  onElegir: (linea: OcrLinea | null) => void;
}) {
  const [abierto, setAbierto] = useState(false);
  const [texto, setTexto] = useState("");
  const boton = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const elegida = lineas.find((l) => l.indice === indice) ?? null;

  const opciones = useMemo(() => {
    const t = normalizar(texto.trim());
    return lineas.filter((l) => !t || normalizar(l.texto).includes(t));
  }, [lineas, texto]);

  useEffect(() => {
    if (!abierto) return;
    function fuera(e: MouseEvent) {
      const n = e.target as Node;
      if (!boton.current?.contains(n) && !panel.current?.contains(n)) setAbierto(false);
    }
    function tecla(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.stopPropagation();
        setAbierto(false);
      }
    }
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", tecla, true);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", tecla, true);
    };
  }, [abierto]);

  function elegir(l: OcrLinea | null) {
    onElegir(l);
    setAbierto(false);
    setTexto("");
  }

  return (
    <div className="mt-1 flex min-w-0 items-center gap-1.5 text-xs">
      {elegida ? (
        <>
          <span className="min-w-0 truncate text-slate-500" title={elegida.texto}>
            En la cotización: «{elegida.descripcion}»
          </span>
          {reconocida && (
            <span className="shrink-0 rounded-full bg-emerald-50 px-1.5 py-px font-medium text-emerald-700">Reconocido</span>
          )}
        </>
      ) : null}
      <button
        ref={boton}
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        aria-label={`Elegir la línea de ${producto} en el documento`}
        className="shrink-0 font-medium text-brand-600 hover:text-brand-700"
      >
        {elegida ? "Cambiar" : "Elegir línea del documento"}
      </button>
      {abierto && (
        <Flotante ancla={boton} panelRef={panel} altoMaximo={320} className="w-80 max-w-[calc(100vw-32px)] rounded-lg border border-slate-200 bg-white shadow-flotante">
          <div className="flex flex-col">
            {lineas.length > 6 && (
              <div className="border-b border-slate-100 p-2">
                <input
                  autoFocus
                  value={texto}
                  onChange={(e) => setTexto(e.target.value)}
                  placeholder="Buscar en el documento…"
                  className={`${inputClass} w-full py-1.5 text-sm`}
                />
              </div>
            )}
            <ul role="listbox" className="max-h-64 overflow-y-auto py-1">
              {opciones.map((l) => (
                <li key={l.indice}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={l.indice === indice}
                    onClick={() => elegir(l)}
                    className={`block w-full px-3 py-2 text-left text-xs hover:bg-slate-50 ${
                      l.indice === indice ? "bg-brand-50 text-brand-800" : "text-slate-700"
                    }`}
                  >
                    {l.texto}
                  </button>
                </li>
              ))}
              {opciones.length === 0 && <li className="px-3 py-2 text-xs text-slate-400">Sin coincidencias.</li>}
              <li className="border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => elegir(null)}
                  className="block w-full px-3 py-2 text-left text-xs text-slate-500 hover:bg-slate-50"
                >
                  Ninguna (no cotiza este producto)
                </button>
              </li>
            </ul>
          </div>
        </Flotante>
      )}
    </div>
  );
}
