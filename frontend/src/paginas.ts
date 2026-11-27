import { lazy, type ComponentType } from "react";

/**
 * Pantallas que se descargan al entrar a ellas (code splitting por ruta). Login,
 * Inicio y el layout siguen en el archivo principal: son lo primero que se ve.
 * El menu llama a precargarPagina al pasar el mouse para que el clic sea inmediato.
 */

const CLAVE_RECARGA = "logistica.recarga-por-deploy";

type Carga = () => Promise<{ default: ComponentType }>;

/**
 * Tras un deploy los archivos de la version anterior ya no existen: si una
 * pantalla no se puede descargar, se recarga una vez para traer el index nuevo.
 */
export function conRecarga<T>(cargar: () => Promise<T>): () => Promise<T> {
  return () =>
    cargar().then(
      (m) => {
        try {
          sessionStorage.removeItem(CLAVE_RECARGA);
        } catch {
          // sin almacenamiento: nada que limpiar
        }
        return m;
      },
      (e: unknown) => {
        let yaRecargo = true;
        try {
          yaRecargo = sessionStorage.getItem(CLAVE_RECARGA) === "1";
          sessionStorage.setItem(CLAVE_RECARGA, "1");
        } catch {
          // sin almacenamiento: no se arriesga un ciclo de recargas
        }
        if (yaRecargo) throw e;
        window.location.reload();
        return new Promise<never>(() => {});
      },
    );
}

const c = {
  requerimientos: conRecarga(() => import("./pages/Requerimientos")),
  requerimientosNuevo: conRecarga(() => import("./pages/RequerimientosNuevo")),
  requerimientoDetalle: conRecarga(() => import("./pages/RequerimientoDetalle")),
  aprobaciones: conRecarga(() => import("./pages/Aprobaciones")),
  presupuestos: conRecarga(() => import("./pages/Presupuestos")),
  presupuestoDetalle: conRecarga(() => import("./pages/PresupuestoDetalle")),
  cajaChica: conRecarga(() => import("./pages/CajaChica")),
  cajaChicaNueva: conRecarga(() => import("./pages/CajaChicaNueva")),
  stock: conRecarga(() => import("./pages/Stock")),
  stockAjustes: conRecarga(() => import("./pages/StockAjustes")),
  stockCarga: conRecarga(() => import("./pages/StockCarga")),
  catalogo: conRecarga(() => import("./pages/CatalogoProductos")),
  proveedores: conRecarga(() => import("./pages/Proveedores")),
  proveedorForm: conRecarga(() => import("./pages/ProveedorForm")),
  proveedorFicha: conRecarga(() => import("./pages/ProveedorFicha")),
  compras: conRecarga(() => import("./pages/Compras")),
  comprasCajaChica: conRecarga(() => import("./pages/ComprasCajaChica")),
  comprasConsolidado: conRecarga(() => import("./pages/ComprasConsolidado")),
  comprasComparativo: conRecarga(() => import("./pages/ComprasComparativo")),
  comprasRfqDetalle: conRecarga(() => import("./pages/ComprasRfqDetalle")),
  comprasOrdenes: conRecarga(() => import("./pages/ComprasOrdenes")),
  comprasOrdenDetalle: conRecarga(() => import("./pages/ComprasOrdenDetalle")),
  comprasOrdenNueva: conRecarga(() => import("./pages/ComprasOrdenNueva")),
  almacen: conRecarga(() => import("./pages/Almacen")),
  almacenRecepcion: conRecarga(() => import("./pages/AlmacenRecepcion")),
  almacenCargaMasiva: conRecarga(() => import("./pages/AlmacenCargaMasiva")),
  almacenDespacho: conRecarga(() => import("./pages/AlmacenDespacho")),
  notaInventario: conRecarga(() => import("./pages/NotaInventarioDetalle")),
  administracion: conRecarga(() => import("./pages/Administracion")),
  repositorio: conRecarga(() => import("./pages/Repositorio")),
  cotizarProveedor: conRecarga(() => import("./pages/CotizarProveedor")),
};

export const Requerimientos = lazy(c.requerimientos);
export const RequerimientosNuevo = lazy(c.requerimientosNuevo);
export const RequerimientoDetalle = lazy(c.requerimientoDetalle);
export const Aprobaciones = lazy(c.aprobaciones);
export const Presupuestos = lazy(c.presupuestos);
export const PresupuestoDetalle = lazy(c.presupuestoDetalle);
export const CajaChica = lazy(c.cajaChica);
export const CajaChicaNueva = lazy(c.cajaChicaNueva);
export const Stock = lazy(c.stock);
export const StockAjustes = lazy(c.stockAjustes);
export const StockCarga = lazy(c.stockCarga);
export const CatalogoProductos = lazy(c.catalogo);
export const Proveedores = lazy(c.proveedores);
export const ProveedorForm = lazy(c.proveedorForm);
export const ProveedorFicha = lazy(c.proveedorFicha);
export const Compras = lazy(c.compras);
export const ComprasCajaChica = lazy(c.comprasCajaChica);
export const ComprasConsolidado = lazy(c.comprasConsolidado);
export const ComprasComparativo = lazy(c.comprasComparativo);
export const ComprasRfqDetalle = lazy(c.comprasRfqDetalle);
export const ComprasOrdenes = lazy(c.comprasOrdenes);
export const ComprasOrdenDetalle = lazy(c.comprasOrdenDetalle);
export const ComprasOrdenNueva = lazy(c.comprasOrdenNueva);
export const Almacen = lazy(c.almacen);
export const AlmacenRecepcion = lazy(c.almacenRecepcion);
export const AlmacenCargaMasiva = lazy(c.almacenCargaMasiva);
export const AlmacenDespacho = lazy(c.almacenDespacho);
export const NotaInventarioDetalle = lazy(c.notaInventario);
export const Administracion = lazy(c.administracion);
export const Repositorio = lazy(c.repositorio);
/** Publica, sin sesion: el proveedor cotiza desde su enlace (DEC-L052). */
export const CotizarProveedor = lazy(c.cotizarProveedor);

/** Entradas del menu lateral (navegacion.ts). */
const DEL_MENU: Record<string, Carga> = {
  "/requerimientos": c.requerimientos,
  "/aprobaciones": c.aprobaciones,
  "/presupuestos": c.presupuestos,
  "/caja-chica": c.cajaChica,
  "/compras": c.compras,
  "/proveedores": c.proveedores,
  "/almacen": c.almacen,
  "/stock": c.stock,
  "/catalogo": c.catalogo,
  "/admin": c.administracion,
  "/documentos": c.repositorio,
};

/** Pantallas de detalle: se precargan al pasar el mouse por un enlace hacia ellas. */
const DETALLES: [RegExp, Carga][] = [
  [/^\/requerimientos\/nuevo$/, c.requerimientosNuevo],
  [/^\/requerimientos\/[^/]+\/editar$/, c.requerimientosNuevo],
  [/^\/(compras\/)?requerimientos\/[^/]+\/caja-chica$/, c.comprasCajaChica],
  [/^\/requerimientos\/[^/]+$/, c.requerimientoDetalle],
  [/^\/presupuestos\/[^/]+$/, c.presupuestoDetalle],
  [/^\/caja-chica\/nueva$/, c.cajaChicaNueva],
  [/^\/proveedores\/nuevo$/, c.proveedorForm],
  [/^\/proveedores\/[^/]+\/editar$/, c.proveedorForm],
  [/^\/proveedores\/[^/]+$/, c.proveedorFicha],
  [/^\/compras\/consolidados\/[^/]+$/, c.comprasConsolidado],
  [/^\/compras\/comparativo$/, c.comprasComparativo],
  [/^\/compras\/rfq\/[^/]+$/, c.comprasRfqDetalle],
  [/^\/compras\/ordenes$/, c.comprasOrdenes],
  [/^\/compras\/ordenes\/nueva$/, c.comprasOrdenNueva],
  [/^\/compras\/ordenes\/[^/]+$/, c.comprasOrdenDetalle],
  [/^\/almacen\/ordenes\/[^/]+\/recepcion$/, c.almacenRecepcion],
  [/^\/almacen\/requerimientos\/[^/]+\/despacho$/, c.almacenDespacho],
  [/^\/almacen\/notas\/[^/]+$/, c.notaInventario],
  [/^\/stock\/ajustes$/, c.stockAjustes],
];

const precargadas = new Set<Carga>();

/**
 * Descarga la pantalla antes del clic (hover o foco en el menu o en cualquier
 * enlace interno), asi el clic no espera al codigo y luego a los datos.
 */
export function precargarPagina(to: string) {
  const ruta = to.split(/[?#]/)[0];
  const carga = DEL_MENU[ruta] ?? DETALLES.find(([patron]) => patron.test(ruta))?.[1];
  if (!carga || precargadas.has(carga)) return;
  precargadas.add(carga);
  carga().catch(() => precargadas.delete(carga));
}
