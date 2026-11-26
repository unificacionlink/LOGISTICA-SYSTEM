import { http } from "./http";

// PDFs corporativos (RF-038/082/083). El nombre final lo pone el codigo del documento.

export function descargarPdfRequerimiento(requestId: string, codigo: string) {
  return http.descargar(`/api/requerimientos/${requestId}/pdf`, `${codigo}.pdf`);
}

export function descargarPdfTracking(requestId: string, codigo: string) {
  return http.descargar(`/api/requerimientos/${requestId}/tracking/pdf`, `TRACKING_${codigo}.pdf`);
}

/** Con el token recien generado, el PDF lleva el enlace del proveedor y su QR (DEC-L052). */
export function descargarPdfRfq(rfqId: string, codigo: string, tokenEnlace?: string) {
  const enlace = tokenEnlace ? `?enlace=${encodeURIComponent(tokenEnlace)}` : "";
  return http.descargar(`/api/compras/rfq/${rfqId}/pdf${enlace}`, `${codigo}.pdf`);
}

export function descargarPdfOrden(orderId: string, codigo: string) {
  return http.descargar(`/api/compras/ordenes/${orderId}/pdf`, `${codigo}.pdf`);
}

export function descargarPdfNota(noteId: string, codigo: string) {
  return http.descargar(`/api/almacen/notas/${noteId}/pdf`, `${codigo}.pdf`);
}

/** F-LOG-08 de la OC (DEC-L102). */
export function descargarPdfVerificacion(orderId: string, codigo: string) {
  return http.descargar(`/api/compras/ordenes/${orderId}/verificacion/pdf`, `F-LOG-08_${codigo}.pdf`);
}

/** F-LOG-08 de una OC desde la Nota de Inventario que despacha lo comprado. */
export function descargarPdfVerificacionDeNota(noteId: string, orderId: string, codigo: string) {
  return http.descargar(`/api/almacen/notas/${noteId}/verificaciones/${orderId}/pdf`, `F-LOG-08_${codigo}.pdf`);
}
