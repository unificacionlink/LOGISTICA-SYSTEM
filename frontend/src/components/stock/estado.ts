import type { SaldoStock } from "../../api/types";

export type Estado = "SIN_STOCK" | "AGOTADO" | "BAJO_MINIMO" | "OK";

/**
 * SIN_STOCK: no hay nada y no tiene mínimo. AGOTADO: tiene mínimo y no queda
 * nada disponible. BAJO_MINIMO: lo disponible no llega al mínimo (reponer).
 */
export function estadoStock(s: SaldoStock): Estado {
  if (s.minimo != null && s.minimo > 0) {
    if (s.disponible <= 0) return "AGOTADO";
    if (s.disponible < s.minimo) return "BAJO_MINIMO";
  }
  return s.fisico <= 0 && s.reservado <= 0 ? "SIN_STOCK" : "OK";
}
