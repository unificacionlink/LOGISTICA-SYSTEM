import { http } from "./http";
import type { Usuario } from "./types";

export function login(correo: string, password: string) {
  return http.post<Usuario>("/api/auth/login", { correo, password });
}

export function logout() {
  return http.post<void>("/api/auth/logout");
}

export function me() {
  return http.get<Usuario>("/api/auth/me");
}

export function cambiarPassword(actual: string, nueva: string) {
  return http.post<Usuario>("/api/auth/cambiar-password", { actual, nueva });
}
