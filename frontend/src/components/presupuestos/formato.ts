import type { EstadoPresupuesto } from "../../api/types";

export const ETIQUETA_PRESUPUESTO: Record<EstadoPresupuesto, string> = {
  BORRADOR: "Borrador",
  EMITIDO: "Emitido",
  APROBADO: "Aprobado",
  OBSERVADO: "Observado",
  MODIFICADO: "En modificación",
  ANULADO: "Anulado",
};

export const soles = new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" });

const MESES = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic"];

export function etiquetaPeriodo(anio: number, semestre: number) {
  return `${anio}-S${semestre}`;
}

/** Los 6 meses del semestre (S1 = enero a junio). */
export function mesesDelSemestre(semestre: number) {
  return MESES.slice((semestre - 1) * 6, semestre * 6);
}
