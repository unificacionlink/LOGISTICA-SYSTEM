import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";

/**
 * Ventana para editar un solo valor numerico del maestro (precio de venta,
 * plazo de atencion; DEC-L035). `onGuardar` devuelve el mensaje de error o
 * null si se guardo; la ventana se cierra sola al guardar.
 */
export default function DialogoValor({
  titulo,
  etiqueta,
  valorInicial,
  prefijo,
  sufijo,
  min,
  max,
  step,
  permitirVacio,
  onGuardar,
  onCerrar,
}: {
  titulo: string;
  etiqueta: string;
  valorInicial: number | null;
  prefijo?: string;
  sufijo?: string;
  min: number;
  max?: number;
  step: string;
  permitirVacio?: boolean;
  onGuardar: (valor: number | null) => Promise<string | null>;
  onCerrar: () => void;
}) {
  const [valor, setValor] = useState(valorInicial == null ? "" : String(valorInicial));
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

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

  async function guardar(e: React.FormEvent) {
    e.preventDefault();
    const texto = valor.trim();
    if (!texto && !permitirVacio) {
      setError("Ingresa un valor.");
      return;
    }
    const numero = texto ? Number(texto) : null;
    if (numero != null && (!Number.isFinite(numero) || numero < min || (max != null && numero > max))) {
      setError(max != null ? `Ingresa un valor entre ${min} y ${max}.` : "Ingresa un valor mayor que cero.");
      return;
    }
    setGuardando(true);
    setError(null);
    const mensaje = await onGuardar(numero);
    setGuardando(false);
    if (mensaje) setError(mensaje);
    else onCerrar();
  }

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialogo-valor-titulo"
        onSubmit={guardar}
        className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-5 shadow-flotante"
      >
        <h2 id="dialogo-valor-titulo" className="font-semibold text-slate-900">
          {titulo}
        </h2>
        <Field label={etiqueta} className="mt-4" hint={permitirVacio ? "Vacío: sin configurar" : undefined}>
          <div className="flex items-center gap-2">
            {prefijo && <span className="text-sm text-slate-500">{prefijo}</span>}
            <input
              autoFocus
              type="number"
              inputMode="decimal"
              min={min}
              max={max}
              step={step}
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              className={`${inputClass} w-full tabular-nums`}
            />
            {sufijo && <span className="shrink-0 text-sm text-slate-500">{sufijo}</span>}
          </div>
        </Field>
        {error && <p className="mt-2 text-xs text-rose-600">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button type="submit" loading={guardando}>
            Guardar
          </Button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
