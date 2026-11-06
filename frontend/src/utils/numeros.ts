/**
 * Deja en el texto solo un numero valido mientras se escribe: digitos y, si
 * se permiten decimales, un separador (la coma se toma como punto), con un
 * tope de digitos enteros y decimales. Evita "1e5", "-3", "2.5" en cantidades
 * y textos de largo infinito.
 */
export function limpiarNumero(texto: string, decimales: number, maxEnteros: number): string {
  const t = texto.replace(/,/g, ".").replace(/[^\d.]/g, "");
  const punto = t.indexOf(".");
  let enteros = punto === -1 ? t : t.slice(0, punto);
  enteros = enteros.replace(/^0+(?=\d)/, "").slice(0, maxEnteros);
  if (decimales <= 0 || punto === -1) return enteros;
  const fraccion = t
    .slice(punto + 1)
    .replace(/\./g, "")
    .slice(0, decimales);
  return `${enteros || "0"}.${fraccion}`;
}
