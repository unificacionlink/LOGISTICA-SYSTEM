import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { ApiResponse } from "./http";
import { fijar, hayQuePedir, instantanea, revalidar, suscribir } from "./cache";

export interface Datos<T> {
  datos: T | null;
  /** Solo es verdadero si todavia no hay nada que mostrar: ahi van los esqueletos. */
  cargando: boolean;
  /** Hay datos en pantalla y se estan pidiendo de nuevo por detras. */
  actualizando: boolean;
  /** Con `conservar`: lo que se ve es de la clave anterior (p. ej. otro filtro). */
  previo: boolean;
  /** Mensaje de la ultima lectura fallida (los datos anteriores siguen en pantalla). */
  error: string | null;
  /** Vuelve a pedir sin vaciar la pantalla. */
  recargar: () => Promise<ApiResponse<T>>;
  /** Pone a mano el dato (lo que devolvio el servidor al guardar), sin volver a pedir. */
  mutar: (cambio: T | ((previo: T | null) => T | null)) => void;
}

interface Opciones {
  /** Cuanto tiempo (ms) se considera fresco lo guardado. 0 = siempre se pide al abrir. */
  fresco?: number;
  /** Al cambiar la clave (p. ej. un filtro), sigue mostrando lo anterior hasta que llegue lo nuevo. */
  conservar?: boolean;
}

/**
 * Lectura de la API con memoria de la pestana (DEC-L073): al volver a una
 * pantalla se ve al instante lo ultimo cargado y se actualiza por detras.
 * `clave` identifica la lectura con sus filtros (`familia:filtros`); con `null`
 * no se pide nada. Las escrituras invalidan su familia con `luegoInvalidar`.
 */
export function useDatos<T>(
  clave: string | null,
  pedir: () => Promise<ApiResponse<T>>,
  { fresco = 0, conservar = false }: Opciones = {},
): Datos<T> {
  const pedirRef = useRef(pedir);
  useEffect(() => {
    pedirRef.current = pedir;
  });

  const suscribirse = useCallback(
    (aviso: () => void) => (clave ? suscribir(clave, aviso, () => pedirRef.current()) : () => {}),
    [clave],
  );
  const leer = useCallback(() => instantanea(clave), [clave]);
  const snap = useSyncExternalStore(suscribirse, leer, leer);

  useEffect(() => {
    if (clave && hayQuePedir(clave, fresco)) void revalidar(clave, () => pedirRef.current());
  }, [clave, fresco]);

  const recargar = useCallback(
    () =>
      clave
        ? revalidar(clave, () => pedirRef.current(), 0, true)
        : Promise.resolve<ApiResponse<T>>({ ok: false, result: null, message: "", code: null, error: null }),
    [clave],
  );

  const mutar = useCallback(
    (cambio: T | ((previo: T | null) => T | null)) => {
      if (!clave) return;
      fijar<T>(clave, (previo) =>
        typeof cambio === "function" ? (cambio as (p: T | null) => T | null)(previo) : cambio,
      );
    },
    [clave],
  );

  const res = snap.res as ApiResponse<T> | null;
  const actual = res?.result ?? null;
  // Lo ultimo mostrado, ajustado durante el render (sin efecto en cascada).
  const [anterior, setAnterior] = useState<T | null>(null);
  if (conservar && actual !== null && actual !== anterior) setAnterior(actual);
  const prestado = conservar && actual === null && clave !== null ? anterior : null;

  return {
    datos: actual ?? prestado,
    cargando: clave !== null && !res && !snap.fallo && prestado === null,
    actualizando: (!!res && snap.enCurso) || prestado !== null,
    previo: prestado !== null,
    error: snap.fallo ? snap.fallo.message || "No se pudo cargar." : null,
    recargar,
    mutar,
  };
}
