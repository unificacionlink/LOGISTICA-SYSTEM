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
    <div className="mb-6 lg:mb-7">
      {backTo && (
        <Link
          to={backTo}
          className="group mb-3 inline-flex items-center gap-1.5 rounded-sm text-[0.8125rem] font-medium text-slate-500 transition-colors hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
        >
          <ArrowLeft className="size-3.5 transition-transform duration-200 group-hover:-translate-x-0.5" />
          {backLabel}
        </Link>
      )}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="font-serif text-[1.625rem] font-medium leading-[1.15] tracking-[-0.015em] text-slate-950 sm:text-[1.875rem]">
            {title}
          </h1>
          {subtitle && <p className="mt-1.5 max-w-[65ch] text-[0.8125rem] text-slate-500">{subtitle}</p>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}
