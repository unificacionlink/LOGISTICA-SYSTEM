import type { ReactNode } from "react";

export type BadgeTone = "gris" | "azul" | "verde" | "amarillo" | "rojo";

const tones: Record<BadgeTone, string> = {
  gris: "bg-slate-100 text-slate-600",
  azul: "bg-blue-50 text-blue-800",
  verde: "bg-emerald-50 text-emerald-700",
  amarillo: "bg-amber-50 text-amber-700",
  rojo: "bg-rose-50 text-rose-700",
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
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-[11px] font-medium uppercase tracking-[0.05em] ${tones[tone]} ${className}`}
    >
      {children}
    </span>
  );
}
