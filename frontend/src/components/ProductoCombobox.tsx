import { useMemo, useState } from "react";
import { Search, X } from "lucide-react";
import type { VarianteCatalogo } from "../api/types";
import { inputClass } from "./ui/Field";

export default function ProductoCombobox({
  variantes,
  valor,
  onSeleccionar,
}: {
  variantes: VarianteCatalogo[];
  valor: VarianteCatalogo | null;
  onSeleccionar: (v: VarianteCatalogo | null) => void;
}) {
  const [query, setQuery] = useState("");
  const [abierto, setAbierto] = useState(false);

  const resultados = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return variantes.slice(0, 30);
    return variantes.filter((v) => v.etiqueta.toLowerCase().includes(q)).slice(0, 30);
  }, [query, variantes]);

  if (valor) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-slate-300 bg-slate-50 px-3 py-2.5">
        <span className="flex-1 truncate font-mono text-xs text-slate-700">{valor.etiqueta}</span>
        <button
          type="button"
          className="shrink-0 text-slate-400 hover:text-slate-600"
          onClick={() => {
            onSeleccionar(null);
            setQuery("");
          }}
          title="Cambiar producto"
        >
          <X className="size-4" />
        </button>
      </div>
    );
  }

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      <input
        type="text"
        placeholder="Buscar por SKU o nombre…"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setAbierto(true);
        }}
        onFocus={() => setAbierto(true)}
        onBlur={() => setTimeout(() => setAbierto(false), 150)}
        className={`${inputClass} w-full pl-9`}
      />
      {abierto && resultados.length > 0 && (
        <ul className="absolute z-10 mt-1.5 max-h-56 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-flotante">
          {resultados.map((v) => (
            <li key={v.variantId}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  onSeleccionar(v);
                  setAbierto(false);
                }}
                className="block w-full rounded-md px-2.5 py-1.5 text-left text-sm text-slate-700 hover:bg-slate-100"
              >
                {v.etiqueta}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
