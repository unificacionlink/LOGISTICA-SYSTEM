import { http } from "./http";
import type { CargaStock, Kardex, SaldoStock } from "./types";

// Stock del almacén central del grupo (DEC-L045): no se elige empresa.

export function listarSaldosStock() {
  return http.get<SaldoStock[]>("/api/stock/saldos");
}

export function obtenerKardex(variantId: string) {
  return http.get<Kardex>(`/api/stock/variantes/${variantId}/kardex`);
}

export interface AjusteStockInput {
  variantId: string;
  /** Con signo: ingreso positivo, salida negativa. */
  qty: number;
  motivo: string;
}

export function registrarAjusteStock(input: AjusteStockInput) {
  return http.post<SaldoStock>("/api/stock/ajustes", input);
}

/** null quita el mínimo. */
export function fijarMinimoStock(variantId: string, minimo: number | null) {
  return http.put<SaldoStock>(`/api/stock/variantes/${variantId}/minimo`, { minimo });
}

// ---------- Inventario por Excel ----------

export function descargarPlantillaInventario() {
  const hoy = new Date().toISOString().slice(0, 10);
  return http.descargar("/api/stock/carga/plantilla", `INVENTARIO_${hoy}.xlsx`);
}

function formArchivo(file: File) {
  const form = new FormData();
  form.append("file", file);
  return form;
}

export function validarInventario(file: File) {
  return http.postForm<CargaStock>("/api/stock/carga/validar", formArchivo(file));
}

export function confirmarInventario(file: File) {
  return http.postForm<CargaStock>("/api/stock/carga/confirmar", formArchivo(file));
}
