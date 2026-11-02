import { useEffect, type FormEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import Button from "../ui/Button";

/** Ventana con formulario (mismo estilo que Confirmacion): Escape o clic fuera cierran. */
export default function Dialogo({
  titulo,
  children,
  textoGuardar = "Guardar",
  guardando,
  error,
  onGuardar,
  onCerrar,
}: {
  titulo: string;
  children: ReactNode;
  textoGuardar?: string;
  guardando: boolean;
  error: string | null;
  onGuardar: () => void;
  onCerrar: () => void;
}) {
  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      anterior?.focus?.();
    };
    // Solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function enviar(e: FormEvent) {
    e.preventDefault();
    onGuardar();
  }

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialogo-titulo"
        onSubmit={enviar}
        className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-flotante"
      >
        <h2 id="dialogo-titulo" className="font-semibold text-slate-900">
          {titulo}
        </h2>
        <div className="mt-4 flex flex-col gap-4">{children}</div>
        {error && <p className="mt-3 text-xs text-rose-600">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button type="submit" loading={guardando}>
            {textoGuardar}
          </Button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
