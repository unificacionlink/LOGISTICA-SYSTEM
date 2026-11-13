import type { BadgeTone } from "../../ui/Badge";

// Textos del registro de la plataforma (DEC-L060) en lenguaje de usuario.

const MODULOS: Record<string, string> = {
  auth: "Sesión",
  admin: "Administración",
  requerimientos: "Requerimientos",
  aprobaciones: "Aprobaciones",
  presupuestos: "Presupuestos",
  stock: "Stock",
  almacen: "Almacén",
  compras: "Compras",
  proveedores: "Proveedores",
  maestro: "Catálogo",
  cajachica: "Caja chica",
  tracking: "Tracking",
  dashboard: "Inicio",
  publico: "Enlace del proveedor",
};

export function etiquetaModulo(modulo: string | null): string {
  if (!modulo) return "—";
  return MODULOS[modulo] ?? modulo.charAt(0).toUpperCase() + modulo.slice(1);
}

const ES_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** "/api/requerimientos/{id}/enviar" -> "enviar"; "/api/auth/login" -> "Ingreso". */
export function etiquetaAccion(metodo: string, ruta: string): string {
  if (ruta === "/api/auth/login") return "Ingreso";
  if (ruta === "/api/auth/logout") return "Salida";
  const partes = ruta
    .replace(/^\/api\/[^/]+\/?/, "")
    .split("/")
    .filter((p) => p && !ES_ID.test(p) && !/^\d+$/.test(p) && p.length < 40);
  const texto = partes.join(" › ").replace(/-/g, " ");
  if (texto) return texto;
  return metodo === "POST" ? "crear" : metodo === "DELETE" ? "eliminar" : "actualizar";
}

export const ETIQUETA_METODO: Record<string, string> = {
  POST: "Crear / ejecutar",
  PUT: "Actualizar",
  PATCH: "Modificar",
  DELETE: "Eliminar",
};

export function resultado(status: number | null): { label: string; tono: BadgeTone } {
  if (status == null) return { label: "—", tono: "gris" };
  if (status < 400) return { label: "Correcto", tono: "verde" };
  if (status === 401 || status === 403) return { label: "Sin permiso", tono: "amarillo" };
  if (status < 500) return { label: "Rechazado", tono: "amarillo" };
  return { label: "Error", tono: "rojo" };
}

/** Navegador y sistema en pocas palabras ("Chrome · Windows"). */
export function navegadorCorto(ua: string | null): string {
  if (!ua) return "—";
  const nav = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\//.test(ua)
      ? "Opera"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /Chrome\//.test(ua)
          ? "Chrome"
          : /Safari\//.test(ua)
            ? "Safari"
            : "Otro";
  const so = /Windows/.test(ua)
    ? "Windows"
    : /Android/.test(ua)
      ? "Android"
      : /iPhone|iPad/.test(ua)
        ? "iOS"
        : /Mac OS X/.test(ua)
          ? "Mac"
          : /Linux/.test(ua)
            ? "Linux"
            : "";
  return so ? `${nav} · ${so}` : nav;
}

const ENTIDADES: Record<string, string> = {
  REQUERIMIENTO: "Requerimiento",
  PRESUPUESTO: "Presupuesto",
  PERIODO_PRESUPUESTAL: "Periodo presupuestal",
  NECESIDAD_COMPRA: "Necesidad de compra",
  CONSOLIDACION: "Consolidado",
  RFQ: "Solicitud de cotización",
  COTIZACION: "Cotización",
  ORDEN_ABASTECIMIENTO: "Orden de compra",
  CAJA_CHICA: "Caja chica",
  CARGA_STOCK: "Carga de inventario",
  PRODUCTO: "Producto",
  PROVEEDOR: "Proveedor",
  PERSONAL: "Personal",
  CECO: "CECO",
};

export function etiquetaEntidad(entidad: string): string {
  return ENTIDADES[entidad] ?? entidad.replace(/_/g, " ").toLowerCase().replace(/^./, (c) => c.toUpperCase());
}

export function etiquetaAccionCambio(accion: string): string {
  return accion.replace(/_/g, " ").toLowerCase().replace(/^./, (c) => c.toUpperCase());
}

/** Pantalla de la entidad de un cambio, si tiene una propia. */
export function enlaceEntidad(entidad: string, id: string): string | null {
  switch (entidad) {
    case "REQUERIMIENTO":
      return `/requerimientos/${id}`;
    case "PRESUPUESTO":
      return `/presupuestos/${id}`;
    case "CONSOLIDACION":
      return `/compras/consolidados/${id}`;
    case "RFQ":
      return `/compras/rfq/${id}`;
    case "ORDEN_ABASTECIMIENTO":
      return `/compras/ordenes/${id}`;
    default:
      return null;
  }
}

/** Pantalla de lo que toco una operacion, deducida de su ruta. */
export function enlaceRuta(ruta: string, entidadId: string | null): string | null {
  if (!entidadId) return null;
  if (ruta.startsWith("/api/requerimientos/")) return `/requerimientos/${entidadId}`;
  if (ruta.startsWith("/api/presupuestos/") && !ruta.includes("/periodos")) return `/presupuestos/${entidadId}`;
  if (ruta.startsWith("/api/compras/rfq/") || ruta.startsWith("/api/compras/rfqs/")) return `/compras/rfq/${entidadId}`;
  if (ruta.startsWith("/api/compras/ordenes/")) return `/compras/ordenes/${entidadId}`;
  if (ruta.startsWith("/api/compras/consolidados/")) return `/compras/consolidados/${entidadId}`;
  return null;
}

export const TIPOS_MOVIMIENTO: Record<string, string> = {
  ENTRADA: "Entrada",
  SALIDA: "Salida",
  RESERVA: "Reserva",
  AJUSTE: "Ajuste",
  PRESTAMO: "Préstamo",
  DEVOLUCION: "Devolución",
};

/** JSON de log_cambios legible; si no es JSON valido se muestra tal cual. */
export function jsonLegible(texto: string | null): string | null {
  if (!texto) return null;
  try {
    return JSON.stringify(JSON.parse(texto), null, 2);
  } catch {
    return texto;
  }
}

export function fechaHora(iso: string): string {
  return new Date(iso).toLocaleString("es-PE", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
