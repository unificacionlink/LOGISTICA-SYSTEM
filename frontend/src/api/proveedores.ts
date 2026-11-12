import { enCache, invalidar, luegoInvalidar, MINUTO } from "./cache";
import { http } from "./http";
import type { EquivalenciaProveedor, Proveedor } from "./types";

export interface GuardarProveedorInput {
  ruc: string;
  legalName: string;
  contactName?: string;
  email?: string;
  whatsappPhone?: string;
  supplierType: string;
  preferredDeliveryMode?: string;
  active: boolean;
}

const pedirProveedores = () => http.get<Proveedor[]>("/api/proveedores");

/**
 * Proveedores para elegir (comparativo, catalogo): en la pestana 5 min; se
 * invalida al guardar uno. La pantalla Proveedores pide siempre la lista fresca.
 */
export function listarProveedores(opciones: { fresco?: boolean } = {}) {
  if (opciones.fresco) {
    return pedirProveedores().then((res) => {
      if (res.ok) invalidar("proveedores");
      return res;
    });
  }
  return enCache("proveedores", 5 * MINUTO, pedirProveedores);
}

export function obtenerProveedor(id: string) {
  return http.get<Proveedor>(`/api/proveedores/${id}`);
}

export function crearProveedor(input: GuardarProveedorInput) {
  return luegoInvalidar(http.post<Proveedor>("/api/proveedores", input), "proveedores");
}

export function actualizarProveedor(id: string, input: GuardarProveedorInput) {
  return luegoInvalidar(http.put<Proveedor>(`/api/proveedores/${id}`, input), "proveedores");
}

/** Como llama este proveedor a nuestros productos (DEC-L053). */
export function listarEquivalencias(supplierId: string) {
  return http.get<EquivalenciaProveedor[]>(`/api/proveedores/${supplierId}/equivalencias`);
}

export function quitarEquivalencia(equivalenceId: string) {
  return http.delete<null>(`/api/proveedores/equivalencias/${equivalenceId}`);
}
