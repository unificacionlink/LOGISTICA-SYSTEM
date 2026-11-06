import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ImageOff, Loader2, Plus, Search, Upload, X } from "lucide-react";
import { subirImagenProducto } from "../api/requerimientos";
import type { Familia, VarianteCatalogo } from "../api/types";
import FiltroFamilia from "./FiltroFamilia";
import { coincideFamilia, etiquetaFamilia } from "./familia";
import { inputClass } from "./ui/Field";

/**
 * Miniatura de producto. Con foto, un clic la amplia en un visor con el
 * nombre del producto (titulo) y un detalle opcional (SKU, familia...).
 */
export function Miniatura({
  url,
  titulo,
  detalle,
}: {
  url: string | null;
  titulo?: string;
  detalle?: string;
}) {
  const [ampliada, setAmpliada] = useState(false);

  if (!url) {
    return (
      <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-dashed border-slate-200 bg-slate-50">
        <ImageOff className="size-4 text-slate-300" />
      </div>
    );
  }
  return (
    <>
      <button
        type="button"
        onClick={() => setAmpliada(true)}
        title="Ver foto"
        className="shrink-0 rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      >
        <img
          src={url}
          alt={titulo ?? ""}
          className="size-10 cursor-zoom-in rounded-md border border-slate-200 object-cover transition hover:opacity-80"
        />
      </button>
      {ampliada && (
        <VisorFoto url={url} titulo={titulo} detalle={detalle} onCerrar={() => setAmpliada(false)} />
      )}
    </>
  );
}

function VisorFoto({
  url,
  titulo,
  detalle,
  onCerrar,
}: {
  url: string;
  titulo?: string;
  detalle?: string;
  onCerrar: () => void;
}) {
  useEffect(() => {
    const alPresionar = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCerrar();
    };
    document.addEventListener("keydown", alPresionar);
    return () => document.removeEventListener("keydown", alPresionar);
  }, [onCerrar]);

  // Portal al body: el visor debe cubrir la ventana aunque algun contenedor
  // de la pagina tenga transform (animaciones) u overflow oculto.
  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={titulo ?? "Foto del producto"}
      onClick={onCerrar}
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm"
    >
      <figure
        onClick={(e) => e.stopPropagation()}
        className="relative flex max-h-full w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-flotante"
      >
        <button
          type="button"
          onClick={onCerrar}
          title="Cerrar"
          className="absolute right-2 top-2 rounded-md border border-slate-200 bg-white/90 p-1.5 text-slate-600 hover:bg-white hover:text-slate-900"
        >
          <X className="size-4" />
        </button>
        <div className="flex min-h-0 flex-1 items-center justify-center bg-slate-50">
          <img src={url} alt={titulo ?? ""} className="max-h-[70vh] w-full object-contain" />
        </div>
        {(titulo || detalle) && (
          <figcaption className="border-t border-slate-100 px-5 py-3">
            {titulo && <p className="text-sm font-medium text-slate-800">{titulo}</p>}
            {detalle && <p className="mt-0.5 font-mono text-xs text-slate-500">{detalle}</p>}
          </figcaption>
        )}
      </figure>
    </div>,
    document.body,
  );
}

export default function BuscadorProductos({
  variantes,
  familias,
  cantidadesEnCarrito,
  onAgregar,
  puedeEditarImagenes,
  onImagenSubida,
}: {
  variantes: VarianteCatalogo[];
  familias: Familia[];
  cantidadesEnCarrito: Record<string, number>;
  onAgregar: (v: VarianteCatalogo) => void;
  puedeEditarImagenes: boolean;
  onImagenSubida: (productId: string, url: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [filtroFamilia, setFiltroFamilia] = useState("");
  const [subiendoProductId, setSubiendoProductId] = useState<string | null>(null);
  const [errorSubida, setErrorSubida] = useState<string | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement | null>(null);
  const productoParaSubirRef = useRef<string | null>(null);

  const resultados = useMemo(() => {
    const q = query.trim().toLowerCase();
    return variantes
      .filter((v) => coincideFamilia(filtroFamilia, v.tipoId, v.familiaId))
      .filter((v) => !q || v.etiqueta.toLowerCase().includes(q))
      .slice(0, 8);
  }, [query, filtroFamilia, variantes]);

  function abrirSelectorArchivo(productId: string) {
    setErrorSubida(null);
    productoParaSubirRef.current = productId;
    inputArchivoRef.current?.click();
  }

  async function onArchivoSeleccionado(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    const productId = productoParaSubirRef.current;
    e.target.value = "";
    if (!file || !productId) return;
    setSubiendoProductId(productId);
    setErrorSubida(null);
    try {
      const res = await subirImagenProducto(productId, file);
      if (!res.ok || !res.result) {
        setErrorSubida(res.message);
        return;
      }
      onImagenSubida(productId, res.result);
    } finally {
      setSubiendoProductId(null);
    }
  }

  return (
    <div>
      <input
        ref={inputArchivoRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onArchivoSeleccionado}
      />

      <div className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Buscar producto por SKU o nombre…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className={`${inputClass} w-full pl-9`}
          />
        </div>
        <FiltroFamilia familias={familias} value={filtroFamilia} onChange={setFiltroFamilia} className="w-full sm:w-64" />
      </div>

      {errorSubida && <p className="mt-2 text-xs text-rose-600">{errorSubida}</p>}

      <ul className="mt-3 flex flex-col divide-y divide-slate-100 rounded-lg border border-slate-200">
        {resultados.length === 0 ? (
          <li className="px-4 py-6 text-center text-sm text-slate-400">Sin resultados.</li>
        ) : (
          resultados.map((v) => {
            const enCarrito = cantidadesEnCarrito[v.variantId] ?? 0;
            const subiendo = subiendoProductId === v.productId;
            return (
              <li key={v.variantId} className="flex items-center gap-3 px-4 py-2.5">
                <Miniatura url={v.imagenUrl} titulo={v.nombre} detalle={`${v.codigoVariante} · ${etiquetaFamilia(v.tipoNombre, v.familiaNombre)}`} />
                <span className="min-w-0 flex-1 truncate font-mono text-xs text-slate-600">{v.etiqueta}</span>
                {enCarrito > 0 && (
                  <span className="shrink-0 text-xs font-medium text-brand-600">En tu pedido · {enCarrito}</span>
                )}
                {puedeEditarImagenes && (
                  <button
                    type="button"
                    onClick={() => abrirSelectorArchivo(v.productId)}
                    disabled={subiendo}
                    title="Subir foto del producto"
                    className="flex shrink-0 items-center justify-center rounded-md border border-slate-200 p-1.5 text-slate-500 hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700 disabled:opacity-60"
                  >
                    {subiendo ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => onAgregar(v)}
                  className="flex shrink-0 items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700"
                >
                  <Plus className="size-3.5" />
                  Agregar
                </button>
              </li>
            );
          })
        )}
      </ul>
    </div>
  );
}
