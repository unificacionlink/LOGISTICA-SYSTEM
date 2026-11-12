import { useState } from "react";
import { FileText, Upload, X } from "lucide-react";
import { corregirCajaChica, registrarCajaChica, subirComprobanteCajaChica } from "../../api/cajachica";
import type { CompraCajaChica, FormularioCajaChica } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import Field, { inputClass } from "../ui/Field";
import CampoNumero from "../ui/CampoNumero";

const TIPOS = ["BOLETA", "FACTURA", "TICKET", "RECIBO", "OTRO"];
const MONEDA = "PEN";

function hoyLima(): string {
  return new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
}

/** "FACTURA F001-123" -> serie y numero. */
function partesComprobante(c: string): { tipo: string; serie: string; numero: string } {
  const [tipo, resto = ""] = c.split(" ", 2);
  const i = resto.lastIndexOf("-");
  return i > 0 ? { tipo, serie: resto.slice(0, i), numero: resto.slice(i + 1) } : { tipo, serie: "", numero: resto };
}

/**
 * Compra con la caja chica del proyecto (DEC-L054). La registra el solicitante
 * con su comprobante; lo comprado se queda en el proyecto y cuenta como
 * atendido cuando Logistica lo verifica. Con {@code corrige}, corrige una
 * compra observada.
 */
export default function FormCajaChica({
  formulario,
  corrige,
  onGuardada,
  onCancelar,
}: {
  formulario: FormularioCajaChica;
  corrige?: CompraCajaChica;
  onGuardada: (c: CompraCajaChica, mensaje: string) => void;
  onCancelar?: () => void;
}) {
  const previos = new Map((corrige?.items ?? []).map((i) => [i.requestItemId, i]));
  // Al corregir, lo de esta compra vuelve a estar disponible.
  const lineas = formulario.items
    .map((it) => ({ ...it, max: it.pendiente + (previos.get(it.requestItemId)?.cantidad ?? 0) }))
    .filter((it) => it.max > 0);
  const inicial = corrige ? partesComprobante(corrige.comprobante) : null;

  const [fechaCompra, setFechaCompra] = useState(corrige?.fechaCompra ?? hoyLima());
  const [ruc, setRuc] = useState(corrige?.proveedorRuc ?? "");
  const [proveedor, setProveedor] = useState(corrige?.proveedor ?? "");
  const [tipo, setTipo] = useState(inicial?.tipo ?? "BOLETA");
  const [serie, setSerie] = useState(inicial?.serie ?? "");
  const [numero, setNumero] = useState(inicial?.numero ?? "");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [filas, setFilas] = useState<Record<string, { qty: string; costo: string }>>(() =>
    Object.fromEntries(
      lineas.map((it) => {
        const p = previos.get(it.requestItemId);
        return [it.requestItemId, { qty: String(p ? p.cantidad : corrige ? 0 : it.max), costo: p ? String(p.costoUnitario) : "" }];
      }),
    ),
  );
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const total = lineas.reduce((acc, it) => {
    const f = filas[it.requestItemId];
    return acc + (Number(f?.qty) || 0) * (Number(f?.costo) || 0);
  }, 0);

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    const items = lineas
      .map((it) => ({ it, f: filas[it.requestItemId] }))
      .filter(({ f }) => Number(f?.qty) > 0)
      .map(({ it, f }) => ({ requestItemId: it.requestItemId, qty: Number(f.qty), costoUnitario: Number(f.costo) || 0 }));
    if (items.length === 0) {
      setError("Indica al menos una cantidad comprada.");
      return;
    }
    if (!archivo && !corrige) {
      setError("Adjunta la foto o el PDF del comprobante.");
      return;
    }
    setError(null);
    setGuardando(true);
    try {
      let documentId: string | undefined;
      if (archivo) {
        const subida = await subirComprobanteCajaChica(formulario.requestId, archivo);
        if (!subida.ok || !subida.result) {
          setError(subida.message);
          return;
        }
        documentId = subida.result.documentId;
      }
      const datos = {
        fechaCompra,
        proveedorRuc: ruc || undefined,
        proveedorNombre: proveedor,
        tipoComprobante: tipo,
        serie: serie || undefined,
        numero,
        moneda: MONEDA,
        documentId,
        items,
      };
      const res = corrige ? await corregirCajaChica(corrige.id, datos) : await registrarCajaChica(formulario.requestId, datos);
      if (res.ok && res.result) onGuardada(res.result, res.message);
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
              <th className="px-4 py-2.5">Costo unit. (S/)</th>
              <th className="px-4 py-2.5 text-right">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {lineas.map((it) => {
              const f = filas[it.requestItemId];
              const linea = (Number(f?.qty) || 0) * (Number(f?.costo) || 0);
              return (
                <tr key={it.requestItemId}>
                  <td className="col-flex px-4 py-2.5">
                    <p className="text-sm text-slate-700">
                      {it.producto}
                      {it.atributo ? ` · ${it.atributo}` : ""}
                    </p>
                    <p className="font-mono text-xs text-slate-500">
                      {it.sku} · hasta {it.max} {it.unidad}
                    </p>
                  </td>
                  <td className="px-4 py-2.5">
                    <CampoNumero
                      decimales={0}
                      max={it.max}
                      value={f?.qty ?? ""}
                      onChange={(v) => setFilas((p) => ({ ...p, [it.requestItemId]: { ...p[it.requestItemId], qty: v } }))}
                      className={`${inputClass} w-24 py-1.5`}
                    />
                  </td>
                  <td className="px-4 py-2.5">
                    <CampoNumero
                      decimales={4}
                      maxEnteros={9}
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
                Total S/
              </td>
              <td className="px-4 py-2.5 text-right font-semibold tabular-nums text-slate-900">{total.toFixed(2)}</td>
            </tr>
          </tfoot>
        </table>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-[140px_1fr_150px]">
            <Field label="RUC">
              <input value={ruc} onChange={(e) => setRuc(e.target.value)} inputMode="numeric" className={`${inputClass} w-full`} />
            </Field>
            <Field label="Dónde se compró" required>
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
              {archivo ? archivo.name : corrige ? "Cambiar el comprobante" : "Adjuntar foto o PDF del comprobante"}
              {!corrige && (
                <span className="text-rose-600" aria-hidden="true">
                  *
                </span>
              )}
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
            {corrige?.documentoUrl && !archivo && (
              <a
                href={corrige.documentoUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                <FileText className="size-3.5" />
                Ver el anterior
              </a>
            )}
          </div>

          {error && <Alert tone="danger">{error}</Alert>}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" loading={guardando}>
              {corrige ? "Enviar corrección" : "Registrar compra"}
            </Button>
            {onCancelar && (
              <Button type="button" variant="ghost" onClick={onCancelar}>
                Cancelar
              </Button>
            )}
          </div>
        </CardBody>
      </Card>
    </form>
  );
}
