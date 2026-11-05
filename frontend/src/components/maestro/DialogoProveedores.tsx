import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Search } from "lucide-react";
import type { Proveedor } from "../../api/types";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";

/**
 * Proveedores habilitados de un producto (DEC-L039): la solicitud de
 * cotizacion del consolidado va a todos ellos. `onGuardar` devuelve el mensaje
 * de error o null si se guardo.
 */
export default function DialogoProveedores({
  titulo,
  proveedores,
  seleccionInicial,
  onGuardar,
  onCerrar,
}: {
  titulo: string;
  proveedores: Proveedor[];
  seleccionInicial: string[];
  onGuardar: (supplierIds: string[]) => Promise<string | null>;
  onCerrar: () => void;
}) {
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set(seleccionInicial));
  const [busqueda, setBusqueda] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

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

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    // Los ya elegidos primero, para verlos sin buscar.
    return proveedores
      .filter((p) => !q || p.legalName.toLowerCase().includes(q) || p.ruc.includes(q))
      .sort((a, b) => Number(seleccion.has(b.supplierId)) - Number(seleccion.has(a.supplierId)));
    // seleccion solo ordena al abrir y al buscar, no en cada clic.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [proveedores, busqueda]);

  function alternar(id: string) {
    setSeleccion((prev) => {
      const s = new Set(prev);
      if (s.has(id)) s.delete(id);
      else s.add(id);
      return s;
    });
  }

  async function guardar(e: React.FormEvent) {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    const mensaje = await onGuardar(Array.from(seleccion));
    setGuardando(false);
    if (mensaje) setError(mensaje);
    else onCerrar();
  }

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialogo-proveedores-titulo"
        onSubmit={guardar}
        className="flex max-h-[80vh] w-full max-w-md flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-flotante"
      >
        <h2 id="dialogo-proveedores-titulo" className="font-semibold text-slate-900">
          {titulo}
        </h2>
        <div className="relative mt-4">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            autoFocus
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por razón social o RUC"
            className={`${inputClass} w-full pl-9`}
          />
        </div>
        <ul className="mt-3 min-h-0 flex-1 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
          {visibles.length === 0 ? (
            <li className="px-3 py-4 text-center text-sm text-slate-400">Sin coincidencias</li>
          ) : (
            visibles.map((p) => (
              <li key={p.supplierId}>
                <label className="flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-slate-50">
                  <input
                    type="checkbox"
                    checked={seleccion.has(p.supplierId)}
                    onChange={() => alternar(p.supplierId)}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-slate-800">{p.legalName}</span>
                    <span className="font-mono text-xs text-slate-400">{p.ruc}</span>
                  </span>
                </label>
              </li>
            ))
          )}
        </ul>
        {error && <p className="mt-2 text-xs text-rose-600">{error}</p>}
        <div className="mt-4 flex items-center justify-between gap-2">
          <span className="text-xs text-slate-500">{seleccion.size} seleccionado(s)</span>
          <div className="flex gap-2">
            <Button variant="ghost" onClick={onCerrar}>
              Cancelar
            </Button>
            <Button type="submit" loading={guardando}>
              Guardar
            </Button>
          </div>
        </div>
      </form>
    </div>,
    document.body,
  );
}
