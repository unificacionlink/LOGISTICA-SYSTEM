// Filtro por familia: valor "t:<tipo>" o "f:<familia>" (ver FiltroFamilia).

export function coincideFamilia(valor: string, tipoId: string, familiaId: string): boolean {
  if (!valor) return true;
  return valor === `t:${tipoId}` || valor === `f:${familiaId}`;
}

/** "ACCESORIO › ELECTRICO": familia y subfamilia, como en el maestro SKU. */
export function etiquetaFamilia(tipoNombre: string, familiaNombre: string): string {
  return `${tipoNombre} › ${familiaNombre}`;
}
