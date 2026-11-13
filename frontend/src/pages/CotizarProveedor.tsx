import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { CheckCircle2, FileText, Paperclip, Plus, X } from "lucide-react";
import { enviarCotizacionPublica, obtenerCotizacionPublica } from "../api/publico";
import type { CotizacionPublica } from "../api/types";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import CampoNumero from "../components/ui/CampoNumero";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import { Skeleton } from "../components/ui/Skeleton";
import { useConfirmar } from "../components/ui/Confirmacion";
import { Miniatura } from "../components/BuscadorProductos";
import { CONDICIONES, ENTREGAS } from "../components/compras/condiciones";
import { cantidad, dinero } from "../components/compras/formato";

interface Fila {
  precio: string;
  marca: string;
  modelo: string;
  conMarca: boolean;
}

const TAMANO_MAXIMO = 8 * 1024 * 1024;

function fecha(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = iso.length === 10 ? new Date(`${iso}T12:00:00`) : new Date(iso);
  return d.toLocaleDateString("es-PE", { day: "2-digit", month: "2-digit", year: "numeric" });
}

/**
 * Pagina publica del proveedor (DEC-L052): abre su enlace, sin cuenta, y pone
 * sus precios sobre nuestras mismas lineas. Puede corregir mientras el enlace
 * siga vigente; el ultimo envio reemplaza al anterior.
 */
export default function CotizarProveedor() {
  const { token = "" } = useParams<{ token: string }>();
  const confirmar = useConfirmar();
  const [datos, setDatos] = useState<CotizacionPublica | null>(null);
  const [cargando, setCargando] = useState(true);
  const [noDisponible, setNoDisponible] = useState<string | null>(null);

  const [filas, setFilas] = useState<Record<string, Fila>>({});
  const [moneda, setMoneda] = useState("PEN");
  const [condicion, setCondicion] = useState("CONTADO");
  const [entrega, setEntrega] = useState("AGENCIA");
  const [plazo, setPlazo] = useState("");
  const [vigencia, setVigencia] = useState("");
  const [numero, setNumero] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recienEnviada, setRecienEnviada] = useState(false);

  function cargarFormulario(d: CotizacionPublica) {
    const previos = new Map((d.enviada?.precios ?? []).map((p) => [p.rfqItemId, p]));
    setFilas(
      Object.fromEntries(
        d.lineas.map((l) => {
          const p = previos.get(l.rfqItemId);
          return [
            l.rfqItemId,
            {
              precio: p ? String(p.unitCost) : "",
              marca: p?.offeredBrand ?? "",
              modelo: p?.offeredModel ?? "",
              conMarca: !!(p?.offeredBrand || p?.offeredModel),
            },
          ];
        }),
      ),
    );
    const e = d.enviada;
    setMoneda(e?.currencyCode ?? d.monedaSolicitada ?? "PEN");
    setCondicion(e?.paymentCondition ?? d.condicionPago ?? "CONTADO");
    setEntrega(e?.deliveryMode ?? d.modalidadEntrega ?? "AGENCIA");
    setPlazo(e?.leadDays != null ? String(e.leadDays) : d.plazoSolicitadoDias != null ? String(d.plazoSolicitadoDias) : "");
    setVigencia(e?.expirationDate ?? "");
    setNumero(e?.supplierQuoteNumber ?? "");
  }

  useEffect(() => {
    obtenerCotizacionPublica(token)
      .then((res) => {
        if (res.ok && res.result) {
          setDatos(res.result);
          cargarFormulario(res.result);
        } else {
          setNoDisponible(res.message);
        }
      })
      .finally(() => setCargando(false));
  }, [token]);

  const total = useMemo(
    () =>
      (datos?.lineas ?? []).reduce((s, l) => {
        const p = Number(filas[l.rfqItemId]?.precio);
        return p > 0 ? s + p * l.cantidad : s;
      }, 0),
    [datos, filas],
  );
  const cotizadas = (datos?.lineas ?? []).filter((l) => Number(filas[l.rfqItemId]?.precio) > 0).length;

  function cambiar(id: string, cambios: Partial<Fila>) {
    setFilas((prev) => ({ ...prev, [id]: { ...prev[id], ...cambios } }));
  }

  function elegirArchivo(f: File | null) {
    setError(null);
    if (f && f.size > TAMANO_MAXIMO) {
      setError("El archivo pesa más de 8 MB.");
      return;
    }
    if (f && !["application/pdf", "image/jpeg", "image/png"].includes(f.type)) {
      setError("Adjunta un PDF, JPG o PNG.");
      return;
    }
    setArchivo(f);
  }

  async function onEnviar(e: React.FormEvent) {
    e.preventDefault();
    if (!datos) return;
    const precios = datos.lineas
      .filter((l) => filas[l.rfqItemId]?.precio.trim())
      .map((l) => {
        const f = filas[l.rfqItemId];
        return {
          rfqItemId: l.rfqItemId,
          unitCost: Number(f.precio),
          offeredBrand: f.marca.trim() || undefined,
          offeredModel: f.modelo.trim() || undefined,
        };
      });
    if (precios.length === 0) {
      setError("Ingresa el precio de al menos un producto.");
      return;
    }
    if (precios.some((p) => !(p.unitCost > 0))) {
      setError("Los precios deben ser mayores a 0.");
      return;
    }
    const sinPrecio = datos.lineas.length - precios.length;
    const r = await confirmar({
      titulo: datos.enviada ? "¿Reemplazar tu cotización anterior?" : "¿Enviar tu cotización?",
      mensaje: `${precios.length} de ${datos.lineas.length} productos · ${dinero(total, moneda)} sin IGV${
        sinPrecio > 0 ? `. ${sinPrecio} quedan como no cotizados.` : "."
      }`,
      confirmar: "Enviar",
    });
    if (!r) return;
    setError(null);
    setEnviando(true);
    try {
      const res = await enviarCotizacionPublica(
        token,
        {
          currencyCode: moneda,
          paymentCondition: condicion,
          deliveryMode: entrega,
          leadDays: plazo.trim() ? Number(plazo) : undefined,
          expirationDate: vigencia || undefined,
          supplierQuoteNumber: numero.trim() || undefined,
          precios,
        },
        archivo,
      );
      if (res.ok && res.result) {
        setDatos(res.result);
        cargarFormulario(res.result);
        setArchivo(null);
        setRecienEnviada(true);
        window.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        setError(res.message);
      }
    } finally {
      setEnviando(false);
    }
  }

  /** Enter en un precio pasa al siguiente (llenado rapido con teclado). */
  function alSiguientePrecio(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const campos = Array.from(document.querySelectorAll<HTMLInputElement>("input[data-precio]"));
    const i = campos.indexOf(e.currentTarget);
    if (i >= 0 && i < campos.length - 1) {
      campos[i + 1].focus();
      campos[i + 1].select();
    } else {
      e.currentTarget.blur();
    }
  }

  if (cargando) {
    return (
      <Marco>
        <Skeleton className="h-4 w-40" />
        <Skeleton className="mt-2 h-7 w-80" />
        <div className="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i}>
              <Skeleton className="h-3 w-16" />
              <Skeleton className="mt-1.5 h-4 w-28" />
            </div>
          ))}
        </div>
        <Card className="mt-6">
          <CardBody className="grid gap-4 sm:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-9 w-full" />
            ))}
          </CardBody>
        </Card>
        <Card className="mt-5">
          <CardBody className="flex flex-col gap-5">
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex items-center gap-4">
                <Skeleton className="size-14 shrink-0" />
                <div className="flex-1">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="mt-2 h-3 w-1/3" />
                </div>
                <Skeleton className="hidden h-9 w-36 sm:block" />
              </div>
            ))}
          </CardBody>
        </Card>
      </Marco>
    );
  }

  if (!datos) {
    return (
      <Marco>
        <Card>
          <CardBody className="py-10 text-center">
            <p className="font-medium text-slate-900">Enlace no disponible</p>
            <p className="mt-1 text-sm text-slate-500">{noDisponible}</p>
          </CardBody>
        </Card>
      </Marco>
    );
  }

  const pendientes = datos.lineas.length - cotizadas;

  return (
    <Marco>
      <header className="border-b border-slate-200 pb-5">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{datos.empresa}</p>
        <div className="mt-1 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h1 className="text-xl font-semibold text-slate-900">Solicitud de cotización</h1>
          <span className="font-mono text-base font-medium text-slate-700">{datos.rfqCode}</span>
        </div>
        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-4">
          <Dato etiqueta="Proveedor" valor={datos.proveedor} detalle={`RUC ${datos.proveedorRuc}`} />
          <Dato etiqueta="Emitida" valor={fecha(datos.fechaEmision)} />
          <Dato etiqueta="Responder hasta" valor={fecha(datos.venceEl)} />
          <Dato etiqueta="Lugar de entrega" valor={datos.lugarEntrega || "—"} />
        </dl>
      </header>

      {datos.enviada && (
        <Alert tone="good" className="mt-5">
          {recienEnviada ? "Recibimos tu cotización. " : `Enviaste tu cotización el ${fecha(datos.enviada.enviadaEl)}. `}
          Puedes corregirla hasta el {fecha(datos.venceEl)}.
        </Alert>
      )}

      <form onSubmit={onEnviar} className="mt-5 flex flex-col gap-5 pb-28">
        <Card>
          <div className="border-b border-slate-200 px-4 py-3 sm:px-5">
            <h2 className="font-semibold text-slate-900">Condiciones de tu oferta</h2>
          </div>
          <CardBody className="grid gap-4 sm:grid-cols-3">
            <Field label="Moneda" required>
              <select value={moneda} onChange={(e) => setMoneda(e.target.value)} className={`${inputClass} w-full`}>
                {(datos.monedas.length ? datos.monedas : [{ currencyCode: "PEN", name: "Soles" }]).map((m) => (
                  <option key={m.currencyCode} value={m.currencyCode}>
                    {m.currencyCode} · {m.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Condición de pago" required>
              <select value={condicion} onChange={(e) => setCondicion(e.target.value)} className={`${inputClass} w-full`}>
                {CONDICIONES.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Entrega" required>
              <select value={entrega} onChange={(e) => setEntrega(e.target.value)} className={`${inputClass} w-full`}>
                {ENTREGAS.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Plazo de entrega (días)">
              <CampoNumero decimales={0} maxEnteros={3} value={plazo} onChange={setPlazo} className={`${inputClass} w-full`} />
            </Field>
            <Field label="Oferta válida hasta">
              <input type="date" value={vigencia} onChange={(e) => setVigencia(e.target.value)} className={`${inputClass} w-full`} />
            </Field>
            <Field label="N° de tu cotización">
              <input value={numero} onChange={(e) => setNumero(e.target.value)} maxLength={60} className={`${inputClass} w-full`} />
            </Field>
          </CardBody>
        </Card>

        <Card className="overflow-hidden">
          <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-200 px-4 py-3 sm:px-5">
            <h2 className="font-semibold text-slate-900">Productos</h2>
            <span className={`text-xs ${pendientes > 0 ? "text-slate-500" : "font-medium text-emerald-700"}`}>
              {pendientes > 0 ? `${cotizadas} de ${datos.lineas.length} con precio` : "Todos con precio"}
            </span>
          </div>
          <div
            aria-hidden="true"
            className={`hidden border-b border-slate-200 bg-slate-50 px-5 py-2 text-xs font-medium text-slate-500 md:grid ${COLUMNAS}`}
          >
            <span>N°</span>
            <span />
            <span>Producto</span>
            <span className="text-right">Cantidad</span>
            <span className="text-right">Precio unitario</span>
            <span className="text-right">Subtotal</span>
          </div>
          <ul className="divide-y divide-slate-100">
            {datos.lineas.map((l) => {
              const f = filas[l.rfqItemId];
              if (!f) return null;
              const sub = Number(f.precio) > 0 ? Number(f.precio) * l.cantidad : null;
              const unidad = l.unidadNombre?.toLowerCase() ?? null;
              return (
                <li
                  key={l.rfqItemId}
                  className={`grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-3 gap-y-3 px-4 py-4 md:items-start sm:px-5 ${COLUMNAS}`}
                >
                  <span className="hidden pt-1 font-mono text-xs text-slate-400 md:block">{l.numero}</span>
                  <Miniatura
                    grande
                    url={l.imagenUrl}
                    titulo={l.producto}
                    detalle={`${l.sku}${l.atributo ? ` · ${l.atributo}` : ""}`}
                  />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900">
                      <span className="mr-1 font-mono text-xs font-normal text-slate-400 md:hidden">{l.numero}.</span>
                      {l.producto}
                      {l.atributo && <span className="font-normal text-slate-500"> · {l.atributo}</span>}
                    </p>
                    <p className="mt-0.5 font-mono text-xs text-slate-500">{l.sku}</p>
                    {l.especificacion && <p className="mt-1 text-xs text-slate-600">{l.especificacion}</p>}
                    {f.conMarca ? (
                      <div className="mt-2 grid max-w-sm grid-cols-2 gap-2">
                        <input
                          value={f.marca}
                          onChange={(e) => cambiar(l.rfqItemId, { marca: e.target.value })}
                          maxLength={100}
                          placeholder="Marca"
                          aria-label={`Marca de ${l.producto}`}
                          className={`${inputClass} py-1.5 text-sm`}
                        />
                        <input
                          value={f.modelo}
                          onChange={(e) => cambiar(l.rfqItemId, { modelo: e.target.value })}
                          maxLength={120}
                          placeholder="Modelo"
                          aria-label={`Modelo de ${l.producto}`}
                          className={`${inputClass} py-1.5 text-sm`}
                        />
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => cambiar(l.rfqItemId, { conMarca: true })}
                        className="mt-1.5 inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        <Plus className="size-3.5" /> Marca y modelo
                      </button>
                    )}
                  </div>

                  <div className="col-span-2 grid grid-cols-2 items-start gap-x-3 gap-y-1 md:contents">
                    <div className="md:text-right">
                      <p className="text-xs text-slate-500 md:hidden">Cantidad</p>
                      <p className="text-base font-semibold tabular-nums text-slate-900">{cantidad(l.cantidad)}</p>
                      <p className={`text-xs ${unidad ? "text-slate-600" : "text-amber-700"}`}>
                        {unidad ? plural(unidad, l.cantidad) : "Unidad por definir"}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-slate-500 md:hidden">Precio</p>
                      <CampoNumero
                        data-precio=""
                        decimales={4}
                        maxEnteros={9}
                        value={f.precio}
                        onChange={(v) => cambiar(l.rfqItemId, { precio: v })}
                        onKeyDown={alSiguientePrecio}
                        enterKeyHint="next"
                        placeholder="0.00"
                        aria-label={`Precio ${unidad ? `por ${unidad}` : "unitario"} de ${l.producto}`}
                        className={`${inputClass} w-full text-right tabular-nums`}
                      />
                      <p className="mt-1 text-right text-xs text-slate-500">
                        {unidad ? `por ${unidad}` : "por unidad de venta"}, sin IGV
                      </p>
                    </div>
                    <p className="col-span-2 text-right text-sm tabular-nums text-slate-700 md:col-span-1 md:pt-2">
                      <span className="mr-1 text-xs text-slate-500 md:hidden">Subtotal</span>
                      {sub != null ? dinero(sub, moneda) : "—"}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
          <div className="flex items-baseline justify-end gap-3 border-t border-slate-200 bg-slate-50 px-4 py-3 sm:px-5">
            <span className="text-sm text-slate-500">Total sin IGV</span>
            <span className="text-base font-semibold tabular-nums text-slate-900">{dinero(total, moneda)}</span>
          </div>
        </Card>

        <Card>
          <CardBody>
            {archivo ? (
              <span className="inline-flex max-w-full items-center gap-2 rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700">
                <FileText className="size-4 shrink-0 text-slate-400" />
                <span className="truncate">{archivo.name}</span>
                <button type="button" onClick={() => setArchivo(null)} aria-label="Quitar archivo" className="text-slate-400 hover:text-rose-600">
                  <X className="size-4" />
                </button>
              </span>
            ) : (
              <label className="inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700">
                <Paperclip className="size-4" />
                {datos.enviada?.documentoNombre ? "Reemplazar documento adjunto" : "Adjuntar tu cotización en PDF o foto (opcional)"}
                <input
                  type="file"
                  accept="application/pdf,image/jpeg,image/png"
                  className="sr-only"
                  onChange={(e) => {
                    elegirArchivo(e.target.files?.[0] ?? null);
                    e.target.value = "";
                  }}
                />
              </label>
            )}
            {!archivo && datos.enviada?.documentoNombre && (
              <p className="mt-1 text-xs text-slate-500">Adjuntado: {datos.enviada.documentoNombre}</p>
            )}
          </CardBody>
        </Card>

        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              {error ? (
                <p className="text-sm text-rose-700">{error}</p>
              ) : (
                <>
                  <p className="text-sm font-semibold tabular-nums text-slate-900">{dinero(total, moneda)}</p>
                  <p className="text-xs text-slate-500">
                    {cotizadas} de {datos.lineas.length} productos con precio · sin IGV
                  </p>
                </>
              )}
            </div>
            <Button type="submit" loading={enviando} icon={recienEnviada ? <CheckCircle2 className="size-4" /> : undefined}>
              {datos.enviada ? "Enviar corrección" : "Enviar cotización"}
            </Button>
          </div>
        </div>
      </form>
    </Marco>
  );
}

/** Columnas de la tabla desde pantallas medianas: N°, foto, producto, cantidad, precio, subtotal. */
const COLUMNAS = "md:grid-cols-[1.5rem_3.5rem_minmax(0,1fr)_6rem_9rem_7rem] md:gap-x-4";

/** "millar" -> "millares", "caja" -> "cajas"; en singular si es 1. */
function plural(unidad: string, n: number): string {
  if (n === 1) return unidad;
  return /[aeiou]$/.test(unidad) ? `${unidad}s` : `${unidad}es`;
}

function Dato({ etiqueta, valor, detalle }: { etiqueta: string; valor: string; detalle?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{etiqueta}</dt>
      <dd className="mt-0.5 truncate font-medium text-slate-900" title={valor}>
        {valor}
      </dd>
      {detalle && <dd className="font-mono text-xs text-slate-500">{detalle}</dd>}
    </div>
  );
}

function Marco({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50">
      <main className="mx-auto max-w-5xl px-4 py-6 sm:py-10">{children}</main>
    </div>
  );
}
