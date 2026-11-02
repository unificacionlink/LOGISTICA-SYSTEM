import { http } from "./http";
import type { Clasificacion, PrecioVenta, PreciosVenta, ProductoCreado, SolicitudProducto, TipoAtributo } from "./types";

// Maestro de productos y solicitudes de alta (DEC-L025, RF-004).

export function obtenerClasificacion() {
  return http.get<Clasificacion>("/api/maestro/clasificacion");
}

export interface CrearProductoInput {
  familyId: string;
  nombre: string;
  uomId: string;
  variantes?: { tipo: TipoAtributo; valor: string }[];
}

export function crearProducto(input: CrearProductoInput) {
  return http.post<ProductoCreado>("/api/maestro/productos", input);
}

export function editarProducto(productId: string, cambios: { nombre?: string; uomId?: string; activo?: boolean }) {
  return http.patch<ProductoCreado>(`/api/maestro/productos/${productId}`, cambios);
}

export function agregarVariante(productId: string, tipo: TipoAtributo, valor: string) {
  return http.post<ProductoCreado>(`/api/maestro/productos/${productId}/variantes`, { tipo, valor });
}

export function listarSolicitudesProducto() {
  return http.get<SolicitudProducto[]>("/api/maestro/solicitudes");
}

export interface CrearSolicitudInput {
  familyId: string;
  nombre: string;
  uomId: string;
  atributoTipo?: TipoAtributo;
  atributoValor?: string;
  descripcionTecnica?: string;
  justificacion: string;
}

export function crearSolicitudProducto(input: CrearSolicitudInput) {
  return http.post<SolicitudProducto>("/api/maestro/solicitudes", input);
}

export function aprobarSolicitudProducto(id: string, familyId: string, nombre: string, uomId: string) {
  return http.post<SolicitudProducto>(`/api/maestro/solicitudes/${id}/aprobar`, { familyId, nombre, uomId });
}

export function rechazarSolicitudProducto(id: string, motivo: string) {
  return http.post<SolicitudProducto>(`/api/maestro/solicitudes/${id}/rechazar`, { motivo });
}

// Precio de venta por empresa y plazo de atención por SKU (DEC-L035).

export function listarPrecios(companyId?: string) {
  return http.get<PreciosVenta>(`/api/maestro/precios${companyId ? `?companyId=${companyId}` : ""}`);
}

export function registrarPrecio(productId: string, companyId: string, precio: number) {
  return http.put<PrecioVenta>(`/api/maestro/productos/${productId}/precio`, { companyId, precio });
}

export function configurarPlazo(productId: string, dias: number | null) {
  return http.put<number | null>(`/api/maestro/productos/${productId}/plazo`, { dias });
}
