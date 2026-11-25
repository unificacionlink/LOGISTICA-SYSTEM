// Empresas del grupo en orden fijo (Requerimientos, dashboard). "marca" es el
// color que identifica a cada una; una empresa nueva en la BD cae al final con su codigo.
export const EMPRESAS: { code: string; label: string; marca: string }[] = [
  { code: "TDEMSRL", label: "TDEM", marca: "bg-blue-600" },
  { code: "GOYDELSAC", label: "GOYDEL", marca: "bg-emerald-600" },
  { code: "METRIN", label: "METRIN", marca: "bg-amber-500" },
];

/** Nombre corto de la empresa por su codigo (GOYDELSAC -> GOYDEL); el codigo si no esta en la lista. */
export function nombreEmpresa(code: string | null): string {
  return EMPRESAS.find((e) => e.code === code)?.label ?? code ?? "";
}
