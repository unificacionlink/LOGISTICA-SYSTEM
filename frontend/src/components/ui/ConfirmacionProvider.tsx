import { useCallback, useEffect, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import Button from "./Button";
import {
  ConfirmacionContext,
  type Confirmar,
  type OpcionesConfirmacion,
  type ResultadoConfirmacion,
} from "./Confirmacion";
import Field, { inputClass } from "./Field";

interface Pendiente extends OpcionesConfirmacion {
  resolver: (r: ResultadoConfirmacion) => void;
}

export function ConfirmacionProvider({ children }: { children: ReactNode }) {
  const [pendiente, setPendiente] = useState<Pendiente | null>(null);

  const confirmar = useCallback<Confirmar>(
    (opciones) =>
      new Promise((resolver) => {
        setPendiente({ ...opciones, resolver });
      }),
    [],
  );

  function cerrar(resultado: ResultadoConfirmacion) {
    pendiente?.resolver(resultado);
    setPendiente(null);
  }

  return (
    <ConfirmacionContext.Provider value={confirmar}>
      {children}
      {pendiente && createPortal(<Dialogo opciones={pendiente} onCerrar={cerrar} />, document.body)}
    </ConfirmacionContext.Provider>
  );
}

function Dialogo({
  opciones,
  onCerrar,
}: {
  opciones: OpcionesConfirmacion;
  onCerrar: (r: ResultadoConfirmacion) => void;
}) {
  const { titulo, mensaje, confirmar = "Confirmar", peligro, motivo: etiquetaMotivo } = opciones;
  const [motivo, setMotivo] = useState("");
  const faltaMotivo = !!etiquetaMotivo && !motivo.trim();

  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCerrar(null);
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      anterior?.focus?.();
    };
    // Solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function aceptar(e: React.FormEvent) {
    e.preventDefault();
    if (faltaMotivo) return;
    onCerrar({ motivo: motivo.trim() });
  }

  return (
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar(null)}
    >
      <form
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirmacion-titulo"
        onSubmit={aceptar}
        className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-flotante"
      >
        <h2 id="confirmacion-titulo" className="font-semibold text-slate-900">
          {titulo}
        </h2>
        {mensaje && <div className="mt-2 text-sm text-slate-600">{mensaje}</div>}
        {etiquetaMotivo && (
          <Field label={etiquetaMotivo} required className="mt-4">
            <textarea
              autoFocus
              rows={3}
              maxLength={500}
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              className={`${inputClass} w-full`}
            />
          </Field>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => onCerrar(null)}>
            Cancelar
          </Button>
          <Button
            autoFocus={!etiquetaMotivo}
            type="submit"
            variant={peligro ? "danger" : "primary"}
            disabled={faltaMotivo}
          >
            {confirmar}
          </Button>
        </div>
      </form>
    </div>
  );
}
