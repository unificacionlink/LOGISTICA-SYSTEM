import { vaciarCache } from "../api/cache";

/**
 * DEC-L090: un usuario, una sesion. Si alguien ingresa con el mismo usuario en
 * otro lugar, esta sesion se cierra: lo avisa el servidor al responder
 * (code SESION_REEMPLAZADA) o en vivo, y el login muestra el motivo.
 */
export const EVENTO_SESION_REEMPLAZADA = "sesion:reemplazada";
const MARCA = "sesionReemplazada";

export function avisarSesionReemplazada() {
  vaciarCache();
  try {
    sessionStorage.setItem(MARCA, "1");
  } catch {
    // Sin almacenamiento igual se cierra la sesion; solo no se muestra el motivo.
  }
  window.dispatchEvent(new Event(EVENTO_SESION_REEMPLAZADA));
}

/** Lee y borra el aviso (para mostrarlo una sola vez en el login). */
export function tomarAvisoSesion(): boolean {
  try {
    const habia = sessionStorage.getItem(MARCA) === "1";
    sessionStorage.removeItem(MARCA);
    return habia;
  } catch {
    return false;
  }
}
