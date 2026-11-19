import type { ApiResponse } from "./http";

/**
 * Memoria de la pestana para las respuestas de la API (DEC-L051, DEC-L073).
 *
 * - Vive solo en la memoria de esta pestana: se vacia al iniciar o cerrar sesion
 *   y al recargar la pagina. Las respuestas de la API siguen con
 *   Cache-Control: no-store; nada privado de un usuario llega a otro usuario.
 * - Datos de referencia (catalogo, monedas, proveedores, clasificacion): `enCache`
 *   los reutiliza mientras esten vigentes.
 * - Datos del dia a dia (bandejas, stock, compras, almacen): `useDatos` muestra al
 *   instante lo ultimo que se vio y lo vuelve a pedir por detras. Siempre se
 *   pide de nuevo al abrir la pantalla; lo guardado solo evita la pantalla vacia.
 * - Tope de entradas: se descarta la menos usada que ninguna pantalla este mirando.
 */

export const MINUTO = 60_000;
const MAX_ENTRADAS = 60;

type Pedir = () => Promise<ApiResponse<unknown>>;

export interface Instantanea {
  /** Ultima respuesta correcta (lo que se muestra). */
  res: ApiResponse<unknown> | null;
  /** Ultima respuesta con error, si la ultima lectura fallo. */
  fallo: ApiResponse<unknown> | null;
  enCurso: boolean;
}

interface Entrada {
  snap: Instantanea;
  cargadoEn: number;
  /** Hasta cuando vale para `enCache` (0 = vieja). */
  vence: number;
  vieja: boolean;
  promesa: Promise<ApiResponse<unknown>> | null;
  /** Sube con cada lectura o cambio local: una respuesta vieja nunca pisa a una nueva. */
  version: number;
  pedir: Pedir | null;
  subs: Set<() => void>;
}

const memoria = new Map<string, Entrada>();
const VACIA: Instantanea = { res: null, fallo: null, enCurso: false };

function entrada(clave: string): Entrada {
  let e = memoria.get(clave);
  if (e) {
    // Reinsertar la deja al final: el orden del Map es el de uso.
    memoria.delete(clave);
  } else {
    e = { snap: VACIA, cargadoEn: 0, vence: 0, vieja: false, promesa: null, version: 0, pedir: null, subs: new Set() };
  }
  memoria.set(clave, e);
  recortar();
  return e;
}

function recortar() {
  if (memoria.size <= MAX_ENTRADAS) return;
  for (const [clave, e] of memoria) {
    if (memoria.size <= MAX_ENTRADAS) break;
    if (e.subs.size === 0 && !e.promesa) memoria.delete(clave);
  }
}

function publicar(e: Entrada, cambio: Partial<Instantanea>) {
  e.snap = { ...e.snap, ...cambio };
  e.subs.forEach((s) => s());
}

/** Una clave `familia:detalle` tambien se invalida al invalidar `familia`. */
function coincide(clave: string, familia: string) {
  return clave === familia || clave.startsWith(`${familia}:`);
}

/**
 * Pide la clave al servidor (o se suma a la lectura en curso) y guarda el
 * resultado. Si fallo, conserva lo ultimo bueno y anota el error.
 * `forzar`: tras una escritura, una lectura en curso pudo salir antes del
 * cambio; se descarta y se pide de nuevo.
 */
export function revalidar<T>(
  clave: string,
  pedir: () => Promise<ApiResponse<T>>,
  vigencia = 0,
  forzar = false,
): Promise<ApiResponse<T>> {
  const e = entrada(clave);
  if (e.promesa && !forzar) return e.promesa as Promise<ApiResponse<T>>;
  const version = ++e.version;
  const promesa = pedir().then((res) => {
    if (e.version === version) {
      e.promesa = null;
      if (res.ok) {
        e.cargadoEn = Date.now();
        e.vence = vigencia > 0 ? Date.now() + vigencia : 0;
        e.vieja = false;
        publicar(e, { res: res as ApiResponse<unknown>, fallo: null, enCurso: false });
      } else {
        publicar(e, { fallo: res as ApiResponse<unknown>, enCurso: false });
      }
    }
    return res;
  });
  e.promesa = promesa as Promise<ApiResponse<unknown>>;
  publicar(e, { enCurso: true });
  return promesa;
}

/**
 * Datos de referencia: devuelve la respuesta guardada si sigue vigente; si no,
 * la pide (y comparte la misma promesa entre pantallas que la pidan a la vez).
 * Un error no se guarda.
 */
export function enCache<T>(
  clave: string,
  vigencia: number,
  pedir: () => Promise<ApiResponse<T>>,
): Promise<ApiResponse<T>> {
  const e = memoria.get(clave);
  if (e?.snap.res && !e.vieja && e.vence > Date.now()) return Promise.resolve(e.snap.res as ApiResponse<T>);
  return revalidar(clave, pedir, vigencia);
}

/** Reemplaza el dato guardado (p. ej. con lo que devolvio el servidor al guardar). */
export function fijar<T>(clave: string, cambio: (previo: T | null) => T | null) {
  const e = entrada(clave);
  const previo = (e.snap.res?.result ?? null) as T | null;
  const base: ApiResponse<unknown> = e.snap.res ?? { ok: true, result: null, message: "", code: null, error: null };
  e.version++;
  e.promesa = null;
  e.cargadoEn = Date.now();
  e.vieja = false;
  publicar(e, { res: { ...base, result: cambio(previo) }, fallo: null, enCurso: false });
}

/**
 * Tras guardar algo que cambia estos datos. Las pantallas que lo estan mirando
 * lo vuelven a pedir por detras; las demas lo piden la proxima vez que abran.
 * `familia` tambien alcanza a sus variantes `familia:...` (p. ej. cada filtro).
 */
export function invalidar(...familias: string[]) {
  for (const [clave, e] of [...memoria]) {
    if (!familias.some((f) => coincide(clave, f))) continue;
    e.vieja = true;
    e.vence = 0;
    if (e.subs.size > 0 && e.pedir) void revalidar(clave, e.pedir, 0, true);
  }
}

/** Al iniciar o cerrar sesion: nada de un usuario queda para el siguiente. */
export function vaciarCache() {
  for (const [clave, e] of [...memoria]) {
    if (e.subs.size === 0) {
      memoria.delete(clave);
      continue;
    }
    // Una pantalla montada sigue suscrita a este objeto: se limpia en su lugar.
    e.version++;
    e.promesa = null;
    e.cargadoEn = 0;
    e.vence = 0;
    e.vieja = false;
    publicar(e, VACIA);
  }
}

/** Invalida las familias dadas cuando la escritura sale bien. */
export function luegoInvalidar<T>(promesa: Promise<ApiResponse<T>>, ...familias: string[]) {
  return promesa.then((res) => {
    if (res.ok) invalidar(...familias);
    return res;
  });
}

/** Para precargar al pasar el mouse: no pide si hay una lectura reciente o en curso. */
export function precargarDatos<T>(clave: string, pedir: () => Promise<ApiResponse<T>>, reciente = 15_000) {
  const e = memoria.get(clave);
  if (e && (e.promesa || (e.snap.res && !e.vieja && Date.now() - e.cargadoEn < reciente))) return;
  void revalidar(clave, pedir);
}

// --- Enganche para useDatos (useSyncExternalStore) ---

export function instantanea(clave: string | null): Instantanea {
  return (clave && memoria.get(clave)?.snap) || VACIA;
}

export function suscribir(clave: string, aviso: () => void, pedir: Pedir) {
  const e = entrada(clave);
  e.subs.add(aviso);
  e.pedir = pedir;
  return () => {
    e.subs.delete(aviso);
  };
}

/** Si conviene volver a pedir al abrir: no hay datos, quedaron viejos o pasaron `fresco` ms. */
export function hayQuePedir(clave: string, fresco: number) {
  const e = memoria.get(clave);
  if (!e?.snap.res) return true;
  return e.vieja || Date.now() - e.cargadoEn >= fresco;
}
