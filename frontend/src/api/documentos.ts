import { http } from "./http";

// PDFs corporativos (RF-038/082/083). El nombre final lo pone el codigo del documento.

export function descargarPdfRequerimiento(requestId: string, codigo: string) {
  return http.descargar(`/api/requerimientos/${requestId}/pdf`, `${codigo}.pdf`);
}

export function descargarPdfTracking(requestId: string, codigo: string) {
  return http.descargar(`/api/requerimientos/${requestId}/tracking/pdf`, `TRACKING_${codigo}.pdf`);
}

export function descargarPdfRfq(rfqId: string, codigo: string) {
  return http.descargar(`/api/compras/rfq/${rfqId}/pdf`, `${codigo}.pdf`);
}

export function descargarPdfOrden(orderId: string, codigo: string) {
  return http.descargar(`/api/compras/ordenes/${orderId}/pdf`, `${codigo}.pdf`);
}

export function descargarPdfNota(noteId: string, codigo: string) {
  return http.descargar(`/api/almacen/notas/${noteId}/pdf`, `${codigo}.pdf`);
}
