import type { ComponentType } from "react";

export interface Pestana<T extends string> {
  id: T;
  label: string;
  icon?: ComponentType<{ className?: string }>;
  /** Numero o marca junto a la etiqueta (p. ej. documentos que faltan). */
  marca?: string | number | null;
}

/**
 * Pestanas de una pantalla (mismo estilo que Administracion). La pagina
 * guarda la activa en `?tab=` para que se pueda volver o compartir el enlace.
 */
export default function Pestanas<T extends string>({
  pestanas,
  activa,
  onCambiar,
}: {
  pestanas: Pestana<T>[];
  activa: T;
  onCambiar: (id: T) => void;
}) {
  return (
    <div role="tablist" className="mb-4 flex gap-1 overflow-x-auto border-b border-slate-200">
      {pestanas.map(({ id, label, icon: Icon, marca }) => (
        <button
          key={id}
          type="button"
          role="tab"
          aria-selected={activa === id}
          onClick={() => onCambiar(id)}
          className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activa === id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-700"
          }`}
        >
          {Icon && <Icon className="size-4" />}
          {label}
          {marca != null && marca !== 0 && (
            <span className="rounded-full bg-amber-100 px-1.5 text-[0.6875rem] font-semibold text-amber-800">{marca}</span>
          )}
        </button>
      ))}
    </div>
  );
}
