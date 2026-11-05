import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Clock, Images, Loader2, PackagePlus, Plus, Search, Truck, Upload } from "lucide-react";
import { guardarProveedoresProducto, listarProveedoresProducto } from "../api/compras";
import { listarProveedores } from "../api/proveedores";
import {
  configurarPlazo,
  editarProducto,
  listarPrecios,
  listarSolicitudesProducto,
  obtenerClasificacion,
  registrarPrecio,
} from "../api/maestro";
import { obtenerCatalogoFormulario, subirImagenProducto } from "../api/requerimientos";
import type {
  Clasificacion,
  EmpresaOpcion,
  Familia,
  PrecioVenta,
  Proveedor,
  SolicitudProducto,
  VarianteCatalogo,
} from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Miniatura } from "../components/BuscadorProductos";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import MenuAcciones from "../components/ui/MenuAcciones";
import BandejaSolicitudes, { PENDIENTE } from "../components/maestro/BandejaSolicitudes";
import DialogoProveedores from "../components/maestro/DialogoProveedores";
import DialogoValor from "../components/maestro/DialogoValor";
import NuevoProducto from "../components/maestro/NuevoProducto";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import FiltroFamilia, { coincideFamilia, etiquetaFamilia } from "../components/FiltroFamilia";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";
import { useConfirmar } from "../components/ui/Confirmacion";

interface ProductoCatalogo {
  productId: string;
  skuBase: string;
  nombre: string;
  familiaId: string;
  familiaNombre: string;
  tipoId: string;
  tipoNombre: string;
  imagenUrl: string | null;
  plazoAtencionDias: number | null;
}

const LIMITE_RESULTADOS = 100;

const soles = new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" });

function agruparPorProducto(variantes: VarianteCatalogo[]): ProductoCatalogo[] {
  const porProducto = new Map<string, ProductoCatalogo>();
  for (const v of variantes) {
    if (!porProducto.has(v.productId)) {
      porProducto.set(v.productId, {
        productId: v.productId,
        skuBase: v.skuBase,
        nombre: v.nombre,
        familiaId: v.familiaId,
        familiaNombre: v.familiaNombre,
        tipoId: v.tipoId,
        tipoNombre: v.tipoNombre,
        imagenUrl: v.imagenUrl,
        plazoAtencionDias: v.plazoAtencionDias,
      });
    }
  }
  return Array.from(porProducto.values()).sort((a, b) => a.skuBase.localeCompare(b.skuBase));
}

export default function CatalogoProductos() {
  const confirmar = useConfirmar();
  const { tieneAlgunRol } = useAuth();
  const puedeEditarImagenes = tieneAlgunRol("LOGISTICA", "ADMIN");
  // Precio de venta por empresa (DEC-L035): lo registran PYC y Logistica; Gerencia y Admin solo lo ven.
  const puedeVerPrecios = tieneAlgunRol("PYC", "GERENCIA", "LOGISTICA", "ADMIN");
  const puedeEditarPrecio = tieneAlgunRol("PYC", "LOGISTICA");
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [productos, setProductos] = useState<ProductoCatalogo[]>([]);
  const [familias, setFamilias] = useState<Familia[]>([]);

  const [query, setQuery] = useState("");
  const [filtroFamilia, setFiltroFamilia] = useState("");
  const [subiendoProductId, setSubiendoProductId] = useState<string | null>(null);
  const [errorSubida, setErrorSubida] = useState<string | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement | null>(null);
  const productoParaSubirRef = useRef<string | null>(null);
  // Proveedores habilitados por producto (DEC-L039): la RFQ del consolidado va a todos.
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);
  const [coberturaPorProducto, setCoberturaPorProducto] = useState<Record<string, string[]>>({});
  // Maestro (DEC-L025): alta de SKU y bandeja de solicitudes, en su propia pestana.
  const [params, setParams] = useSearchParams();
  const pestana = params.get("tab") === "solicitudes" ? "solicitudes" : "productos";
  const [clasificacion, setClasificacion] = useState<Clasificacion | null>(null);
  const [solicitudes, setSolicitudes] = useState<SolicitudProducto[]>([]);
  const [creando, setCreando] = useState(false);
  const [exito, setExito] = useState<string | null>(null);
  const [empresas, setEmpresas] = useState<EmpresaOpcion[]>([]);
  const [empresaPrecios, setEmpresaPrecios] = useState("");
  const [precios, setPrecios] = useState<Record<string, PrecioVenta>>({});
  const [soloSinPrecio, setSoloSinPrecio] = useState(false);
  const [editando, setEditando] = useState<{ tipo: "precio" | "plazo" | "proveedores"; producto: ProductoCatalogo } | null>(null);

  function cargarCatalogo() {
    return obtenerCatalogoFormulario()
      .then((res) => {
        if (res.ok && res.result) {
          setProductos(agruparPorProducto(res.result.variantes));
          setFamilias(res.result.familias);
        } else {
          setError(res.message);
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(() => {
    cargarCatalogo();
  }, []);

  useEffect(() => {
    if (!puedeEditarImagenes) return;
    obtenerClasificacion().then((res) => res.ok && res.result && setClasificacion(res.result));
    listarSolicitudesProducto().then((res) => res.ok && res.result && setSolicitudes(res.result));
  }, [puedeEditarImagenes]);

  async function onInactivar(productId: string, sku: string) {
    const r = await confirmar({
      titulo: `¿Inactivar ${sku}?`,
      mensaje: "Dejará de aparecer al crear requerimientos.",
      confirmar: "Inactivar",
      peligro: true,
    });
    if (!r) return;
    const res = await editarProducto(productId, { activo: false });
    if (res.ok) {
      setProductos((prev) => prev.filter((p) => p.productId !== productId));
      setExito(`${sku} inactivado.`);
    } else {
      setErrorSubida(res.message);
    }
  }

  useEffect(() => {
    if (!puedeVerPrecios) return;
    listarPrecios(empresaPrecios || undefined).then((res) => {
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      setEmpresas(res.result.empresas);
      if (!empresaPrecios) setEmpresaPrecios(res.result.companyId);
      setPrecios(Object.fromEntries(res.result.precios.map((pv) => [pv.productId, pv])));
    });
  }, [puedeVerPrecios, empresaPrecios]);

  async function onGuardarPrecio(p: ProductoCatalogo, valor: number | null) {
    if (valor == null) return "Ingresa un precio de venta mayor que cero.";
    const res = await registrarPrecio(p.productId, empresaPrecios, valor);
    if (!res.ok || !res.result) return res.message;
    const precio = res.result;
    setPrecios((prev) => ({ ...prev, [p.productId]: precio }));
    setExito(`${p.skuBase}: precio de venta ${soles.format(precio.precio)}.`);
    return null;
  }

  async function onGuardarPlazo(p: ProductoCatalogo, valor: number | null) {
    if (valor != null && !Number.isInteger(valor)) return "Ingresa días enteros.";
    const res = await configurarPlazo(p.productId, valor);
    if (!res.ok) return res.message;
    setProductos((prev) => prev.map((x) => (x.productId === p.productId ? { ...x, plazoAtencionDias: valor } : x)));
    setExito(`${p.skuBase}: ${valor == null ? "plazo de atención sin configurar" : `atención en ${valor} días calendario`}.`);
    return null;
  }

  const pendientes = solicitudes.filter((s) => s.estado === PENDIENTE).length;

  useEffect(() => {
    if (!puedeEditarImagenes) return;
    listarProveedores().then((res) => {
      if (res.ok && res.result) setProveedores(res.result.filter((pr) => pr.active));
    });
    listarProveedoresProducto().then((res) => {
      if (res.ok && res.result) {
        const mapa: Record<string, string[]> = {};
        for (const c of res.result) (mapa[c.productId] ??= []).push(c.supplierId);
        setCoberturaPorProducto(mapa);
      }
    });
  }, [puedeEditarImagenes]);

  async function onGuardarProveedores(p: ProductoCatalogo, supplierIds: string[]): Promise<string | null> {
    const res = await guardarProveedoresProducto(p.productId, supplierIds);
    if (!res.ok) return res.message;
    setCoberturaPorProducto((prev) => ({ ...prev, [p.productId]: (res.result ?? []).map((c) => c.supplierId) }));
    setExito(`${p.skuBase}: ${supplierIds.length === 0 ? "sin proveedores habilitados" : `${supplierIds.length} proveedor(es) habilitado(s)`}.`);
    return null;
  }

  const filtrados = useMemo(() => {
    const q = query.trim().toLowerCase();
    return productos
      .filter((p) => coincideFamilia(filtroFamilia, p.tipoId, p.familiaId))
      .filter((p) => !q || p.skuBase.toLowerCase().includes(q) || p.nombre.toLowerCase().includes(q))
      .filter((p) => !soloSinPrecio || !precios[p.productId]);
  }, [productos, query, filtroFamilia, soloSinPrecio, precios]);

  const visibles = filtrados.slice(0, LIMITE_RESULTADOS);

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
      const url = res.result;
      setProductos((prev) => prev.map((p) => (p.productId === productId ? { ...p, imagenUrl: url } : p)));
    } finally {
      setSubiendoProductId(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Catálogo de productos"
        actions={
          puedeEditarImagenes &&
          clasificacion &&
          pestana === "productos" &&
          !creando && (
            <Button icon={<Plus className="size-4" />} onClick={() => setCreando(true)}>
              Nuevo producto
            </Button>
          )
        }
      />

      <input ref={inputArchivoRef} type="file" accept="image/*" className="hidden" onChange={onArchivoSeleccionado} />

      {puedeEditarImagenes && (
        <div role="tablist" className="mb-4 flex flex-wrap gap-1 border-b border-slate-200">
          {(["productos", "solicitudes"] as const).map((id) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={pestana === id}
              onClick={() => {
                setParams(id === "productos" ? {} : { tab: id }, { replace: true });
                setExito(null);
              }}
              className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
                pestana === id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              {id === "productos" ? <Images className="size-4" /> : <PackagePlus className="size-4" />}
              {id === "productos" ? "Productos" : "Solicitudes de alta"}
              {id === "solicitudes" && pendientes > 0 && <Badge tone="amarillo">{pendientes}</Badge>}
            </button>
          ))}
        </div>
      )}

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      {pestana === "solicitudes" && clasificacion ? (
        <BandejaSolicitudes
          solicitudes={solicitudes}
          clasificacion={clasificacion}
          onActualizada={(s, mensaje) => {
            setSolicitudes((prev) => prev.map((x) => (x.id === s.id ? s : x)));
            setExito(mensaje);
            if (s.skuGenerado) cargarCatalogo();
          }}
        />
      ) : (
      <>
      {creando && clasificacion && (
        <NuevoProducto
          clasificacion={clasificacion}
          cerrar={() => setCreando(false)}
          onCreado={(p, mensaje) => {
            setCreando(false);
            setExito(`${mensaje} Variantes: ${p.variantes.join(", ")}.`);
            setQuery(p.skuBase);
            cargarCatalogo();
          }}
        />
      )}

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={4} />
        ) : (
          <>
            <div className="flex flex-col gap-2 border-b border-slate-100 p-4 sm:flex-row sm:items-center">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  type="text"
                  placeholder="Buscar por SKU o nombre…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  className={`${inputClass} w-full pl-9`}
                />
              </div>
              <FiltroFamilia familias={familias} value={filtroFamilia} onChange={setFiltroFamilia} className="w-full sm:w-64" />
              {puedeVerPrecios && empresas.length > 1 && (
                <select
                  value={empresaPrecios}
                  onChange={(e) => setEmpresaPrecios(e.target.value)}
                  aria-label="Empresa de los precios de venta"
                  className={`${inputClass} w-full sm:w-36`}
                >
                  {empresas.map((e) => (
                    <option key={e.companyId} value={e.companyId}>
                      {e.code}
                    </option>
                  ))}
                </select>
              )}
              {puedeVerPrecios && (
                <label className="flex shrink-0 cursor-pointer items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={soloSinPrecio}
                    onChange={(e) => setSoloSinPrecio(e.target.checked)}
                    className="size-4 rounded border-slate-300 accent-brand-700"
                  />
                  Sin precio
                </label>
              )}
            </div>

            {errorSubida && <p className="px-4 pt-3 text-xs text-rose-600">{errorSubida}</p>}

            {filtrados.length === 0 ? (
              <EmptyState icon={Images} title="Sin resultados" />
            ) : (
              <>
                <ul className="divide-y divide-slate-100">
                  {visibles.map((p) => {
                    const subiendo = subiendoProductId === p.productId;
                    return (
                      <li key={p.productId} className="flex items-center gap-3 px-4 py-3">
                        <Miniatura url={p.imagenUrl} titulo={p.nombre} detalle={`${p.skuBase} · ${etiquetaFamilia(p.tipoNombre, p.familiaNombre)}`} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm text-slate-800">{p.nombre}</p>
                          <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span className="font-mono text-xs text-slate-500">{p.skuBase}</span>
                            <Badge tone="gris">{etiquetaFamilia(p.tipoNombre, p.familiaNombre)}</Badge>
                            {puedeEditarImagenes && (coberturaPorProducto[p.productId]?.length ?? 0) > 0 && (
                              <span className="flex items-center gap-1 text-xs text-slate-500" title="Proveedores habilitados">
                                <Truck className="size-3.5" />
                                {coberturaPorProducto[p.productId].length}
                              </span>
                            )}
                            {p.plazoAtencionDias != null && (
                              <span className="flex items-center gap-1 text-xs text-slate-500" title="Plazo de atención">
                                <Clock className="size-3.5" />
                                {p.plazoAtencionDias} {p.plazoAtencionDias === 1 ? "día" : "días"}
                              </span>
                            )}
                          </p>
                        </div>
                        {puedeVerPrecios && (
                          <span
                            className={`shrink-0 text-right tabular-nums ${
                              precios[p.productId] ? "text-sm font-medium text-slate-800" : "text-xs text-slate-400"
                            }`}
                            title={precios[p.productId] ? `Registrado por ${precios[p.productId].actualizadoPor}` : undefined}
                          >
                            {precios[p.productId] ? soles.format(precios[p.productId].precio) : "Sin precio"}
                          </span>
                        )}
                        {puedeEditarPrecio && !puedeEditarImagenes && (
                          <Button variant="secondary" size="sm" onClick={() => setEditando({ tipo: "precio", producto: p })}>
                            {precios[p.productId] ? "Cambiar precio" : "Registrar precio"}
                          </Button>
                        )}
                        {puedeEditarImagenes && (
                          <button
                            type="button"
                            onClick={() => abrirSelectorArchivo(p.productId)}
                            disabled={subiendo}
                            className="flex shrink-0 items-center gap-1.5 rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-700 hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700 disabled:opacity-60"
                          >
                            {subiendo ? (
                              <Loader2 className="size-3.5 animate-spin" />
                            ) : (
                              <Upload className="size-3.5" />
                            )}
                            {p.imagenUrl ? "Cambiar foto" : "Subir foto"}
                          </button>
                        )}
                        {puedeEditarImagenes && (
                          <MenuAcciones
                            acciones={[
                              ...(puedeEditarPrecio
                                ? [{ label: "Precio de venta", onClick: () => setEditando({ tipo: "precio", producto: p }) }]
                                : []),
                              { label: "Proveedores", onClick: () => setEditando({ tipo: "proveedores", producto: p }) },
                              { label: "Plazo de atención", onClick: () => setEditando({ tipo: "plazo", producto: p }) },
                              { label: "Inactivar producto", peligro: true, onClick: () => onInactivar(p.productId, p.skuBase) },
                            ]}
                          />
                        )}
                      </li>
                    );
                  })}
                </ul>
                {filtrados.length > LIMITE_RESULTADOS && (
                  <p className="border-t border-slate-100 px-4 py-3 text-center text-xs text-slate-400">
                    Mostrando {LIMITE_RESULTADOS} de {filtrados.length}
                  </p>
                )}
              </>
            )}
          </>
        )}
      </Card>
      </>
      )}

      {editando?.tipo === "proveedores" && (
        <DialogoProveedores
          titulo={`Proveedores · ${editando.producto.skuBase}`}
          proveedores={proveedores}
          seleccionInicial={coberturaPorProducto[editando.producto.productId] ?? []}
          onGuardar={(ids) => onGuardarProveedores(editando.producto, ids)}
          onCerrar={() => setEditando(null)}
        />
      )}
      {editando?.tipo === "precio" && (
        <DialogoValor
          titulo={`Precio de venta · ${editando.producto.skuBase}`}
          etiqueta={`Precio unitario en ${empresas.find((e) => e.companyId === empresaPrecios)?.code ?? "la empresa"}`}
          valorInicial={precios[editando.producto.productId]?.precio ?? null}
          prefijo="S/"
          min={0.01}
          step="0.01"
          onGuardar={(v) => onGuardarPrecio(editando.producto, v)}
          onCerrar={() => setEditando(null)}
        />
      )}
      {editando?.tipo === "plazo" && (
        <DialogoValor
          titulo={`Plazo de atención · ${editando.producto.skuBase}`}
          etiqueta="Días calendario"
          valorInicial={editando.producto.plazoAtencionDias}
          sufijo="días"
          min={0}
          max={3650}
          step="1"
          permitirVacio
          onGuardar={(v) => onGuardarPlazo(editando.producto, v)}
          onCerrar={() => setEditando(null)}
        />
      )}
    </div>
  );
}
