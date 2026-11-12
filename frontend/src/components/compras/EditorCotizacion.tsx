import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { ExternalLink, ScanText, X } from "lucide-react";
import { registrarEnComparativo, subirDocumentoCotizacion } from "../../api/compras";
import type {
  Comparativo,
  ComparativoColumna,
  ComparativoFila,
  Moneda,
  OcrComparativo,
  OcrLinea,
  Proveedor,
} from "../../api/types";
import LineaDocumento from "./LineaDocumento";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";
import { CONDICIONES, ENTREGAS } from "./condiciones";
import { cantidad, dinero, unitarioDeLinea } from "./formato";
import ProveedorCombobox from "./ProveedorCombobox";

import CampoNumero from "../ui/CampoNumero";
function aTexto(n: number | null | undefined): string {
  return n == null ? "" : String(n);
}

/**
 * Revisión de una cotización antes de guardarla (DEC-L047): el OCR propone
 * proveedor, moneda, plazo y precios; Logística confirma o corrige. Un
 * producto sin precio se toma como "no cotizado" por ese proveedor.
 */
export default function EditorCotizacion({
  ids,
  filas,
  archivo,
  ocr,
  proveedores,
  monedas,
  supplierFijo,
  reemplaza,
  excluir,
  onGuardado,
  onCerrar,
}: {
  ids: string[];
  filas: ComparativoFila[];
  archivo: File | null;
  ocr: OcrComparativo | null;
  proveedores: Proveedor[];
  monedas: Moneda[];
  supplierFijo: string | null;
  reemplaza: ComparativoColumna | null;
  excluir: string[];
  onGuardado: (c: Comparativo, mensaje: string) => void;
  onCerrar: () => void;
}) {
  const [supplierId, setSupplierId] = useState<string | null>(supplierFijo ?? ocr?.supplierId ?? null);
  const [moneda, setMoneda] = useState(ocr?.monedaDetectada ?? reemplaza?.currencyCode ?? "PEN");
  const [numero, setNumero] = useState("");
  const [condicion, setCondicion] = useState(reemplaza?.paymentCondition ?? "CONTADO");
  const [entrega, setEntrega] = useState(reemplaza?.deliveryMode ?? "AGENCIA");
  const [plazo, setPlazo] = useState(aTexto(ocr?.plazoDetectadoDias ?? reemplaza?.leadDays));
  const [precios, setPrecios] = useState<Record<string, string>>(() =>
    Object.fromEntries(filas.map((f) => [f.consolidationId, aTexto(ocr?.precios[f.consolidationId])])),
  );
  // Linea del documento elegida por producto (DEC-L053): parte de lo que encontro la lectura.
  const [lineaDe, setLineaDe] = useState<Record<string, number | null>>(() =>
    Object.fromEntries(filas.map((f) => [f.consolidationId, ocr?.coincidencias?.[f.consolidationId]?.indiceLinea ?? null])),
  );
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verTexto, setVerTexto] = useState(false);

  const urlArchivo = useMemo(() => (archivo ? URL.createObjectURL(archivo) : null), [archivo]);
  useEffect(
    () => () => {
      if (urlArchivo) URL.revokeObjectURL(urlArchivo);
    },
    [urlArchivo],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !guardando && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar, guardando]);

  const leidos = ocr ? Object.keys(ocr.precios).length : 0;
  const total = filas.reduce((s, f) => {
    const p = Number(precios[f.consolidationId]);
    return precios[f.consolidationId] && p > 0 ? s + p * f.totalQty : s;
  }, 0);
  const cotizados = filas.filter((f) => Number(precios[f.consolidationId]) > 0).length;

  // El total del documento suele incluir IGV: se avisa solo si no cuadra ni con ni sin IGV.
  let avisoTotal: { texto: string; alerta: boolean } | null = null;
  if (ocr?.totalDetectado && total > 0) {
    const t = ocr.totalDetectado;
    const cerca = (a: number, b: number) => Math.abs(a - b) <= b * 0.02;
    if (cerca(t, total * 1.18))
      avisoTotal = { texto: "El total del documento coincide con estos precios más IGV.", alerta: false };
    else if (!cerca(t, total))
      avisoTotal = { texto: `El documento dice ${dinero(t, moneda)} de total; revisa los precios.`, alerta: true };
  }
  const rucSinRegistrar =
    ocr?.rucDetectado && !ocr.supplierId && !proveedores.some((p) => p.ruc === ocr.rucDetectado)
      ? ocr.rucDetectado
      : null;

  function elegirLinea(f: ComparativoFila, l: OcrLinea | null) {
    setLineaDe((p) => ({ ...p, [f.consolidationId]: l?.indice ?? null }));
    const u = l ? unitarioDeLinea(l.importes, f.totalQty) : null;
    setPrecios((p) => ({ ...p, [f.consolidationId]: u != null ? String(u) : "" }));
  }

  async function onGuardar(e: React.FormEvent) {
    e.preventDefault();
    if (!supplierId) {
      setError("Selecciona el proveedor.");
      return;
    }
    const items = filas
      .filter((f) => precios[f.consolidationId]?.trim())
      .map((f) => {
        const l = ocr?.lineas.find((x) => x.indice === lineaDe[f.consolidationId]);
        return {
          consolidationId: f.consolidationId,
          unitCost: Number(precios[f.consolidationId]),
          supplierDescription: l?.descripcion,
          supplierCode: l?.codigo ?? undefined,
        };
      });
    if (items.length === 0) {
      setError("Ingresa el precio de al menos un producto.");
      return;
    }
    if (items.some((i) => !(i.unitCost > 0))) {
      setError("Los precios deben ser mayores a 0.");
      return;
    }
    setError(null);
    setGuardando(true);
    try {
      let documentId: string | undefined;
      if (archivo) {
        const subida = await subirDocumentoCotizacion(archivo);
        if (!subida.ok || !subida.result) {
          setError(subida.message);
          return;
        }
        documentId = subida.result.documentId;
      }
      const res = await registrarEnComparativo({
        supplierId,
        consolidationIds: ids,
        supplierQuoteNumber: numero.trim() || undefined,
        issueDate: ocr?.fechaDetectada ?? undefined,
        currencyCode: moneda,
        paymentCondition: condicion,
        deliveryMode: entrega,
        leadDays: plazo.trim() ? Number(plazo) : undefined,
        documentId,
        reemplazaQuotationId: reemplaza?.quotationId ?? undefined,
        leidaPorOcr: leidos > 0,
        items,
      });
      if (res.ok && res.result) onGuardado(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20"
      onMouseDown={(e) => e.target === e.currentTarget && !guardando && onCerrar()}
    >
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="editor-cotizacion"
        className="flex h-full w-full max-w-2xl flex-col border-l border-slate-200 bg-white shadow-flotante"
      >
        <header className="flex items-start gap-3 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0 flex-1">
            <h2 id="editor-cotizacion" className="text-base font-semibold text-slate-900">
              {reemplaza ? `Reemplazar cotización de ${reemplaza.proveedorNombre}` : "Revisar cotización"}
            </h2>
            {archivo && (
              <p className="mt-0.5 flex items-center gap-2 text-xs text-slate-500">
                <span className="truncate">{archivo.name}</span>
                {urlArchivo && (
                  <a
                    href={urlArchivo}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex shrink-0 items-center gap-1 font-medium text-brand-600 hover:text-brand-700"
                  >
                    Ver archivo <ExternalLink className="size-3" />
                  </a>
                )}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onCerrar}
            disabled={guardando}
            aria-label="Cerrar"
            className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X className="size-5" />
          </button>
        </header>

        <form onSubmit={onGuardar} className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            {ocr && (
              <div className="mb-4 flex items-start gap-2.5 rounded-md border border-brand-100 bg-brand-50/60 px-3 py-2.5 text-xs text-brand-900">
                <ScanText className="mt-px size-4 shrink-0" />
                <p>
                  Se leyeron <b>{leidos}</b> de {filas.length} precios
                  {ocr.supplierId ? " y se reconoció al proveedor por su RUC" : ""}. Revisa antes de guardar.
                </p>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Proveedor" required className="sm:col-span-2">
                <ProveedorCombobox
                  proveedores={proveedores}
                  valor={supplierId}
                  onSeleccionar={setSupplierId}
                  deshabilitado={!!supplierFijo}
                  excluir={excluir}
                />
                {rucSinRegistrar && (
                  <p className="text-xs text-amber-700">
                    El RUC {rucSinRegistrar} ({ocr?.proveedorDetectado ?? "sin nombre"}) no está registrado.{" "}
                    <a href="/proveedores/nuevo" target="_blank" rel="noreferrer" className="font-medium underline">
                      Registrar proveedor
                    </a>
                  </p>
                )}
              </Field>
              <Field label="Moneda" required>
                <select value={moneda} onChange={(e) => setMoneda(e.target.value)} className={`${inputClass} w-full`}>
                  {(monedas.length ? monedas : [{ currencyCode: "PEN", name: "Soles" }]).map((m) => (
                    <option key={m.currencyCode} value={m.currencyCode}>
                      {m.currencyCode} · {m.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="N° de cotización">
                <input
                  value={numero}
                  onChange={(e) => setNumero(e.target.value)}
                  maxLength={60}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Condición de pago" required>
                <select
                  value={condicion}
                  onChange={(e) => setCondicion(e.target.value)}
                  className={`${inputClass} w-full`}
                >
                  {CONDICIONES.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label}
                    </option>
                  ))}
                </select>
              </Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="Entrega" required>
                  <select
                    value={entrega}
                    onChange={(e) => setEntrega(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    {ENTREGAS.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Plazo (días)">
                  <CampoNumero
                    decimales={0} maxEnteros={3}
                    value={plazo}
                    onChange={(v) => setPlazo(v)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>
            </div>

            <div className="mt-6 overflow-hidden rounded-lg border border-slate-200">
              <table className="w-full text-left text-sm tabular-nums">
                <thead>
                  <tr className="cabecera-tabla border-b border-slate-200">
                    <th className="px-3 py-2.5">Producto</th>
                    <th className="px-3 py-2.5 text-right">Cantidad</th>
                    <th className="w-32 px-3 py-2.5 text-right">Precio unit.</th>
                    <th className="hidden px-3 py-2.5 text-right sm:table-cell">Subtotal</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filas.map((f) => {
                    const v = precios[f.consolidationId] ?? "";
                    const leido = ocr?.precios[f.consolidationId] != null;
                    const sub = Number(v) > 0 ? Number(v) * f.totalQty : null;
                    return (
                      <tr key={f.consolidationId}>
                        <td className="max-w-0 px-3 py-2">
                          <p className="line-clamp-2 text-slate-800" title={f.nombreProducto}>
                            {f.nombreProducto}
                            {f.atributo && <span className="text-slate-400"> · {f.atributo}</span>}
                          </p>
                          <p className="font-mono text-xs text-slate-500">{f.codigo}</p>
                          {ocr && ocr.lineas?.length > 0 && (
                            <LineaDocumento
                              lineas={ocr.lineas}
                              indice={lineaDe[f.consolidationId] ?? null}
                              reconocida={
                                ocr.coincidencias?.[f.consolidationId]?.origen === "EQUIVALENCIA" &&
                                lineaDe[f.consolidationId] === ocr.coincidencias[f.consolidationId].indiceLinea
                              }
                              producto={f.nombreProducto}
                              onElegir={(l) => elegirLinea(f, l)}
                            />
                          )}
                        </td>
                        <td className="whitespace-nowrap px-3 py-2 text-right text-slate-600">
                          {cantidad(f.totalQty)} <span className="text-xs text-slate-400">{f.unidadMedida}</span>
                        </td>
                        <td className="px-3 py-2">
                          <div className="relative">
                            <CampoNumero
                              decimales={4} maxEnteros={9}
                              aria-label={`Precio unitario de ${f.nombreProducto}`}
                              value={v}
                              placeholder="No cotiza"
                              onChange={(v) => setPrecios((p) => ({ ...p, [f.consolidationId]: v }))}
                              className={`${inputClass} w-full py-1.5 text-right ${leido ? "border-brand-200! bg-brand-50!" : ""}`}
                            />
                          </div>
                        </td>
                        <td className="hidden whitespace-nowrap px-3 py-2 text-right text-slate-600 sm:table-cell">
                          {sub != null ? dinero(sub, moneda) : "—"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t border-slate-200 bg-slate-50/70">
                    <td className="px-3 py-2.5 text-xs text-slate-500" colSpan={2}>
                      {cotizados} de {filas.length} productos cotizados
                    </td>
                    <td className="px-3 py-2.5 text-right font-semibold text-slate-900" colSpan={2}>
                      {dinero(total, moneda)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
            {leidos > 0 && (
              <p className="mt-2 flex items-center gap-1.5 text-xs text-slate-500">
                <span className="size-2.5 rounded-sm border border-brand-200 bg-brand-50" aria-hidden="true" /> Leído
                del documento
              </p>
            )}
            {avisoTotal && (
              <p className={`mt-2 text-xs ${avisoTotal.alerta ? "text-amber-700" : "text-slate-500"}`}>
                {avisoTotal.texto}
              </p>
            )}

            {ocr?.textoDetectado && (
              <div className="mt-5">
                <button
                  type="button"
                  onClick={() => setVerTexto((v) => !v)}
                  className="text-xs font-medium text-slate-500 hover:text-slate-800"
                >
                  {verTexto ? "Ocultar texto leído" : "Ver texto leído"}
                </button>
                {verTexto && (
                  <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] text-slate-600">
                    {ocr.textoDetectado}
                  </pre>
                )}
              </div>
            )}
          </div>

          <footer className="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3">
            {error && (
              <Alert tone="danger" className="mr-auto py-1.5 text-xs">
                {error}
              </Alert>
            )}
            <Button type="button" variant="ghost" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" loading={guardando}>
              Guardar cotización
            </Button>
          </footer>
        </form>
      </aside>
    </div>,
    document.body,
  );
}
