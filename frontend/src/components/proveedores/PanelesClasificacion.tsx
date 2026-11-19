import { useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { obtenerClasificacion } from "../../api/maestro";
import { guardarCategoriasProveedor, guardarProductosProveedor, listarProductosDisponibles } from "../../api/proveedores";
import type { Clasificacion, FichaProveedor, ProductoDisponible } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import PanelLateral from "../ui/PanelLateral";
import { Skeleton } from "../ui/Skeleton";

const normal = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

/**
 * Categorias que atiende el proveedor, con la clasificacion del catalogo
 * (Familia = tipo de producto, Subfamilia = familia). Marcar la familia entera
 * cubre todas sus subfamilias.
 */
export function PanelCategorias({
  ficha,
  onCerrar,
  onGuardado,
}: {
  ficha: FichaProveedor;
  onCerrar: () => void;
  onGuardado: (f: FichaProveedor, mensaje: string) => void;
}) {
  const [clasificacion, setClasificacion] = useState<Clasificacion | null>(null);
  const [marcadas, setMarcadas] = useState<Set<string>>(
    new Set(ficha.categorias.map((c) => `${c.productTypeId}|${c.familyId ?? ""}`)),
  );
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    obtenerClasificacion().then((res) => {
      if (res.ok && res.result) setClasificacion(res.result);
      else setError(res.message);
    });
  }, []);

  function alternar(clave: string) {
    setMarcadas((prev) => {
      const s = new Set(prev);
      if (s.has(clave)) s.delete(clave);
      else s.add(clave);
      return s;
    });
  }

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      const items = [...marcadas].map((clave) => {
        const [productTypeId, familyId] = clave.split("|");
        return { productTypeId, familyId: familyId || null };
      });
      const res = await guardarCategoriasProveedor(ficha.proveedor.supplierId, items);
      if (res.ok && res.result) onGuardado(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo="Categorías"
      subtitulo={ficha.proveedor.legalName}
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button loading={guardando} onClick={guardar} disabled={!clasificacion}>
            Guardar
          </Button>
        </>
      }
    >
      {error && <Alert tone="danger" className="mx-5 mt-4">{error}</Alert>}
      {!clasificacion ? (
        <div className="space-y-3 px-5 py-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-8" />
          ))}
        </div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {clasificacion.tipos.map((t) => {
            const todo = marcadas.has(`${t.id}|`);
            return (
              <li key={t.id} className="px-5 py-3">
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                  <input type="checkbox" checked={todo} onChange={() => alternar(`${t.id}|`)} />
                  {t.nombre}
                  <span className="text-xs font-normal text-slate-500">toda la familia</span>
                </label>
                {!todo && (
                  <div className="mt-2 grid grid-cols-1 gap-1.5 pl-6 sm:grid-cols-2">
                    {t.familias.map((f) => (
                      <label key={f.id} className="flex items-center gap-2 text-sm text-slate-700">
                        <input type="checkbox" checked={marcadas.has(`${t.id}|${f.id}`)} onChange={() => alternar(`${t.id}|${f.id}`)} />
                        <span className="truncate" title={f.nombre}>
                          {f.nombre}
                        </span>
                      </label>
                    ))}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </PanelLateral>
  );
}

/**
 * Productos que ofrece el proveedor. Es la misma habilitacion por producto que
 * usa Compras para pedir cotizacion (DEC-L039), vista desde el proveedor.
 */
export function PanelProductos({
  ficha,
  onCerrar,
  onGuardado,
}: {
  ficha: FichaProveedor;
  onCerrar: () => void;
  onGuardado: (f: FichaProveedor, mensaje: string) => void;
}) {
  const [productos, setProductos] = useState<ProductoDisponible[] | null>(null);
  const [marcados, setMarcados] = useState<Set<string>>(new Set(ficha.productos.map((p) => p.productId)));
  const [busqueda, setBusqueda] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const iniciales = useMemo(() => new Set(ficha.productos.map((p) => p.productId)), [ficha.productos]);

  useEffect(() => {
    listarProductosDisponibles().then((res) => {
      if (res.ok && res.result) setProductos(res.result);
      else setError(res.message);
    });
  }, []);

  const visibles = useMemo(() => {
    if (!productos) return [];
    const q = normal(busqueda.trim());
    return productos
      .filter((p) => !q || normal(`${p.skuBase} ${p.nombre} ${p.tipo} ${p.familia}`).includes(q))
      .sort((a, b) => Number(iniciales.has(b.productId)) - Number(iniciales.has(a.productId)))
      .slice(0, 200);
  }, [productos, busqueda, iniciales]);

  function alternar(id: string) {
    setMarcados((prev) => {
      const s = new Set(prev);
      if (s.has(id)) s.delete(id);
      else s.add(id);
      return s;
    });
  }

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      const res = await guardarProductosProveedor(ficha.proveedor.supplierId, [...marcados]);
      if (res.ok && res.result) onGuardado(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo="Productos que ofrece"
      subtitulo={`${marcados.size} marcados`}
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button loading={guardando} onClick={guardar} disabled={!productos}>
            Guardar
          </Button>
        </>
      }
    >
      <div className="sticky top-0 z-10 border-b border-slate-100 bg-white px-5 py-3">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            className={`${inputClass} w-full pl-9`}
            placeholder="SKU, nombre o familia"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            aria-label="Buscar producto"
          />
        </div>
      </div>
      {error && <Alert tone="danger" className="mx-5 mt-4">{error}</Alert>}
      {!productos ? (
        <div className="space-y-3 px-5 py-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-7" />
          ))}
        </div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {visibles.map((p) => (
            <li key={p.productId}>
              <label className="flex cursor-pointer items-center gap-3 px-5 py-2 hover:bg-slate-50">
                <input type="checkbox" checked={marcados.has(p.productId)} onChange={() => alternar(p.productId)} />
                <span className="w-20 shrink-0 font-mono text-xs text-slate-500">{p.skuBase}</span>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-800" title={p.nombre}>
                  {p.nombre}
                </span>
                <span className="hidden max-w-[10rem] truncate text-xs text-slate-400 sm:inline">{p.familia}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
    </PanelLateral>
  );
}
