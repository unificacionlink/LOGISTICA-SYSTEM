/** Pantalla completa mientras se valida la sesion al entrar o recargar. */
export default function PantallaCarga({ texto = "Cargando" }: { texto?: string }) {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-slate-100" aria-busy="true">
      <div className="flex items-center gap-2.5">
        <span className="flex size-9 items-center justify-center rounded-md bg-brand-700 font-serif text-lg font-semibold text-white">
          T
        </span>
        <span className="leading-tight">
          <span className="block text-sm font-semibold tracking-[-0.01em] text-slate-900">Logística</span>
          <span className="block text-[0.6875rem] text-slate-500">TDEM · ERP corporativo</span>
        </span>
      </div>
      <div className="h-[3px] w-44 overflow-hidden rounded-full bg-slate-200">
        <div className="animate-carga-indeterminada h-full w-2/5 rounded-full bg-brand-700" />
      </div>
      <p className="text-xs text-slate-500">{texto}…</p>
    </main>
  );
}
