import type { BadgeTone } from "../ui/Badge";
import type { EstadoProveedor, ResultadoEvaluacion, TipoEvaluacion, Vigencia } from "../../api/types";

// Etiquetas del modulo de Proveedores (DEC-L069/L070), aparte de los
// componentes para que la recarga en caliente siga funcionando.

export const ESTADO_PROVEEDOR: Record<EstadoProveedor, { label: string; tono: BadgeTone }> = {
  BORRADOR: { label: "Borrador", tono: "gris" },
  PENDIENTE_REVISION: { label: "En revisión", tono: "azul" },
  HABILITADO: { label: "Habilitado", tono: "verde" },
  OBSERVADO: { label: "Observado", tono: "amarillo" },
  RECHAZADO: { label: "Rechazado", tono: "rojo" },
};

export const VIGENCIA: Record<Vigencia | "FALTA", { label: string; tono: BadgeTone }> = {
  VIGENTE: { label: "Vigente", tono: "verde" },
  PROXIMO_A_VENCER: { label: "Por vencer", tono: "amarillo" },
  VENCIDO: { label: "Vencida", tono: "rojo" },
  SIN_EVALUACION: { label: "Sin evaluación", tono: "gris" },
  FALTA: { label: "Falta", tono: "gris" },
};

export const RESULTADO: Record<ResultadoEvaluacion, { label: string; tono: BadgeTone }> = {
  APROBADO: { label: "Aprobado", tono: "verde" },
  NO_APROBADO: { label: "No aprobado", tono: "rojo" },
  PENDIENTE_CONFORMIDAD: { label: "Conformidad pendiente", tono: "amarillo" },
};

export const TIPO_EVALUACION: Record<TipoEvaluacion, string> = {
  SELECCION: "Selección",
  DESEMPENO: "Evaluación de desempeño",
};

export const ACCION_HISTORIAL: Record<string, string> = {
  CREAR: "Registro",
  EDITAR: "Edición de datos",
  CATEGORIAS: "Categorías",
  PRODUCTOS: "Productos",
  ENVIAR_REVISION: "Enviado a revisión",
  HABILITAR: "Habilitado",
  OBSERVAR: "Observado",
  RECHAZAR: "Rechazado",
  REABRIR: "Reabierto",
  ACTIVAR: "Activado",
  DESACTIVAR: "Desactivado",
  SELECCION: "Selección registrada",
  EVALUACION: "Evaluación registrada",
  ANULAR_EVALUACION: "Evaluación anulada",
  SUBIR_DOCUMENTO: "Documento subido",
  ANULAR_DOCUMENTO: "Documento anulado",
  QUITAR_EQUIVALENCIA: "Equivalencia quitada",
};

export const ESTADO_OC: Record<string, string> = {
  POR_APROBAR: "Por aprobar",
  EMITIDA: "Emitida",
  EN_ATENCION: "En atención",
  RECEPCION_PARCIAL: "Recepción parcial",
  RECEPCION_COMPLETA: "Recepción completa",
  PEND_VERIFICACION: "Por verificar",
  VERIFICADA: "Verificada",
  CERRADA: "Cerrada",
  ANULADA: "Anulada",
};

export const ESTADO_COTIZACION: Record<string, string> = {
  RECIBIDA: "Recibida",
  VALIDADA: "Validada",
  DESCARTADA: "Descartada",
  SELECCIONADA: "Adjudicada",
};

/** Nota con hasta 2 decimales y sin ceros de relleno (8.50 -> 8.5). */
export function fmtNota(n: number | null | undefined): string {
  if (n === null || n === undefined) return "—";
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: 2 });
}

/** Proveedor operativo para Compras: habilitado y activo. */
export function etiquetaEstado(status: EstadoProveedor, activo: boolean): { label: string; tono: BadgeTone } {
  if (status === "HABILITADO" && !activo) return { label: "Inactivo", tono: "gris" };
  return ESTADO_PROVEEDOR[status];
}
