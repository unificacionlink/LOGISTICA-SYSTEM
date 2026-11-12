import { http } from "./http";
import type { CotizacionPublica, EnviarCotizacionPublica } from "./types";

// Pagina publica del proveedor (DEC-L052): sin sesion, la autoriza el token del enlace.

export function obtenerCotizacionPublica(token: string) {
  return http.get<CotizacionPublica>(`/api/publico/cotizacion/${encodeURIComponent(token)}`);
}

/** Datos en JSON y el PDF/foto opcional en el mismo envio (multipart). */
export function enviarCotizacionPublica(token: string, datos: EnviarCotizacionPublica, archivo: File | null) {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
  if (archivo) form.append("archivo", archivo);
  return http.postForm<CotizacionPublica>(`/api/publico/cotizacion/${encodeURIComponent(token)}`, form);
}
