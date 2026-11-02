import { useMemo } from "react";
import type { Familia } from "../api/types";
import { inputClass } from "./ui/Field";

/**
 * Filtro Familia › Subfamilia del maestro SKU V38 (DEC-L034). Un solo select:
 * cada familia (AC, EP...) agrupa sus subfamilias y se puede elegir entera.
 * Valor: "" (todas), "t:<tipoId>" (familia) o "f:<familyId>" (subfamilia).
 */
export default function FiltroFamilia({
  familias,
  value,
  onChange,
  className = "",
}: {
  familias: Familia[];
  value: string;
  onChange: (valor: string) => void;
  className?: string;
}) {
  const grupos = useMemo(() => {
    const porTipo = new Map<string, { tipoId: string; tipoNombre: string; subfamilias: Familia[] }>();
    for (const f of familias) {
      const g = porTipo.get(f.tipoId) ?? { tipoId: f.tipoId, tipoNombre: f.tipoNombre, subfamilias: [] };
      g.subfamilias.push(f);
      porTipo.set(f.tipoId, g);
    }
    return Array.from(porTipo.values());
  }, [familias]);

  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} className={`${inputClass} ${className}`}>
      <option value="">Todas las familias</option>
      {grupos.map((g) => (
        <optgroup key={g.tipoId} label={g.tipoNombre}>
          <option value={`t:${g.tipoId}`}>Todo {g.tipoNombre}</option>
          {g.subfamilias.map((f) => (
            <option key={f.familyId} value={`f:${f.familyId}`}>
              {f.nombre}
            </option>
          ))}
        </optgroup>
      ))}
    </select>
  );
}

export function coincideFamilia(valor: string, tipoId: string, familiaId: string): boolean {
  if (!valor) return true;
  return valor === `t:${tipoId}` || valor === `f:${familiaId}`;
}

/** "ACCESORIO › ELECTRICO": familia y subfamilia, como en el maestro SKU. */
export function etiquetaFamilia(tipoNombre: string, familiaNombre: string): string {
  return `${tipoNombre} › ${familiaNombre}`;
}
