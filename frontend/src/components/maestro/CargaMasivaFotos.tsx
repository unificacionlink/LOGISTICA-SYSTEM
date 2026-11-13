import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CheckCircle2, FolderOpen, ImagePlus, XCircle } from "lucide-react";
import { subirImagenProducto } from "../../api/requerimientos";
import { comprimirImagen, esImagen } from "../../utils/imagenes";
import Badge from "../ui/Badge";
import Button from "../ui/Button";

export interface ProductoFoto {
  productId: string;
  skuBase: string;
  nombre: string;
  imagenUrl: string | null;
}

type Estado = "lista" | "omitida" | "sin_producto" | "repetida" | "subiendo" | "ok" | "error";

interface Fila {
  clave: string;
  file: File;
  producto: ProductoFoto | null;
  estado: Estado;
  mensaje?: string;
}

/** Fotos a la vez: mas no acelera (el servidor sube a Storage y escribe en la BD). */
const EN_PARALELO = 4;

/**
 * Codigo del producto desde el nombre del archivo: "AC01001.jpg",
 * "ep02015 (2).PNG" o "EP02001-XL.webp" (variante: la foto va a su producto base).
 */
function codigoDe(nombre: string): string {
  const sinExtension = nombre.replace(/\.[^.]+$/, "").trim().toUpperCase();
  return sinExtension.split(/[\s_(]+/)[0] ?? "";
}

/** Recorre una carpeta arrastrada (Chrome/Edge/Firefox) y devuelve sus archivos. */
async function archivosDeEntrada(entrada: FileSystemEntry): Promise<File[]> {
  if (entrada.isFile) {
    return new Promise((r) => (entrada as FileSystemFileEntry).file((f) => r([f]), () => r([])));
  }
  if (!entrada.isDirectory) return [];
  const lector = (entrada as FileSystemDirectoryEntry).createReader();
  const hijos: FileSystemEntry[] = [];
  // readEntries devuelve por tandas (100 en Chrome) hasta una vacia.
  for (;;) {
    const tanda = await new Promise<FileSystemEntry[]>((r) => lector.readEntries(r, () => r([])));
    if (tanda.length === 0) break;
    hijos.push(...tanda);
  }
  return (await Promise.all(hijos.map(archivosDeEntrada))).flat();
}

const ETIQUETA: Record<Estado, { texto: string; tono: "gris" | "azul" | "verde" | "amarillo" | "rojo" }> = {
  lista: { texto: "Lista", tono: "azul" },
  omitida: { texto: "Ya tiene foto", tono: "gris" },
  sin_producto: { texto: "Sin producto", tono: "amarillo" },
  repetida: { texto: "Repetida", tono: "amarillo" },
  subiendo: { texto: "Subiendo", tono: "azul" },
  ok: { texto: "Subida", tono: "verde" },
  error: { texto: "Error", tono: "rojo" },
};

/**
 * Carga masiva de fotos del catalogo: cada archivo se llama como el SKU base o
 * el codigo de variante (misma convencion que scripts/cargar_imagenes.py). Se
 * revisa antes de subir; el navegador reduce cada foto y sube varias a la vez
 * por el mismo endpoint (y permisos) que la subida individual.
 */
export default function CargaMasivaFotos({
  productos,
  codigoAProducto,
  onSubida,
  onCerrar,
}: {
  productos: ProductoFoto[];
  /** SKU base y codigos de variante (en mayusculas) -> productId. */
  codigoAProducto: Map<string, string>;
  onSubida: (productId: string, url: string) => void;
  onCerrar: () => void;
}) {
  const [filas, setFilas] = useState<Fila[]>([]);
  const [reemplazar, setReemplazar] = useState(false);
  const [subiendo, setSubiendo] = useState(false);
  const [terminado, setTerminado] = useState(false);
  const [arrastrando, setArrastrando] = useState(false);
  const cancelado = useRef(false);
  const inputArchivos = useRef<HTMLInputElement>(null);
  const inputCarpeta = useRef<HTMLInputElement>(null);
  const porId = useMemo(() => new Map(productos.map((p) => [p.productId, p])), [productos]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !subiendo && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [subiendo, onCerrar]);

  // La carpeta se elige con webkitdirectory (no esta en los tipos de React).
  useEffect(() => {
    inputCarpeta.current?.setAttribute("webkitdirectory", "");
  }, []);

  function clasificar(archivos: File[], conReemplazo: boolean): Fila[] {
    const vistos = new Set<string>();
    return archivos
      .filter(esImagen)
      // Si hay foto del SKU base y de una variante del mismo producto, gana la del SKU base.
      .sort(
        (a, b) =>
          Number(codigoDe(a.name).includes("-")) - Number(codigoDe(b.name).includes("-")) || a.name.localeCompare(b.name),
      )
      .map((file, i) => {
        const codigo = codigoDe(file.name);
        const productId =
          codigoAProducto.get(codigo) ?? (codigo.includes("-") ? codigoAProducto.get(codigo.split("-")[0]) : undefined);
        const producto = productId ? (porId.get(productId) ?? null) : null;
        let estado: Estado = "lista";
        if (!producto) estado = "sin_producto";
        else if (vistos.has(producto.productId)) estado = "repetida";
        else if (producto.imagenUrl && !conReemplazo) estado = "omitida";
        if (producto) vistos.add(producto.productId);
        return { clave: `${i}-${file.name}`, file, producto, estado };
      });
  }

  function agregar(archivos: File[]) {
    setTerminado(false);
    setFilas(clasificar(archivos, reemplazar));
  }

  function cambiarReemplazo(valor: boolean) {
    setReemplazar(valor);
    setFilas((prev) => clasificar(prev.map((f) => f.file), valor));
  }

  async function onDrop(e: React.DragEvent) {
    e.preventDefault();
    setArrastrando(false);
    if (subiendo) return;
    const entradas = Array.from(e.dataTransfer.items)
      .map((it) => it.webkitGetAsEntry?.())
      .filter((x): x is FileSystemEntry => !!x);
    const archivos = entradas.length
      ? (await Promise.all(entradas.map(archivosDeEntrada))).flat()
      : Array.from(e.dataTransfer.files);
    agregar(archivos);
  }

  function actualizar(clave: string, cambios: Partial<Fila>) {
    setFilas((prev) => prev.map((f) => (f.clave === clave ? { ...f, ...cambios } : f)));
  }

  async function subir(objetivo: Fila[]) {
    cancelado.current = false;
    setSubiendo(true);
    setTerminado(false);
    const cola = [...objetivo];
    const trabajador = async () => {
      for (let f = cola.shift(); f && !cancelado.current; f = cola.shift()) {
        const fila = f;
        actualizar(fila.clave, { estado: "subiendo", mensaje: undefined });
        try {
          const archivo = await comprimirImagen(fila.file);
          const res = await subirImagenProducto(fila.producto!.productId, archivo, true);
          if (res.ok && res.result) {
            actualizar(fila.clave, { estado: "ok" });
            onSubida(fila.producto!.productId, res.result);
          } else {
            actualizar(fila.clave, { estado: "error", mensaje: res.message });
          }
        } catch (err) {
          actualizar(fila.clave, { estado: "error", mensaje: err instanceof Error ? err.message : "No se pudo procesar." });
        }
      }
    };
    await Promise.all(Array.from({ length: Math.min(EN_PARALELO, cola.length) }, trabajador));
    // Lo que quedo en cola por cancelar vuelve a "lista".
    setFilas((prev) => prev.map((f) => (f.estado === "subiendo" ? { ...f, estado: "lista" } : f)));
    setSubiendo(false);
    setTerminado(true);
  }

  const conteo = (e: Estado) => filas.filter((f) => f.estado === e).length;
  const listas = filas.filter((f) => f.estado === "lista");
  const fallidas = filas.filter((f) => f.estado === "error");
  const procesables = filas.filter((f) => ["lista", "subiendo", "ok", "error"].includes(f.estado)).length;
  const hechas = conteo("ok") + conteo("error");
  // Primero lo que requiere atencion; lo subido al final.
  const orden: Estado[] = ["error", "subiendo", "lista", "sin_producto", "repetida", "omitida", "ok"];
  const visibles = [...filas].sort((a, b) => orden.indexOf(a.estado) - orden.indexOf(b.estado));

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && !subiendo && onCerrar()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="carga-fotos-titulo"
        className="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-xl border border-slate-200 bg-white shadow-flotante"
      >
        <div className="border-b border-slate-200 px-5 py-4">
          <h2 id="carga-fotos-titulo" className="font-semibold text-slate-900">
            Carga masiva de fotos
          </h2>
          <p className="mt-0.5 text-xs text-slate-500">Cada foto se llama como su SKU: AC01001.jpg, EP02001-XL.png</p>
        </div>

        <input
          ref={inputArchivos}
          type="file"
          accept="image/*"
          multiple
          className="hidden"
          onChange={(e) => {
            agregar(Array.from(e.target.files ?? []));
            e.target.value = "";
          }}
        />
        <input
          ref={inputCarpeta}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => {
            agregar(Array.from(e.target.files ?? []));
            e.target.value = "";
          }}
        />

        {filas.length === 0 ? (
          <div
            onDragOver={(e) => {
              e.preventDefault();
              setArrastrando(true);
            }}
            onDragLeave={() => setArrastrando(false)}
            onDrop={onDrop}
            className={`m-5 flex flex-col items-center gap-4 rounded-lg border-2 border-dashed px-6 py-12 text-center transition-colors ${
              arrastrando ? "border-brand-400 bg-brand-50" : "border-slate-200"
            }`}
          >
            <ImagePlus className="size-8 text-slate-400" />
            <p className="text-sm text-slate-600">Arrastra aquí la carpeta o las fotos</p>
            <div className="flex flex-wrap justify-center gap-2">
              <Button variant="secondary" icon={<FolderOpen className="size-4" />} onClick={() => inputCarpeta.current?.click()}>
                Elegir carpeta
              </Button>
              <Button variant="ghost" onClick={() => inputArchivos.current?.click()}>
                Elegir fotos
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-slate-100 px-5 py-3 text-xs text-slate-600">
              <span>
                <b className="text-slate-900">{filas.length}</b> fotos
              </span>
              {conteo("lista") > 0 && (
                <span>
                  <b className="text-brand-700">{conteo("lista")}</b> por subir
                </span>
              )}
              {conteo("ok") > 0 && (
                <span>
                  <b className="text-emerald-700">{conteo("ok")}</b> subidas
                </span>
              )}
              {conteo("error") > 0 && (
                <span>
                  <b className="text-rose-700">{conteo("error")}</b> con error
                </span>
              )}
              {conteo("sin_producto") > 0 && <span>{conteo("sin_producto")} sin producto</span>}
              {conteo("repetida") > 0 && <span>{conteo("repetida")} repetidas</span>}
              {conteo("omitida") > 0 && <span>{conteo("omitida")} ya tienen foto</span>}
              {!subiendo && !terminado && (
                <label className="ml-auto flex cursor-pointer items-center gap-2 text-slate-600">
                  <input
                    type="checkbox"
                    checked={reemplazar}
                    onChange={(e) => cambiarReemplazo(e.target.checked)}
                    className="size-4 rounded border-slate-300 accent-brand-700"
                  />
                  Reemplazar fotos existentes
                </label>
              )}
            </div>

            {(subiendo || terminado) && procesables > 0 && (
              <div className="px-5 pt-3">
                <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full bg-brand-600 transition-[width] duration-300"
                    style={{ width: `${Math.round((hechas / procesables) * 100)}%` }}
                  />
                </div>
                <p className="mt-1 text-right font-mono text-[0.6875rem] tabular-nums text-slate-400">
                  {hechas} / {procesables}
                </p>
              </div>
            )}

            <ul className="min-h-0 flex-1 divide-y divide-slate-100 overflow-y-auto">
              {visibles.map((f) => (
                <li key={f.clave} className="flex items-center gap-3 px-5 py-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-mono text-xs text-slate-700" title={f.file.name}>
                      {f.file.name}
                    </p>
                    <p className="truncate text-xs text-slate-500">
                      {f.producto ? `${f.producto.skuBase} · ${f.producto.nombre}` : "El nombre no coincide con ningún SKU"}
                      {f.mensaje && <span className="text-rose-700"> · {f.mensaje}</span>}
                    </p>
                  </div>
                  {f.estado === "ok" ? (
                    <CheckCircle2 className="size-4 shrink-0 text-emerald-600" />
                  ) : f.estado === "error" ? (
                    <XCircle className="size-4 shrink-0 text-rose-600" />
                  ) : (
                    <Badge tone={ETIQUETA[f.estado].tono}>{ETIQUETA[f.estado].texto}</Badge>
                  )}
                </li>
              ))}
            </ul>
          </>
        )}

        <div className="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3">
          {subiendo ? (
            <Button variant="secondary" onClick={() => (cancelado.current = true)}>
              Detener
            </Button>
          ) : (
            <>
              {filas.length > 0 && !terminado && (
                <Button variant="ghost" onClick={() => setFilas([])}>
                  Elegir otras
                </Button>
              )}
              <Button variant={listas.length > 0 || fallidas.length > 0 ? "ghost" : "primary"} onClick={onCerrar}>
                {terminado ? "Cerrar" : "Cancelar"}
              </Button>
              {fallidas.length > 0 && (
                <Button variant="secondary" onClick={() => subir(fallidas)}>
                  Reintentar {fallidas.length}
                </Button>
              )}
              {listas.length > 0 && (
                <Button onClick={() => subir(listas)}>
                  Subir {listas.length} {listas.length === 1 ? "foto" : "fotos"}
                </Button>
              )}
            </>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
