import type { ApiResponse } from "./http";

/**
 * Cache en memoria de la pestana para datos de referencia (catalogo, monedas,
 * proveedores, clasificacion). No es una cache compartida: vive en la memoria de
 * esta pestana, se vacia al iniciar o cerrar sesion y al recargar la pagina, y
 * las respuestas de la API siguen con Cache-Control: no-store. Nada privado de
 * un usuario llega a otro usuario.
 *
 * Datos que cambian seguido o son del dia a dia (stock, bandejas, aprobaciones,
 * dashboard) no pasan por aqui: se piden al abrir cada pantalla.
 */

export const MINUTO = 60_000;

interface Entrada {
  vence: number;
  promesa: Promise<ApiResponse<unknown>>;
}

const memoria = new Map<string, Entrada>();

/**
 * Devuelve la respuesta guardada si sigue vigente; si no, la pide (y comparte la
 * misma promesa entre pantallas que la pidan a la vez). Un error no se guarda.
 */
export function enCache<T>(
  clave: string,
  vigencia: number,
  pedir: () => Promise<ApiResponse<T>>,
): Promise<ApiResponse<T>> {
  const previa = memoria.get(clave);
  if (previa && previa.vence > Date.now()) return previa.promesa as Promise<ApiResponse<T>>;
  const promesa: Promise<ApiResponse<T>> = pedir().then((res) => {
    if (!res.ok && memoria.get(clave)?.promesa === promesa) memoria.delete(clave);
    return res;
  });
  memoria.set(clave, { vence: Date.now() + vigencia, promesa });
  return promesa;
}

/** Tras guardar algo que cambia estos datos: la proxima lectura va al servidor. */
export function invalidar(...claves: string[]) {
  for (const c of claves) memoria.delete(c);
}

/** Al iniciar o cerrar sesion: nada de un usuario queda para el siguiente. */
export function vaciarCache() {
  memoria.clear();
}

/** Invalida las claves dadas cuando la escritura sale bien. */
export function luegoInvalidar<T>(promesa: Promise<ApiResponse<T>>, ...claves: string[]) {
  return promesa.then((res) => {
    if (res.ok) invalidar(...claves);
    return res;
  });
}
