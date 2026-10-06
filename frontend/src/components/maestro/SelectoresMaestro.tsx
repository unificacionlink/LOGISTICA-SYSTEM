import type { Clasificacion, TipoAtributo } from "../../api/types";
import { inputClass } from "../ui/Field";

export const ETIQUETA_ATRIBUTO: Record<TipoAtributo, string> = {
  TALLA: "Talla",
  COLOR: "Color",
  MODELO: "Modelo",
  OTRO: "Otro",
};

/** Familia agrupada por tipo (EPP, Herramienta...): una sola lista, sin elegir el tipo aparte. */
export function SelectorFamilia({
  clasificacion,
  value,
  onChange,
  id,
}: {
  clasificacion: Clasificacion;
  value: string;
  onChange: (familyId: string) => void;
  id?: string;
}) {
  return (
    <select id={id} required value={value} onChange={(e) => onChange(e.target.value)} className={`${inputClass} w-full`}>
      <option value="" disabled>
        Selecciona…
      </option>
      {clasificacion.tipos
        .filter((t) => t.familias.length > 0)
        .map((t) => (
          <optgroup key={t.id} label={t.nombre}>
            {t.familias.map((f) => (
              <option key={f.id} value={f.id}>
                {f.nombre}
              </option>
            ))}
          </optgroup>
        ))}
    </select>
  );
}

export function SelectorUnidad({
  clasificacion,
  value,
  onChange,
}: {
  clasificacion: Clasificacion;
  value: string;
  onChange: (uomId: string) => void;
}) {
  return (
    <select required value={value} onChange={(e) => onChange(e.target.value)} className={`${inputClass} w-full`}>
      <option value="" disabled>
        Selecciona…
      </option>
      {clasificacion.unidades.map((u) => (
        <option key={u.id} value={u.id}>
          {u.nombre}
        </option>
      ))}
    </select>
  );
}

export function unidadPorDefecto(c: Clasificacion): string {
  return c.unidades.find((u) => u.code === "UND")?.id ?? c.unidades[0]?.id ?? "";
}
