import { http } from "./http";
import type { Proveedor } from "./types";

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

export function listarProveedores() {
  return http.get<Proveedor[]>("/api/proveedores");
}

export function obtenerProveedor(id: string) {
  return http.get<Proveedor>(`/api/proveedores/${id}`);
}

export function crearProveedor(input: GuardarProveedorInput) {
  return http.post<Proveedor>("/api/proveedores", input);
}

export function actualizarProveedor(id: string, input: GuardarProveedorInput) {
  return http.put<Proveedor>(`/api/proveedores/${id}`, input);
}
