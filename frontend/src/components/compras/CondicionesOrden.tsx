import { useState, type ReactNode } from "react";
import { Pencil } from "lucide-react";
import { editarCondicionesOrden } from "../../api/compras";
import type { ModoIgv, Orden } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import { dinero } from "./formato";

const ETIQUETA_IGV: Record<ModoIgv, string> = {
  MAS_IGV: "Precios sin IGV (+18%)",
  IGV_INCLUIDO: "Precios con IGV incluido",
  NO_AFECTO: "No afecto a IGV",
};

function Dato({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="rotulo">{label}</div>
      <div className="mt-1 truncate text-sm text-slate-700">{children}</div>
    </div>
  );
}

/**
 * Condiciones y montos del formato F-LOG-09 (DEC-L079). Logistica las ajusta
 * mientras la OC espera aprobacion; despues quedan fijas.
 */
export default function CondicionesOrden({
  orden,
  onCambio,
  onError,
}: {
  orden: Orden;
  onCambio: (o: Orden) => void;
  onError: (mensaje: string) => void;
}) {
  const { tieneAlgunRol } = useAuth();
  const editable = orden.status === "POR_APROBAR" && tieneAlgunRol("LOGISTICA", "ADMIN");
  const [editando, setEditando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [modo, setModo] = useState<ModoIgv>(orden.taxMode);
  const [plazo, setPlazo] = useState(orden.deliveryTerm ?? "");
  const [lugar, setLugar] = useState(orden.deliveryLocation ?? "");
  const [especificaciones, setEspecificaciones] = useState(orden.additionalSpecs ?? "");

  function abrir() {
    setModo(orden.taxMode);
    setPlazo(orden.deliveryTerm ?? "");
    setLugar(orden.deliveryLocation ?? "");
    setEspecificaciones(orden.additionalSpecs ?? "");
    setEditando(true);
  }

  async function guardar() {
    setGuardando(true);
    try {
      const res = await editarCondicionesOrden(orden.orderId, {
        taxMode: modo,
        deliveryTerm: plazo.trim() || null,
        deliveryLocation: lugar.trim() || null,
        additionalSpecs: especificaciones.trim() || null,
      });
      if (res.ok && res.result) {
        onCambio(res.result);
        setEditando(false);
      } else {
        onError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  const moneda = orden.currencyCode;

  return (
    <Card className="mb-4">
      <CardBody>
        {editando ? (
          <div className="flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="IGV">
                <select className={`${inputClass} w-full`} value={modo} onChange={(e) => setModo(e.target.value as ModoIgv)}>
                  {(Object.keys(ETIQUETA_IGV) as ModoIgv[]).map((m) => (
                    <option key={m} value={m}>
                      {ETIQUETA_IGV[m]}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Plazo de entrega">
                <input className={`${inputClass} w-full`} value={plazo} maxLength={120} onChange={(e) => setPlazo(e.target.value)} />
              </Field>
              <Field label="Lugar de entrega">
                <input className={`${inputClass} w-full`} value={lugar} maxLength={250} onChange={(e) => setLugar(e.target.value)} />
              </Field>
            </div>
            <Field label="Especificaciones adicionales">
              <textarea
                className={`${inputClass} w-full`}
                rows={3}
                maxLength={2000}
                value={especificaciones}
                onChange={(e) => setEspecificaciones(e.target.value)}
              />
            </Field>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setEditando(false)} disabled={guardando}>
                Cancelar
              </Button>
              <Button onClick={guardar} loading={guardando}>
                Guardar condiciones
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-4">
            <div className="grid flex-1 gap-4 sm:grid-cols-4">
              <Dato label="Forma de pago">{orden.paymentCondition}</Dato>
              <Dato label="Plazo de entrega">{orden.deliveryTerm ?? "—"}</Dato>
              <Dato label="Lugar de entrega">{orden.deliveryLocation ?? "—"}</Dato>
              <Dato label="IGV">{ETIQUETA_IGV[orden.taxMode]}</Dato>
              <Dato label="Valor total">{dinero(orden.subtotal, moneda)}</Dato>
              <Dato label="IGV">{dinero(orden.taxAmount, moneda)}</Dato>
              <Dato label="Precio total">
                <span className="font-semibold text-slate-900">{dinero(orden.total, moneda)}</span>
              </Dato>
              {orden.additionalSpecs && (
                <div className="sm:col-span-4">
                  <div className="rotulo">Especificaciones adicionales</div>
                  <p className="mt-1 whitespace-pre-line text-sm text-slate-700">{orden.additionalSpecs}</p>
                </div>
              )}
            </div>
            {editable && (
              <Button variant="secondary" size="sm" icon={<Pencil className="size-3.5" />} onClick={abrir}>
                Editar
              </Button>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
