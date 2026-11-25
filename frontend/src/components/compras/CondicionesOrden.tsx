import { useState, type ReactNode } from "react";
import { Pencil } from "lucide-react";
import { editarCondicionesOrden } from "../../api/compras";
import type { ModoIgv, Orden, TipoPlazo } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import { LugarEntregaCampo, PlazoEntregaCampo } from "./CamposEntrega";
import { dinero } from "./formato";
import { fmtDia } from "../formato";

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
  const [dias, setDias] = useState(orden.deliveryDays != null ? String(orden.deliveryDays) : "");
  const [tipoPlazo, setTipoPlazo] = useState<TipoPlazo>(orden.deliveryDayType ?? "HABILES");
  const [lugar, setLugar] = useState<string | null>(orden.deliveryPointId);
  const [especificaciones, setEspecificaciones] = useState(orden.additionalSpecs ?? "");
  const [directa, setDirecta] = useState(orden.entregaDirecta);

  function abrir() {
    setModo(orden.taxMode);
    setDias(orden.deliveryDays != null ? String(orden.deliveryDays) : "");
    setTipoPlazo(orden.deliveryDayType ?? "HABILES");
    setLugar(orden.deliveryPointId);
    setEspecificaciones(orden.additionalSpecs ?? "");
    setDirecta(orden.entregaDirecta);
    setEditando(true);
  }

  async function guardar() {
    if (!(Number(dias) >= 1)) {
      onError("Indica el plazo de entrega en días.");
      return;
    }
    setGuardando(true);
    try {
      const res = await editarCondicionesOrden(orden.orderId, {
        taxMode: modo,
        deliveryDays: Number(dias),
        deliveryDayType: tipoPlazo,
        deliveryPointId: lugar,
        additionalSpecs: especificaciones.trim() || null,
        entregaDirecta: directa,
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
              <Field label="Plazo de entrega" required>
                <PlazoEntregaCampo dias={dias} tipo={tipoPlazo} onDias={setDias} onTipo={setTipoPlazo} />
              </Field>
              <Field label="Lugar de entrega">
                <LugarEntregaCampo valor={lugar} onCambio={setLugar} />
              </Field>
            </div>
            {/* DEC-L084: el proveedor lleva cada parte al CECO de su requerimiento. */}
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={directa}
                onChange={(e) => setDirecta(e.target.checked)}
                className="size-4 accent-brand-700"
              />
              Entrega directa en proyecto (el proveedor entrega en el CECO de cada requerimiento)
            </label>
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
              <Dato label="Plazo de entrega">
                {orden.deliveryTerm ?? "—"}
                {orden.fechaEntregaComprometida && (
                  <span className="text-slate-500"> · hasta el {fmtDia(orden.fechaEntregaComprometida)}</span>
                )}
              </Dato>
              <Dato label="Lugar de entrega">
                {orden.entregaDirecta
                  ? `Entrega directa en proyecto${orden.deliveryLocation ? ` · ${orden.deliveryLocation}` : ""}`
                  : (orden.deliveryLocation ?? "—")}
              </Dato>
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
