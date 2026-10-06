export function Skeleton({ className = "" }: { className?: string }) {
  // bg-slate-200 (no 100): el fondo de pagina ya es slate-100 y el bloque desaparecia.
  return <div aria-hidden="true" className={`skeleton rounded bg-slate-200/80 ${className}`} />;
}

export function SkeletonTable({ rows = 5, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div className="p-5 sm:p-6">
      <div className="flex flex-col gap-3">
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="flex gap-4">
            {Array.from({ length: cols }).map((_, c) => (
              <Skeleton key={c} className={`h-4 ${c === 0 ? "w-24" : "flex-1"}`} />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
