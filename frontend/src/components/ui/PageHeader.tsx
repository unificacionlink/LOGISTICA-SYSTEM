import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";

export default function PageHeader({
  title,
  subtitle,
  backTo,
  backLabel,
  actions,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  backTo?: string;
  backLabel?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-8">
      {backTo && (
        <Link
          to={backTo}
          className="group mb-4 inline-flex items-center gap-1.5 text-[13px] font-medium text-slate-500 transition-colors hover:text-slate-900"
        >
          <ArrowLeft className="size-3.5 transition-transform duration-200 group-hover:-translate-x-0.5" />
          {backLabel}
        </Link>
      )}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-serif text-[28px] font-medium leading-[1.1] tracking-[-0.02em] text-slate-950 sm:text-[34px]">
            {title}
          </h1>
          {subtitle && <p className="mt-2 max-w-[65ch] text-sm text-slate-500">{subtitle}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
      </div>
    </div>
  );
}
