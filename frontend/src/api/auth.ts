import { vaciarCache } from "./cache";
import { http } from "./http";
import type { Usuario } from "./types";

// Al entrar o salir se vacia la cache de la pestana (api/cache.ts): los CECOs y
// datos del usuario anterior no quedan para el siguiente.

export function login(correo: string, password: string) {
  vaciarCache();
  return http.post<Usuario>("/api/auth/login", { correo, password });
}

export function logout() {
  vaciarCache();
  return http.post<void>("/api/auth/logout");
}

export function me() {
  return http.get<Usuario>("/api/auth/me");
}

export function cambiarPassword(actual: string, nueva: string) {
  return http.post<Usuario>("/api/auth/cambiar-password", { actual, nueva });
}

/** Fin de un recorrido guiado (DEC-L055): lo recuerda el servidor por usuario. */
export function marcarTutorial(codigo: string, estado: "COMPLETADO" | "OMITIDO") {
  return http.put<Usuario>(`/api/auth/tutoriales/${encodeURIComponent(codigo)}`, { estado });
}
