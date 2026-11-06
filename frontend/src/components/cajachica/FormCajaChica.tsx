import { useMemo, useState } from "react";
import { Upload, X } from "lucide-react";
import { ETIQUETA_MOTIVO, registrarCajaChica, subirComprobanteCajaChica } from "../../api/cajachica";
import type { ComprasDetalle, CompraCajaChica, EvaluacionCajaChica, MotivoCajaChica } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";

import CampoNumero from "../ui/CampoNumero";
const TIPOS = ["FACTURA", "BOLETA", "TICKET", "RECIBO", "OTRO"];

function hoyLima(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
}

/**
 * Registro de una compra por Caja Chica (DEC-L026). Lo comprado ya esta en
 * mano: al guardar entra al almacen reservado para el requerimiento.
 */
export default function FormCajaChica({
  detalle,
  evaluacion,
  onRegistrada,
}: {
  detalle: ComprasDetalle;
  evaluacion: EvaluacionCajaChica;
  onRegistrada: (c: CompraCajaChica, mensaje: string) => void;
}) {
  const pendientes = detalle.items.filter((it) => it.cantidadPendiente > 0);
  const regla = evaluacion.regla;
  // Motivos que la regla permite: los sugeridos, bajo monto (se valida con el total real) y la excepcion.
  const motivos = useMemo(() => {
    const m = new Set<MotivoCajaChica>(evaluacion.motivos.map((x) => x.codigo));
    if (regla.bajoMonto) m.add("BAJO_MONTO");
    if (regla.excepcion) m.add("EXCEPCION_AUTORIZADA");
    return Array.from(m);
  }, [evaluacion, regla]);

  const [motivo, setMotivo] = useState<MotivoCajaChica>(evaluacion.motivos[0]?.codigo ?? motivos[0]);
  const [motivoDetalle, setMotivoDetalle] = useState("");
  const [fechaCompra, setFechaCompra] = useState(hoyLima());
  const [ruc, setRuc] = useState("");
  const [proveedor, setProveedor] = useState("");
  const [tipo, setTipo] = useState("BOLETA");
  const [serie, setSerie] = useState("");
  const [numero, setNumero] = useState("");
  const [codigoExterno, setCodigoExterno] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [filas, setFilas] = useState<Record<string, { qty: string; costo: string }>>(() =>
    Object.fromEntries(pendientes.map((it) => [it.requestItemId, { qty: String(it.cantidadPendiente), costo: "" }])),
  );
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const total = pendientes.reduce((acc, it) => {
    const f = filas[it.requestItemId];
    return acc + (Number(f?.qty) || 0) * (Number(f?.costo) || 0);
  }, 0);
  const sobreUmbral = motivo === "BAJO_MONTO" && total > regla.umbral;

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    const items = pendientes
      .map((it) => ({ it, f: filas[it.requestItemId] }))
      .filter(({ f }) => Number(f?.qty) > 0)
      .map(({ it, f }) => ({ requestItemId: it.requestItemId, qty: Number(f.qty), costoUnitario: Number(f.costo) || 0 }));
    if (items.length === 0) {
      setError("Indica al menos una cantidad comprada.");
      return;
    }
    setError(null);
    setGuardando(true);
    try {
      let documentId: string | undefined;
      if (archivo) {
        const subida = await subirComprobanteCajaChica(archivo);
        if (!subida.ok || !subida.result) {
          setError(subida.message);
          return;
        }
        documentId = subida.result.documentId;
      }
      const res = await registrarCajaChica(detalle.requerimiento.id, {
        motivo,
        motivoDetalle: motivoDetalle || undefined,
        fechaCompra,
        proveedorRuc: ruc || undefined,
        proveedorNombre: proveedor,
        tipoComprobante: tipo,
        serie: serie || undefined,
        numero,
        moneda: regla.moneda,
        documentId,
        codigoExterno: codigoExterno || undefined,
        items,
      });
      if (res.ok && res.result) onRegistrada(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={onGuardar} className="flex flex-col gap-6">
      <Card className="overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Lo comprado</div>
        </CardBody>
        <table className="tabla">
          <thead>
            <tr className="border-y border-slate-200 cabecera-tabla">
              <th className="px-4 py-2.5">Producto</th>
              <th className="px-4 py-2.5">Cantidad</th>
              <th className="px-4 py-2.5">Costo unit. ({regla.moneda})</th>
              <th className="px-4 py-2.5 text-right">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {pendientes.map((it) => {
              const f = filas[it.requestItemId];
              const linea = (Number(f?.qty) || 0) * (Number(f?.costo) || 0);
              return (
                <tr key={it.requestItemId}>
                  <td className="col-flex px-4 py-2.5">
                    <p className="text-sm text-slate-700">{it.nombreProducto}</p>
                    <p className="font-mono text-xs text-slate-500">
                      {it.codigoVariante} · pendiente {it.cantidadPendiente} {it.unidadMedida}
                    </p>
                  </td>
                  <td className="px-4 py-2.5">
                    <CampoNumero
                      decimales={0}
                      max={it.cantidadPendiente}
                      value={f?.qty ?? ""}
                      onChange={(v) => setFilas((p) => ({ ...p, [it.requestItemId]: { ...p[it.requestItemId], qty: v } }))}
                      className={`${inputClass} w-24 py-1.5`}
                    />
                  </td>
                  <td className="px-4 py-2.5">
                    <CampoNumero
                      decimales={4} maxEnteros={9}
                      value={f?.costo ?? ""}
                      onChange={(v) => setFilas((p) => ({ ...p, [it.requestItemId]: { ...p[it.requestItemId], costo: v } }))}
                      className={`${inputClass} w-28 py-1.5`}
                    />
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{linea.toFixed(2)}</td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="border-t border-slate-200">
              <td colSpan={3} className="px-4 py-2.5 text-right text-sm font-medium text-slate-700">
                Total {regla.moneda}
              </td>
              <td className={`px-4 py-2.5 text-right font-semibold tabular-nums ${sobreUmbral ? "text-rose-700" : "text-slate-900"}`}>
                {total.toFixed(2)}
              </td>
            </tr>
          </tfoot>
        </table>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-[220px_1fr]">
            <Field label="Motivo" required>
              <select value={motivo} onChange={(e) => setMotivo(e.target.value as MotivoCajaChica)} className={`${inputClass} w-full`}>
                {motivos.map((m) => (
                  <option key={m} value={m}>
                    {ETIQUETA_MOTIVO[m]}
                  </option>
                ))}
              </select>
            </Field>
            {motivo === "EXCEPCION_AUTORIZADA" ? (
              <Field label="Quién la autorizó y por qué" required>
                <input required value={motivoDetalle} onChange={(e) => setMotivoDetalle(e.target.value)} className={`${inputClass} w-full`} />
              </Field>
            ) : (
              <p className="self-end pb-2 text-xs text-slate-500">
                {motivo === "BAJO_MONTO"
                  ? `Hasta ${regla.moneda} ${regla.umbral.toFixed(2)}${sobreUmbral ? " — el total lo supera" : ""}`
                  : evaluacion.motivos.find((m) => m.codigo === motivo)?.detalle}
              </p>
            )}
          </div>

          <div className="grid gap-4 sm:grid-cols-[140px_1fr_140px]">
            <Field label="RUC">
              <input value={ruc} onChange={(e) => setRuc(e.target.value)} inputMode="numeric" className={`${inputClass} w-full`} />
            </Field>
            <Field label="Proveedor" required>
              <input required value={proveedor} onChange={(e) => setProveedor(e.target.value)} className={`${inputClass} w-full`} />
            </Field>
            <Field label="Fecha de compra" required>
              <input
                type="date"
                required
                max={hoyLima()}
                value={fechaCompra}
                onChange={(e) => setFechaCompra(e.target.value)}
                className={`${inputClass} w-full`}
              />
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-[140px_110px_1fr]">
            <Field label="Comprobante" required>
              <select value={tipo} onChange={(e) => setTipo(e.target.value)} className={`${inputClass} w-full`}>
                {TIPOS.map((t) => (
                  <option key={t} value={t}>
                    {t.charAt(0) + t.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Serie">
              <input value={serie} onChange={(e) => setSerie(e.target.value)} className={`${inputClass} w-full`} />
            </Field>
            <Field label="Número" required>
              <input required value={numero} onChange={(e) => setNumero(e.target.value)} className={`${inputClass} w-full`} />
            </Field>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <label className="flex cursor-pointer items-center gap-2 rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-600 hover:border-brand-300 hover:bg-brand-50 hover:text-brand-700">
              <Upload className="size-4" />
              {archivo ? archivo.name : "Adjuntar comprobante"}
              <input
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
              />
            </label>
            {archivo && (
              <button type="button" onClick={() => setArchivo(null)} title="Quitar" className="text-slate-400 hover:text-rose-600">
                <X className="size-4" />
              </button>
            )}
          </div>

          <details className="text-sm">
            <summary className="cursor-pointer list-none text-xs font-medium text-slate-500 hover:text-slate-800">
              Código en el aplicativo de Caja Chica
            </summary>
            <input
              value={codigoExterno}
              onChange={(e) => setCodigoExterno(e.target.value)}
              placeholder="Si ya se registró allá"
              className={`${inputClass} mt-2 w-60`}
            />
          </details>

          {error && <Alert tone="danger">{error}</Alert>}
          <Button type="submit" loading={guardando} disabled={sobreUmbral} className="self-start">
            Registrar compra
          </Button>
        </CardBody>
      </Card>
    </form>
  );
}
