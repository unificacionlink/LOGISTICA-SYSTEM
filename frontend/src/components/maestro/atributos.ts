import type { Clasificacion, TipoAtributo } from "../../api/types";

export const ETIQUETA_ATRIBUTO: Record<TipoAtributo, string> = {
  TALLA: "Talla",
  COLOR: "Color",
  MODELO: "Modelo",
  OTRO: "Otro",
};

export function unidadPorDefecto(c: Clasificacion): string {
  return c.unidades.find((u) => u.code === "UND")?.id ?? c.unidades[0]?.id ?? "";
}
