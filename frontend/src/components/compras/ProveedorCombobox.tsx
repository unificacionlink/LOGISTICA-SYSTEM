import { useMemo, useRef, useState } from "react";
import { ChevronsUpDown } from "lucide-react";
import type { Proveedor } from "../../api/types";
import { inputClass } from "../ui/Field";

function normalizar(t: string): string {
  return t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Buscador de proveedor por razón social o RUC (lista larga: combobox en vez de select). */
export default function ProveedorCombobox({
  proveedores,
  valor,
  onSeleccionar,
  deshabilitado = false,
  excluir = [],
}: {
  proveedores: Proveedor[];
  valor: string | null;
  onSeleccionar: (supplierId: string) => void;
  deshabilitado?: boolean;
  /** Proveedores que ya tienen otra cotización en el comparativo. */
  excluir?: string[];
}) {
  const [texto, setTexto] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const lista = useRef<HTMLUListElement>(null);
  const elegido = proveedores.find((p) => p.supplierId === valor) ?? null;

  const opciones = useMemo(() => {
    const t = normalizar(texto.trim());
    return proveedores
      .filter((p) => p.active && !excluir.includes(p.supplierId))
      .filter((p) => !t || normalizar(p.legalName).includes(t) || p.ruc.includes(t))
      .slice(0, 8);
  }, [proveedores, texto, excluir]);

  function elegir(p: Proveedor) {
    onSeleccionar(p.supplierId);
    setTexto("");
    setAbierto(false);
  }

  return (
    <div className="relative">
      <input
        role="combobox"
        aria-expanded={abierto}
        aria-autocomplete="list"
        disabled={deshabilitado}
        value={abierto ? texto : elegido ? `${elegido.legalName} · ${elegido.ruc}` : ""}
        placeholder="Busca por razón social o RUC"
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
            elegir(opciones[activo]);
          } else if (e.key === "Escape") {
            setAbierto(false);
          }
        }}
        className={`${inputClass} w-full pr-9`}
      />
      <ChevronsUpDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      {abierto && (
        <ul
          ref={lista}
          role="listbox"
          className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-flotante"
        >
          {opciones.length === 0 ? (
            <li className="px-3 py-2 text-sm text-slate-500">Sin coincidencias</li>
          ) : (
            opciones.map((p, i) => (
              <li
                key={p.supplierId}
                role="option"
                aria-selected={p.supplierId === valor}
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(p);
                }}
                onMouseEnter={() => setActivo(i)}
                className={`cursor-pointer px-3 py-2 text-sm ${i === activo ? "bg-slate-100" : ""}`}
              >
                <p className="truncate text-slate-800">{p.legalName}</p>
                <p className="font-mono text-xs text-slate-500">{p.ruc}</p>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
