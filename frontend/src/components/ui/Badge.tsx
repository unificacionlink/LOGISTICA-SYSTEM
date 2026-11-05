import type { ReactNode } from "react";

export type BadgeTone = "gris" | "azul" | "verde" | "amarillo" | "rojo";

const tones: Record<BadgeTone, string> = {
  gris: "bg-slate-100 text-slate-600 ring-slate-200/70",
  azul: "bg-blue-50 text-blue-800 ring-blue-100",
  verde: "bg-emerald-50 text-emerald-700 ring-emerald-100",
  amarillo: "bg-amber-50 text-amber-700 ring-amber-100",
  rojo: "bg-rose-50 text-rose-700 ring-rose-100",
};

export default function Badge({
  tone = "gris",
  children,
  className = "",
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={`badge inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]} ${className}`}
    >
      {children}
    </span>
  );
}
