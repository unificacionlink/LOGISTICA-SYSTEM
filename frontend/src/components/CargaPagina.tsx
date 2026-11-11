import { Skeleton, SkeletonTable } from "./ui/Skeleton";

/** Esqueleto de pantalla mientras se descarga su codigo (paginas.ts). */
export default function CargaPagina() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <div className="rounded-xl border border-slate-200 bg-white">
        <SkeletonTable rows={6} cols={4} />
      </div>
    </div>
  );
}
