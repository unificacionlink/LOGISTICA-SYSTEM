import type { AccionBandeja, CodigoAccion } from "../../api/types";

/**
 * Siguiente accion de un requerimiento (DEC-L048): etiqueta y pantalla
 * existente donde se ejecuta. Las de espera solo informan (dependen de otro).
 */
const DEF: Record<CodigoAccion, { label: string; ruta: (destino: string, reqId: string) => string }> = {
  DESPACHAR: { label: "Despachar", ruta: (d) => `/almacen/requerimientos/${d}/despacho` },
  EMITIR_OC: { label: "Emitir OC", ruta: () => "/compras" },
  ADJUDICAR: { label: "Adjudicar", ruta: (d) => `/compras/comparativo?ids=${d}` },
  REGISTRAR_CAJA_CHICA: { label: "Caja chica", ruta: (d) => `/compras/requerimientos/${d}/caja-chica` },
  DECIDIR_RUTA: { label: "Decidir ruta", ruta: () => "/compras?tab=necesidades" },
  PEDIR_COTIZACION: { label: "Cotizar", ruta: (d) => `/compras/comparativo?ids=${d}` },
  ESPERA_COTIZACION: { label: "Esperando cotización", ruta: (d) => `/compras/comparativo?ids=${d}` },
  RECIBIR_OC: { label: "OC por recibir", ruta: (d) => `/almacen/ordenes/${d}/recepcion` },
  CONFIRMAR_ENTREGA: { label: "En tránsito", ruta: (d) => `/almacen/notas/${d}` },
  APROBACION: { label: "En aprobación", ruta: (_d, id) => `/requerimientos/${id}` },
  CORRECCION: { label: "Espera corrección", ruta: (_d, id) => `/requerimientos/${id}` },
  ENVIAR: { label: "Enviar", ruta: (_d, id) => `/requerimientos/${id}` },
  CORREGIR: { label: "Corregir", ruta: (_d, id) => `/requerimientos/${id}/editar` },
};

export function etiquetaAccion(a: AccionBandeja): string {
  return DEF[a.codigo]?.label ?? a.codigo;
}

export function rutaAccion(a: AccionBandeja, reqId: string): string {
  return DEF[a.codigo]?.ruta(a.destino ?? reqId, reqId) ?? `/requerimientos/${reqId}`;
}

const DE_COMPRAS: CodigoAccion[] = [
  "EMITIR_OC",
  "ADJUDICAR",
  "REGISTRAR_CAJA_CHICA",
  "DECIDIR_RUTA",
  "PEDIR_COTIZACION",
  "ESPERA_COTIZACION",
  "RECIBIR_OC",
];

/**
 * La primera accion que el usuario puede hacer (en la vista Compras o Despacho,
 * la de esa vista); si todo depende de otros, la primera espera.
 */
export function accionPrincipal(acciones: AccionBandeja[], vista?: string): AccionBandeja | null {
  const propia =
    vista === "compras"
      ? acciones.find((a) => DE_COMPRAS.includes(a.codigo))
      : vista === "despacho"
        ? acciones.find((a) => a.codigo === "DESPACHAR")
        : undefined;
  return propia ?? acciones.find((a) => !a.espera) ?? acciones[0] ?? null;
}
