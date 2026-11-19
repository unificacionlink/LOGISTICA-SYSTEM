import type { Clasificacion, TipoAtributo } from "../../api/types";

export const ETIQUETA_ATRIBUTO: Record<TipoAtributo, string> = {
  TALLA: "Talla",
  CLASE: "Clase",
  COLOR: "Color",
  MODELO: "Modelo",
  OTRO: "Otro",
};

export function unidadPorDefecto(c: Clasificacion): string {
  return c.unidades.find((u) => u.code === "UND")?.id ?? c.unidades[0]?.id ?? "";
}

/** Prefijo del codigo de variante por tipo de atributo (DEC-L075). */
const PREFIJO: Record<TipoAtributo, string> = { TALLA: "", CLASE: "C", COLOR: "CL", MODELO: "M", OTRO: "OT" };

/** attribute_code es varchar(20). */
const LARGO_MAXIMO = 20;

/**
 * Codigo del atributo tal como lo arma el backend (NomenclaturaVariante.java):
 * solo letras y numeros, sin tildes; quita el nombre del tipo si el valor lo
 * repite y, en clase, la C que la abrevia. Devuelve null si no es valido.
 */
export function codigoAtributo(tipo: TipoAtributo, valor: string): string | null {
  let limpio = valor
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  if (limpio.startsWith(tipo) && limpio.length > tipo.length) limpio = limpio.slice(tipo.length);
  if (tipo === "CLASE" && /^C[0-9]/.test(limpio)) limpio = limpio.slice(1);
  if (!limpio) return null;
  const codigo = PREFIJO[tipo] + limpio;
  return codigo.length > LARGO_MAXIMO ? null : codigo;
}
