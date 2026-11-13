import type { ReactNode } from "react";

// No incluye ancho a propósito: los usos que ocupan todo el ancho deben
// agregar "w-full" explícitamente, para no pelear en la cascada con
// overrides de ancho como "w-20" o "flex-1" en carritos/tablas.
export const inputClass =
  "rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 " +
  "shadow-boton placeholder:text-slate-400 transition-[border-color,box-shadow] duration-200 hover:border-slate-300 " +
  "focus:border-brand-500 focus:outline-none focus:ring-3 focus:ring-brand-100 " +
  "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500";

export default function Field({
  label,
  required,
  hint,
  children,
  className = "",
}: {
  label?: ReactNode;
  required?: boolean;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <label className="text-[0.8125rem] font-medium text-slate-700">
          {label} {required && <span className="text-rose-600" aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  );
}
