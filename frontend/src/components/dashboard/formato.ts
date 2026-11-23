// Formato del dashboard (DEC-L083). Aparte de los componentes para que esos
// archivos solo exporten componentes (recarga en caliente).

// Un solo tono para todas las marcas de datos (series unicas: no hay identidad
// que codificar). El azul TDEM ya es el acento de la app.
export const MARCA = "bg-brand-500";

export function fmtNum(n: number | null | undefined, decimales = 1): string {
  if (n === null || n === undefined) return "—";
  return Number(n).toLocaleString("es-PE", { maximumFractionDigits: decimales });
}

export function simboloMoneda(moneda: string): string {
  return moneda === "PEN" ? "S/" : moneda === "USD" ? "US$" : moneda;
}

export function fmtMonto(moneda: string, n: number, decimales = 2): string {
  return `${simboloMoneda(moneda)} ${Number(n).toLocaleString("es-PE", {
    minimumFractionDigits: decimales,
    maximumFractionDigits: decimales,
  })}`;
}

/** Monto corto para ejes y barras: S/ 12,5 mil, S/ 1,2 M. */
export function fmtMontoCorto(moneda: string, n: number): string {
  const abs = Math.abs(n);
  if (abs >= 1_000_000) return `${simboloMoneda(moneda)} ${fmtNum(n / 1_000_000)} M`;
  if (abs >= 10_000) return `${simboloMoneda(moneda)} ${fmtNum(n / 1000)} mil`;
  return fmtMonto(moneda, n, 0);
}

const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

/** "2026-11" -> "nov 26". */
export function fmtMes(clave: string): string {
  const [a, m] = clave.split("-");
  return `${MESES[Number(m) - 1] ?? m} ${a.slice(2)}`;
}

export function pct(parte: number, total: number): number | null {
  return total > 0 ? Math.round((parte / total) * 100) : null;
}
