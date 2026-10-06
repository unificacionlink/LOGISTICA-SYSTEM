import { http } from "./http";
import type { EmpresaOpcion, SaldoAjustado, SaldoStock } from "./types";

export function listarSaldosStock(companyId: string) {
  return http.get<SaldoStock[]>(`/api/stock/saldos?companyId=${companyId}`);
}

export interface AjusteStockInput {
  companyId: string;
  variantId: string;
  qty: number;
  comment?: string;
}

export function registrarAjusteStock(input: AjusteStockInput) {
  return http.post<SaldoAjustado>("/api/stock/ajustes", input);
}

export function listarEmpresasStock() {
  return http.get<EmpresaOpcion[]>("/api/stock/empresas");
}
