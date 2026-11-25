import { API_URL, http } from "./http";
import type { AdminDatos, EmpresaDatos, DarAccesoResultado, PersonalAdmin, PersonalCarga } from "./types";

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

// ---------- Padron de personal (DEC-L041) ----------

export function listarPersonal() {
  return http.get<PersonalAdmin[]>("/api/admin/personal");
}

export interface PersonaInput {
  /** Solo al agregar: la empresa y el DNI no cambian. */
  empresaCode?: string;
  dni?: string;
  nombre: string;
  correo?: string;
  cargo?: string;
  cecoId?: string;
}

/** Alta individual, solo en empresas sin padron en RR.HH. (DEC-L064). */
export function crearPersona(input: PersonaInput) {
  return http.post<PersonalAdmin[]>("/api/admin/personal", input);
}

export function editarPersona(personalId: string, input: PersonaInput) {
  return http.put<PersonalAdmin[]>(`/api/admin/personal/${personalId}`, input);
}

/** Inactivar en lugar de eliminar; reactivar es solo de ADMIN (DEC-L064). */
export function cambiarActivoPersona(personalId: string, activo: boolean) {
  return http.patch<PersonalAdmin[]>(`/api/admin/personal/${personalId}/activo`, { activo });
}

/** Borra a la persona del padron: irreversible, con motivo (DEC-L065). */
export function eliminarPersona(personalId: string, motivo: string) {
  return http.post<PersonalAdmin[]>(`/api/admin/personal/${personalId}/eliminar`, { motivo });
}

/** Crea la cuenta de una persona del padron; devuelve su contrasena temporal. */
export function darAcceso(personalId: string, correo: string, roles: string[]) {
  return http.post<DarAccesoResultado>(`/api/admin/personal/${personalId}/acceso`, { correo, roles });
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

/** Reemplaza los CECOs que aprueba el usuario (DEC-L037); un CECO tiene un solo aprobador. */
export function asignarCecosAprobados(userId: string, cecoIds: string[]) {
  return http.put<AdminDatos>(`/api/admin/usuarios/${userId}/cecos-aprobados`, { cecoIds });
}

/** CECOs donde el usuario es suplente del aprobador (DEC-L087). */
export function asignarCecosSuplente(userId: string, cecoIds: string[]) {
  return http.put<AdminDatos>(`/api/admin/usuarios/${userId}/cecos-suplente`, { cecoIds });
}

export function sincronizarRrhh() {
  return http.post<AdminDatos>("/api/admin/sincronizar-rrhh");
}

// ---------------------------------------------------------------- Empresas (F-LOG-09, DEC-L079)

export type ImagenEmpresa = "logo" | "firma-gerencia" | "firma-logistica";

export function listarEmpresas() {
  return http.get<EmpresaDatos[]>("/api/admin/empresas");
}

export function editarEmpresa(id: string, datos: { nombre: string; ruc: string; direccion: string; telefono: string }) {
  return http.put<EmpresaDatos[]>(`/api/admin/empresas/${id}`, datos);
}

export function subirImagenEmpresa(id: string, tipo: ImagenEmpresa, file: File) {
  return http.postForm<EmpresaDatos[]>(`/api/admin/empresas/${id}/imagen/${tipo}`, formArchivo(file));
}

export function quitarImagenEmpresa(id: string, tipo: ImagenEmpresa) {
  return http.delete<EmpresaDatos[]>(`/api/admin/empresas/${id}/imagen/${tipo}`);
}

/** Vista previa de una imagen del bucket privado: URL local del navegador, o null. */
export async function verImagenEmpresa(id: string, tipo: ImagenEmpresa): Promise<string | null> {
  try {
    const res = await fetch(`${API_URL}/api/admin/empresas/${id}/imagen/${tipo}`, { credentials: "include" });
    return res.ok ? URL.createObjectURL(await res.blob()) : null;
  } catch {
    return null;
  }
}
