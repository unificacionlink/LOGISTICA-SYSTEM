import { http } from "./http";
import type { AdminDatos, PersonalCarga } from "./types";

// Administracion de identidad (solo ADMIN, DEC-L024). Cada escritura devuelve
// los datos completos de la pantalla: una sola ida y vuelta.

export function obtenerAdmin() {
  return http.get<AdminDatos>("/api/admin");
}

export function asignarRoles(userId: string, roles: string[]) {
  return http.put<AdminDatos>(`/api/admin/usuarios/${userId}/roles`, { roles });
}

export function generarPasswordTemporal(userId: string) {
  return http.post<{ correo: string; password: string }>(`/api/admin/usuarios/${userId}/password-temporal`);
}

export function cambiarActivo(userId: string, activo: boolean) {
  return http.patch<AdminDatos>(`/api/admin/usuarios/${userId}/activo`, { activo });
}

export interface CrearUsuarioInput {
  nombre: string;
  correo: string;
  dni?: string;
  empresaCode: string;
  cecoId?: string;
  roles: string[];
  cargo?: string;
}

export function crearUsuario(input: CrearUsuarioInput) {
  return http.post<AdminDatos>("/api/admin/usuarios", input);
}

export interface EditarUsuarioInput {
  nombre: string;
  correo: string;
  dni?: string;
  cargo?: string;
}

export function editarUsuario(userId: string, input: EditarUsuarioInput) {
  return http.put<AdminDatos>(`/api/admin/usuarios/${userId}`, input);
}

/** Reemplaza los CECOs para los que el usuario puede pedir (DEC-L029). */
export function asignarCecos(userId: string, cecoIds: string[]) {
  return http.put<AdminDatos>(`/api/admin/usuarios/${userId}/cecos`, { cecoIds });
}

// ---------- Padron de personal manual por Excel (GOYDEL/METRIN, DEC-L028) ----------

export function descargarPlantillaPersonal() {
  const hoy = new Date().toISOString().slice(0, 10);
  return http.descargar("/api/admin/personal/plantilla", `PERSONAL_GOYDEL_METRIN_${hoy}.xlsx`);
}

function formArchivo(file: File) {
  const form = new FormData();
  form.append("file", file);
  return form;
}

export function validarPersonal(file: File) {
  return http.postForm<PersonalCarga>("/api/admin/personal/validar", formArchivo(file));
}

export function confirmarPersonal(file: File) {
  return http.postForm<PersonalCarga>("/api/admin/personal/confirmar", formArchivo(file));
}

export function crearCeco(empresaCode: string, code: string, nombre: string) {
  return http.post<AdminDatos>("/api/admin/cecos", { empresaCode, code, nombre });
}

/** Aprobador del CECO (DEC-L030); null lo deja sin asignar. */
export function asignarAprobadorCeco(cecoId: string, userId: string | null) {
  return http.put<AdminDatos>(`/api/admin/cecos/${cecoId}/aprobador`, { userId });
}

export function sincronizarRrhh() {
  return http.post<AdminDatos>("/api/admin/sincronizar-rrhh");
}
