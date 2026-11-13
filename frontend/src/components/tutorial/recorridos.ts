/**
 * Recorridos guiados para usuarios nuevos (DEC-L055). Cada paso puede llevar
 * a una pantalla (`ruta`) y resaltar un elemento marcado con
 * `data-tutorial="<ancla>"`; sin ancla (o si no esta visible, p. ej. el menu
 * lateral en el celular) el globo sale centrado.
 *
 * Para agregar un paso: marca el elemento con `data-tutorial` y suma la entrada
 * aqui. Textos cortos, en lenguaje de usuario.
 */
export const EVENTO_RECORRIDO = "recorrido:iniciar";

/** Abre un recorrido desde cualquier parte (menu, busqueda rapida); lo muestra `RecorridoGuiado`. */
export function iniciarRecorrido(id: string) {
  window.dispatchEvent(new CustomEvent<string>(EVENTO_RECORRIDO, { detail: id }));
}

export interface Paso {
  /** Pantalla del paso (con `?tab=`/`?vista=` si hace falta); sin ruta se queda donde esta. */
  ruta?: string;
  ancla?: string;
  titulo: string;
  texto: string;
}

export interface Recorrido {
  id: string;
  titulo: string;
  /** Quien lo ve en la busqueda rapida. null = todos. */
  roles: readonly string[] | null;
  pasos: Paso[];
}

const AYUDA: Paso = {
  ancla: "menu-ayuda",
  titulo: "Vuelve a verlo cuando quieras",
  texto: "Toca «Ver tutorial» en el menú. Con Ctrl+K puedes ir a cualquier pantalla.",
};

export const RECORRIDOS: Recorrido[] = [
  {
    id: "solicitante",
    titulo: "Cómo pedir y seguir un requerimiento",
    roles: null,
    pasos: [
      {
        titulo: "Te damos la bienvenida",
        texto: "Aquí pides lo que necesitas para tu proyecto y sigues el pedido hasta que lo recibes. Te mostramos cómo en 2 minutos.",
      },
      {
        ruta: "/requerimientos",
        ancla: "menu/requerimientos",
        titulo: "Requerimientos",
        texto: "Desde el menú entras a tus pedidos.",
      },
      {
        ruta: "/requerimientos",
        ancla: "req-nuevo",
        titulo: "Crea un pedido",
        texto: "Con «Nuevo requerimiento» empiezas uno.",
      },
      {
        ruta: "/requerimientos/nuevo",
        ancla: "req-datos",
        titulo: "Datos del pedido",
        texto: "Elige el centro de costo (solo ves los tuyos), la prioridad, el propósito y para cuándo lo necesitas.",
      },
      {
        ruta: "/requerimientos/nuevo",
        ancla: "req-buscador",
        titulo: "Agrega productos",
        texto: "Búscalos por nombre o código y tócalos para sumarlos. Verás cuánto hay en el almacén; lo que falte se compra.",
      },
      {
        ruta: "/requerimientos/nuevo",
        ancla: "req-pedido",
        titulo: "Tu pedido",
        texto: "Ajusta las cantidades. Si una parte conviene comprarla con la caja chica de tu proyecto, indícalo en cada producto.",
      },
      {
        ruta: "/requerimientos/nuevo",
        ancla: "req-enviar",
        titulo: "Solicitar",
        texto: "Revisas el resumen del pedido y lo envías a tu aprobador en un solo paso. Te avisamos al instante cuando lo apruebe u observe.",
      },
      {
        ruta: "/requerimientos",
        ancla: "req-vistas",
        titulo: "Sigue tu pedido",
        texto: "Aquí ves lo pendiente, lo que está en proceso y lo cerrado. Toca un pedido para ver su avance y confirmar la entrega.",
      },
      AYUDA,
    ],
  },
  {
    id: "logistica",
    titulo: "Recorrido de Logística",
    roles: ["LOGISTICA", "ADMIN"],
    pasos: [
      {
        titulo: "Te damos la bienvenida",
        texto: "Te mostramos el camino de un pedido: del requerimiento aprobado a la compra, al almacén y a la entrega.",
      },
      {
        ruta: "/requerimientos",
        ancla: "req-vistas",
        titulo: "Bandeja de trabajo",
        texto: "Los pedidos se ordenan por lo que hay que hacer. Cada uno muestra su siguiente paso y te lleva a hacerlo.",
      },
      {
        ruta: "/compras?tab=necesidades",
        ancla: "compras-tab-necesidades",
        titulo: "Necesidades",
        texto: "Lo que falta de cada pedido aprobado. Aquí decides si sale del almacén, se compra o va por caja chica.",
      },
      {
        ruta: "/compras?tab=consolidados",
        ancla: "compras-tab-consolidados",
        titulo: "Consolidados",
        texto: "Lo que piden varios centros de costo del mismo producto se junta para comprarlo una sola vez.",
      },
      {
        ruta: "/compras?tab=consolidados",
        ancla: "compras-acciones",
        titulo: "Cotizar y comparar",
        texto: "Marca los productos y pide cotización a sus proveedores, o sube hasta 3 cotizaciones para compararlas y adjudicar.",
      },
      {
        ruta: "/compras?tab=solicitudes",
        ancla: "compras-tab-solicitudes",
        titulo: "Solicitudes de cotización",
        texto: "Abre una para descargar su PDF y hacérsela llegar al proveedor, generar el enlace para que cotice en línea o registrar su cotización.",
      },
      {
        ruta: "/compras?tab=solicitudes",
        ancla: "compras-ordenes",
        titulo: "Órdenes de compra",
        texto: "Las órdenes que emites desde el comparativo quedan aquí, con su PDF.",
      },
      {
        ruta: "/almacen?tab=recibir",
        ancla: "almacen-tab-recibir",
        titulo: "Recibir",
        texto: "Cuando llega la mercadería, registra la recepción de la orden. Se reparte sola entre los pedidos de origen.",
      },
      {
        ruta: "/almacen?tab=despachar",
        ancla: "almacen-tab-despachar",
        titulo: "Despachar",
        texto: "Despacha lo reservado con su nota de inventario. Quien pidió confirma la entrega.",
      },
      AYUDA,
    ],
  },
];

/** Recorridos que puede ver un usuario segun sus roles. */
export function recorridosPara(tieneAlgunRol: (...roles: string[]) => boolean): Recorrido[] {
  return RECORRIDOS.filter((r) => !r.roles || tieneAlgunRol(...r.roles));
}

/** El que se ofrece en el primer ingreso: Logistica/Admin el suyo; el resto, el de pedir. */
export function recorridoInicial(tieneAlgunRol: (...roles: string[]) => boolean): Recorrido {
  return RECORRIDOS.find((r) => r.id === (tieneAlgunRol("LOGISTICA", "ADMIN") ? "logistica" : "solicitante"))!;
}
