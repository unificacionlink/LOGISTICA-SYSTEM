import { useMemo, useRef, useState } from "react";
import { ChevronsUpDown } from "lucide-react";
import type { Ceco } from "../api/types";
import { buscador } from "./busqueda";
import { EMPRESAS, nombreEmpresa } from "./empresas";
import { inputClass } from "./ui/Field";
import Flotante from "./ui/Flotante";

function etiqueta(c: Ceco): string {
  return c.code ? `${c.code} — ${c.name}` : c.name;
}

/**
 * Centro de costo escribiendo el codigo o el nombre (lista larga y con nombres
 * parecidos entre empresas: combobox en vez de select). Cada opcion dice su empresa.
 */
export default function CecoCombobox({
  cecos,
  valor,
  onSeleccionar,
  vacio,
  id,
}: {
  cecos: Ceco[];
  valor: string;
  onSeleccionar: (cecoId: string) => void;
  /** Texto de la opcion "sin CECO"; si no se indica, el CECO es obligatorio. */
  vacio?: string;
  id?: string;
}) {
  const [texto, setTexto] = useState("");
  const caja = useRef<HTMLDivElement>(null);
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const elegido = cecos.find((c) => c.cecoId === valor) ?? null;
  const variasEmpresas = new Set(cecos.map((c) => c.empresaCode)).size > 1;

  const opciones = useMemo(() => {
    const coincide = buscador(texto);
    const orden = (code: string) => EMPRESAS.findIndex((e) => e.code === code);
    return cecos
      .filter((c) => coincide(`${c.code} ${c.name} ${nombreEmpresa(c.empresaCode)}`))
      .sort((a, b) => orden(a.empresaCode) - orden(b.empresaCode) || a.code.localeCompare(b.code));
  }, [cecos, texto]);

  function elegir(cecoId: string) {
    onSeleccionar(cecoId);
    setTexto("");
    setAbierto(false);
  }

  const mostrado = elegido
    ? `${etiqueta(elegido)}${variasEmpresas ? ` · ${nombreEmpresa(elegido.empresaCode)}` : ""}`
    : "";

  return (
    <div ref={caja} className="relative">
      <input
        id={id}
        role="combobox"
        aria-expanded={abierto}
        aria-autocomplete="list"
        value={abierto ? texto : mostrado}
        placeholder={vacio ?? "Escribe el código o el nombre del CECO"}
        onFocus={() => {
          setAbierto(true);
          setActivo(0);
        }}
        onBlur={() => setTimeout(() => setAbierto(false), 120)}
        onChange={(e) => {
          setTexto(e.target.value);
          setActivo(0);
          setAbierto(true);
        }}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setActivo((a) => Math.min(a + 1, opciones.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActivo((a) => Math.max(a - 1, 0));
          } else if (e.key === "Enter" && abierto && opciones[activo]) {
            e.preventDefault();
            elegir(opciones[activo].cecoId);
          } else if (e.key === "Escape") {
            setAbierto(false);
          }
        }}
        className={`${inputClass} w-full pr-9`}
      />
      <ChevronsUpDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      {abierto && (
        <Flotante ancla={caja} anchoAncla altoMaximo={288} className="rounded-md border border-slate-200 bg-white py-1 shadow-flotante">
          <ul role="listbox">
            {vacio && !texto.trim() && (
              <li
                role="option"
                aria-selected={!valor}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir("");
                }}
                className="cursor-pointer px-3 py-2 text-sm text-slate-500 hover:bg-slate-100"
              >
                {vacio}
              </li>
            )}
            {opciones.length === 0 ? (
              <li className="px-3 py-2 text-sm text-slate-500">Sin coincidencias</li>
            ) : (
              opciones.map((c, i) => (
                <li
                  key={c.cecoId}
                  role="option"
                  aria-selected={c.cecoId === valor}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    elegir(c.cecoId);
                  }}
                  onMouseEnter={() => setActivo(i)}
                  className={`flex cursor-pointer items-center gap-2 px-3 py-2 text-sm ${i === activo ? "bg-slate-100" : ""}`}
                >
                  <span className="min-w-0 flex-1 truncate text-slate-800" title={etiqueta(c)}>
                    <span className="font-mono text-xs text-slate-500">{c.code}</span> {c.name}
                  </span>
                  {variasEmpresas && <span className="shrink-0 text-xs text-slate-500">{nombreEmpresa(c.empresaCode)}</span>}
                </li>
              ))
            )}
          </ul>
        </Flotante>
      )}
    </div>
  );
}
