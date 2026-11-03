import type { Desviacion, EstadoPresupuesto } from "../../api/types";

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

const cantidad = new Intl.NumberFormat("es-PE", { maximumFractionDigits: 3 });

/** Texto corto de una desviacion del control presupuestal (DEC-L038). */
export function textoDesviacion(d: Desviacion) {
  if (d.motivo === "SIN_PRESUPUESTO_APROBADO") return `${d.sku}: sin presupuesto aprobado`;
  if (d.motivo === "SKU_NO_PRESUPUESTADO") return `${d.sku}: no presupuestado (${cantidad.format(d.solicitado)})`;
  return `${d.sku}: excede en ${cantidad.format(d.exceso)} (disponible ${cantidad.format(d.disponible)})`;
}

export function etiquetaPeriodo(anio: number, semestre: number) {
  return `${anio}-S${semestre}`;
}

/** Los 6 meses del semestre (S1 = enero a junio). */
export function mesesDelSemestre(semestre: number) {
  return MESES.slice((semestre - 1) * 6, semestre * 6);
}
