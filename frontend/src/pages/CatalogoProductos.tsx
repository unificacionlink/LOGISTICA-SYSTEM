import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Images, Loader2, PackagePlus, Plus, Search, Upload } from "lucide-react";
import { asignarProveedorHabitual, listarProveedoresHabituales } from "../api/compras";
import { listarProveedores } from "../api/proveedores";
import { editarProducto, listarSolicitudesProducto, obtenerClasificacion } from "../api/maestro";
import { obtenerCatalogoFormulario, subirImagenProducto } from "../api/requerimientos";
import type { Clasificacion, Familia, Proveedor, SolicitudProducto, VarianteCatalogo } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { Miniatura } from "../components/BuscadorProductos";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import MenuAcciones from "../components/ui/MenuAcciones";
import BandejaSolicitudes, { PENDIENTE } from "../components/maestro/BandejaSolicitudes";
import NuevoProducto from "../components/maestro/NuevoProducto";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";
import { useConfirmar } from "../components/ui/Confirmacion";

interface ProductoCatalogo {
  productId: string;
  skuBase: string;
  nombre: string;
  familiaNombre: string;
  imagenUrl: string | null;
}

const LIMITE_RESULTADOS = 100;

function agruparPorProducto(variantes: VarianteCatalogo[]): ProductoCatalogo[] {
  const porProducto = new Map<string, ProductoCatalogo>();
  for (const v of variantes) {
    if (!porProducto.has(v.productId)) {
      porProducto.set(v.productId, {
        productId: v.productId,
        skuBase: v.skuBase,
        nombre: v.nombre,
        familiaNombre: v.familiaNombre,
        imagenUrl: v.imagenUrl,
      });
    }
  }
  return Array.from(porProducto.values()).sort((a, b) => a.skuBase.localeCompare(b.skuBase));
}

export default function CatalogoProductos() {
  const confirmar = useConfirmar();
  const { tieneAlgunRol } = useAuth();
  const puedeEditarImagenes = tieneAlgunRol("LOGISTICA", "ADMIN");
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [productos, setProductos] = useState<ProductoCatalogo[]>([]);
  const [familias, setFamilias] = useState<Familia[]>([]);

  const [query, setQuery] = useState("");
  const [familiaNombre, setFamiliaNombre] = useState("");
  const [subiendoProductId, setSubiendoProductId] = useState<string | null>(null);
  const [errorSubida, setErrorSubida] = useState<string | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement | null>(null);
  const productoParaSubirRef = useRef<string | null>(null);
  // Proveedor habitual (DEC-L018): destino de la RFQ automatica del faltante.
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);
  const [habitualPorProducto, setHabitualPorProducto] = useState<Record<string, string>>({});
  const [guardandoProveedor, setGuardandoProveedor] = useState<string | null>(null);
  // Maestro (DEC-L025): alta de SKU y bandeja de solicitudes, en su propia pestana.
  const [params, setParams] = useSearchParams();
  const pestana = params.get("tab") === "solicitudes" ? "solicitudes" : "productos";
  const [clasificacion, setClasificacion] = useState<Clasificacion | null>(null);
  const [solicitudes, setSolicitudes] = useState<SolicitudProducto[]>([]);
  const [creando, setCreando] = useState(false);
  const [exito, setExito] = useState<string | null>(null);

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

  const pendientes = solicitudes.filter((s) => s.estado === PENDIENTE).length;

  useEffect(() => {
    if (!puedeEditarImagenes) return;
    listarProveedores().then((res) => {
      if (res.ok && res.result) setProveedores(res.result.filter((pr) => pr.active));
    });
    listarProveedoresHabituales().then((res) => {
      if (res.ok && res.result) {
        const mapa: Record<string, string> = {};
        for (const h of res.result) if (h.supplierId) mapa[h.productId] = h.supplierId;
        setHabitualPorProducto(mapa);
      }
    });
  }, [puedeEditarImagenes]);

  async function onCambiarProveedor(productId: string, supplierId: string) {
    setGuardandoProveedor(productId);
    setErrorSubida(null);
    try {
      const res = await asignarProveedorHabitual(productId, supplierId || null);
      if (!res.ok) {
        setErrorSubida(res.message);
        return;
      }
      setHabitualPorProducto((prev) => {
        const nuevo = { ...prev };
        if (supplierId) nuevo[productId] = supplierId;
        else delete nuevo[productId];
        return nuevo;
      });
    } finally {
      setGuardandoProveedor(null);
    }
  }

  const nombresFamilia = useMemo(
    () => Array.from(new Set(familias.map((f) => f.nombre))).sort((a, b) => a.localeCompare(b)),
    [familias],
  );

  const filtrados = useMemo(() => {
    const q = query.trim().toLowerCase();
    return productos
      .filter((p) => !familiaNombre || p.familiaNombre === familiaNombre)
      .filter((p) => !q || p.skuBase.toLowerCase().includes(q) || p.nombre.toLowerCase().includes(q));
  }, [productos, query, familiaNombre]);

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
              <select
                value={familiaNombre}
                onChange={(e) => setFamiliaNombre(e.target.value)}
                className={`${inputClass} w-full sm:w-56`}
              >
                <option value="">Todas las familias</option>
                {nombresFamilia.map((nombre) => (
                  <option key={nombre} value={nombre}>
                    {nombre}
                  </option>
                ))}
              </select>
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
                        <Miniatura url={p.imagenUrl} titulo={p.nombre} detalle={`${p.skuBase} · ${p.familiaNombre}`} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm text-slate-800">{p.nombre}</p>
                          <p className="mt-0.5 flex items-center gap-2">
                            <span className="font-mono text-xs text-slate-500">{p.skuBase}</span>
                            <Badge tone="gris">{p.familiaNombre}</Badge>
                          </p>
                        </div>
                        {puedeEditarImagenes && (
                          <select
                            value={habitualPorProducto[p.productId] ?? ""}
                            onChange={(e) => onCambiarProveedor(p.productId, e.target.value)}
                            disabled={guardandoProveedor === p.productId}
                            title="Proveedor habitual"
                            aria-label={`Proveedor habitual de ${p.skuBase}`}
                            className={`${inputClass} hidden w-52 shrink-0 py-1.5 text-xs sm:block`}
                          >
                            <option value="">Sin proveedor habitual</option>
                            {proveedores.map((pr) => (
                              <option key={pr.supplierId} value={pr.supplierId}>
                                {pr.legalName}
                              </option>
                            ))}
                          </select>
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
                            acciones={[{ label: "Inactivar producto", peligro: true, onClick: () => onInactivar(p.productId, p.skuBase) }]}
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
    </div>
  );
}
