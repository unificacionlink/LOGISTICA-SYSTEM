import type { ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info } from "lucide-react";

type Tone = "good" | "danger" | "info";

const tones: Record<Tone, { wrap: string; icon: ReactNode }> = {
  good: {
    wrap: "bg-emerald-50 text-emerald-800 border-emerald-100",
    icon: <CheckCircle2 className="size-4.5 shrink-0 text-emerald-600" />,
  },
  danger: {
    wrap: "bg-rose-50 text-rose-800 border-rose-100",
    icon: <AlertTriangle className="size-4.5 shrink-0 text-rose-600" />,
  },
  info: {
    wrap: "bg-blue-50 text-blue-800 border-blue-100",
    icon: <Info className="size-4.5 shrink-0 text-blue-600" />,
  },
};

export default function Alert({
  tone,
  children,
  className = "",
}: {
  tone: Tone;
  children: ReactNode;
  className?: string;
}) {
  const t = tones[tone];
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={`flex items-start gap-2.5 rounded-lg border px-4 py-3 text-sm leading-snug [&>svg]:mt-px ${t.wrap} ${className}`}
    >
      {t.icon}
      <span>{children}</span>
    </div>
  );
}
