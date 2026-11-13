import type { Clasificacion } from "../../api/types";
import { inputClass } from "../ui/Field";

/** Subfamilia agrupada por familia (EPP, Herramienta...), como el maestro SKU V38 (DEC-L034): una sola lista. */
export function SelectorFamilia({
  clasificacion,
  value,
  onChange,
  id,
  requerido = true,
}: {
  clasificacion: Clasificacion;
  value: string;
  onChange: (familyId: string) => void;
  id?: string;
  /** false si va dentro de otro formulario (su validacion nativa lo bloquearia). */
  requerido?: boolean;
}) {
  return (
    <select id={id} required={requerido} aria-required value={value} onChange={(e) => onChange(e.target.value)} className={`${inputClass} w-full`}>
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
  requerido = true,
}: {
  clasificacion: Clasificacion;
  value: string;
  onChange: (uomId: string) => void;
  /** false si va dentro de otro formulario (su validacion nativa lo bloquearia). */
  requerido?: boolean;
}) {
  return (
    <select required={requerido} aria-required value={value} onChange={(e) => onChange(e.target.value)} className={`${inputClass} w-full`}>
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
