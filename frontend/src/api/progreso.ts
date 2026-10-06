// Contador global de peticiones en curso, por tipo. Lo alimenta http.ts y lo
// lee la capa de carga, asi toda pantalla muestra actividad sin avisar a mano.

export type TipoActividad = "cargando" | "guardando" | "descargando";

type Listener = () => void;

const enCurso: Record<TipoActividad, number> = { cargando: 0, guardando: 0, descargando: 0 };
const listeners = new Set<Listener>();
// Instantanea estable para useSyncExternalStore (solo cambia al notificar).
let actual: TipoActividad | null = null;

function recalcular() {
  // Si hay de todo a la vez, se informa lo que mas le importa al usuario.
  actual = enCurso.guardando > 0 ? "guardando"
    : enCurso.descargando > 0 ? "descargando"
    : enCurso.cargando > 0 ? "cargando"
    : null;
  listeners.forEach((l) => l());
}

export const progreso = {
  inicio(tipo: TipoActividad) {
    enCurso[tipo]++;
    recalcular();
  },
  fin(tipo: TipoActividad) {
    enCurso[tipo] = Math.max(0, enCurso[tipo] - 1);
    recalcular();
  },
  actividad: () => actual,
  suscribir(l: Listener) {
    listeners.add(l);
    return () => {
      listeners.delete(l);
    };
  },
};

/** Envuelve una promesa para que cuente como actividad mientras dura. */
export async function conProgreso<T>(tipo: TipoActividad, tarea: () => Promise<T>): Promise<T> {
  progreso.inicio(tipo);
  try {
    return await tarea();
  } finally {
    progreso.fin(tipo);
  }
}
