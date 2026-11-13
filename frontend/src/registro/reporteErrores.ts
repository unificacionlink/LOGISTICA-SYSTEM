import { API_URL } from "../api/http";

/**
 * Errores de la pantalla al registro de la plataforma (DEC-L060). Va directo
 * con fetch (sin la barra de progreso ni mensajes al usuario) y nunca falla:
 * si no hay sesion o no hay red, se descarta. Un mismo mensaje se reporta una
 * vez por minuto y como mucho 15 por pestana, para no inundar el registro.
 */
const MAX_POR_PESTANA = 15;
const RUIDO = [/ResizeObserver loop/i, /^Script error\.?$/i];

let enviados = 0;
const vistos = new Map<string, number>();

export function reportarError(mensaje: string, pila?: string | null) {
  const texto = (mensaje ?? "").trim();
  if (!texto || enviados >= MAX_POR_PESTANA || RUIDO.some((r) => r.test(texto))) return;
  const clave = texto.slice(0, 200);
  const ahora = Date.now();
  if ((vistos.get(clave) ?? 0) > ahora - 60_000) return;
  vistos.set(clave, ahora);
  enviados++;
  try {
    void fetch(`${API_URL}/api/registro/errores`, {
      method: "POST",
      credentials: "include",
      keepalive: true,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        mensaje: texto.slice(0, 2000),
        pila: pila ? pila.slice(0, 8000) : null,
        ruta: `${window.location.pathname}${window.location.search}`.slice(0, 500),
      }),
    }).catch(() => undefined);
  } catch {
    // Reportar nunca debe romper la pantalla.
  }
}

let instalado = false;

/** Escucha los errores que nadie atrapo (codigo y promesas). Se instala una vez al arrancar. */
export function instalarReporteDeErrores() {
  if (instalado) return;
  instalado = true;
  window.addEventListener("error", (e) => {
    reportarError(e.message || String(e.error ?? ""), e.error instanceof Error ? e.error.stack : null);
  });
  window.addEventListener("unhandledrejection", (e) => {
    const r: unknown = e.reason;
    reportarError(r instanceof Error ? r.message : String(r ?? ""), r instanceof Error ? r.stack : null);
  });
}
