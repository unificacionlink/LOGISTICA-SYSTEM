import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import type { ProductoPresupuesto } from "../../api/types";
import { inputClass } from "../ui/Field";
import { soles } from "./formato";

/** Buscador de SKU base para agregar partidas; muestra el precio de venta vigente. */
export default function BuscadorSku({
  productos,
  excluir,
  onSeleccionar,
}: {
  productos: ProductoPresupuesto[] | null;
  excluir: Set<string>;
  onSeleccionar: (p: ProductoPresupuesto) => void;
}) {
  const [query, setQuery] = useState("");
  const [abierto, setAbierto] = useState(false);

  const resultados = useMemo(() => {
    const q = query.trim().toLowerCase();
    const libres = (productos ?? []).filter((p) => !excluir.has(p.productId));
    if (!q) return libres.slice(0, 30);
    return libres.filter((p) => p.sku.toLowerCase().includes(q) || p.nombre.toLowerCase().includes(q)).slice(0, 30);
  }, [query, productos, excluir]);

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
      <input
        type="text"
        placeholder={productos ? "Agregar SKU por código o nombre…" : "Cargando catálogo…"}
        disabled={!productos}
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
        <ul className="absolute z-10 mt-1.5 max-h-64 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white p-1 shadow-flotante">
          {resultados.map((p) => (
            <li key={p.productId}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  onSeleccionar(p);
                  setQuery("");
                  setAbierto(false);
                }}
                className="flex w-full items-center gap-3 rounded-md px-2.5 py-1.5 text-left text-sm text-slate-700 hover:bg-slate-100"
              >
                <span className="shrink-0 font-mono text-xs text-slate-500">{p.sku}</span>
                <span className="min-w-0 flex-1 truncate">{p.nombre}</span>
                <span className={`shrink-0 text-xs tabular-nums ${p.precio == null ? "text-amber-600" : "text-slate-500"}`}>
                  {p.precio == null ? "Sin precio" : soles.format(p.precio)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
