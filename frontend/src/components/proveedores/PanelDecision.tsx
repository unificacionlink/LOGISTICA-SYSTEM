import { useState } from "react";
import { AlertTriangle } from "lucide-react";
import { decidirProveedor } from "../../api/proveedores";
import type { FichaProveedor } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";

type Decision = "HABILITAR" | "OBSERVAR" | "RECHAZAR";

const OPCIONES: { id: Decision; label: string; ayuda: string }[] = [
  { id: "HABILITAR", label: "Habilitar", ayuda: "Queda disponible en Compras." },
  { id: "OBSERVAR", label: "Observar", ayuda: "Vuelve para corregir y reenviar." },
  { id: "RECHAZAR", label: "Rechazar", ayuda: "Se cierra; se puede reabrir después." },
];

/**
 * Decision sobre una solicitud en revision (DEC-L069). Las advertencias no
 * bloquean: habilitar con advertencias pide motivo, que queda auditado.
 */
export default function PanelDecision({
  ficha,
  onCerrar,
  onDecidido,
}: {
  ficha: FichaProveedor;
  onCerrar: () => void;
  onDecidido: (f: FichaProveedor, mensaje: string) => void;
}) {
  const [decision, setDecision] = useState<Decision>("HABILITAR");
  const [motivo, setMotivo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const avisos = ficha.advertencias;
  const pideMotivo = decision !== "HABILITAR" || avisos.length > 0;

  async function decidir() {
    setGuardando(true);
    setError(null);
    try {
      const res = await decidirProveedor(ficha.proveedor.supplierId, decision, motivo.trim() || undefined);
      if (res.ok && res.result) onDecidido(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo="Decidir solicitud"
      subtitulo={ficha.proveedor.legalName}
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button
            variant={decision === "RECHAZAR" ? "danger" : "primary"}
            loading={guardando}
            disabled={pideMotivo && !motivo.trim()}
            onClick={decidir}
          >
            {OPCIONES.find((o) => o.id === decision)?.label}
          </Button>
        </>
      }
    >
      {avisos.length > 0 && (
        <SeccionPanel titulo="Advertencias">
          <ul className="space-y-1.5">
            {avisos.map((a) => (
              <li key={a} className="flex items-start gap-2 whitespace-normal text-sm text-amber-800">
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                {a}
              </li>
            ))}
          </ul>
        </SeccionPanel>
      )}
      <SeccionPanel titulo="Decisión">
        <div role="radiogroup" className="grid gap-2">
          {OPCIONES.map((o) => (
            <label
              key={o.id}
              className={`flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2.5 ${
                decision === o.id ? "border-brand-500 bg-brand-50/50" : "border-slate-200 hover:border-slate-300"
              }`}
            >
              <input type="radio" name="decision" checked={decision === o.id} onChange={() => setDecision(o.id)} />
              <span className="text-sm font-medium text-slate-900">{o.label}</span>
              <span className="text-sm text-slate-500">{o.ayuda}</span>
            </label>
          ))}
        </div>
        <Field
          label={decision === "HABILITAR" ? "Motivo para habilitar con advertencias" : "Motivo"}
          required={pideMotivo}
          className="mt-4"
        >
          <textarea
            className={`${inputClass} w-full whitespace-normal`}
            rows={3}
            maxLength={1000}
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
          />
        </Field>
        {error && <Alert tone="danger" className="mt-3">{error}</Alert>}
      </SeccionPanel>
    </PanelLateral>
  );
}
