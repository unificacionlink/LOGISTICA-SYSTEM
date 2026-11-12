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

  if (cargando) {
    return (
      <Marco>
        <Skeleton className="h-6 w-56" />
        <Skeleton className="mt-2 h-4 w-72" />
        <Card className="mt-6">
          <CardBody className="flex flex-col gap-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="flex items-center gap-4">
                <div className="flex-1">
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="mt-2 h-3 w-1/3" />
                </div>
                <Skeleton className="h-9 w-28" />
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

  return (
    <Marco>
      <header>
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{datos.empresa}</p>
        <h1 className="mt-1 text-xl font-semibold text-slate-900">
          Solicitud de cotización <span className="font-mono">{datos.rfqCode}</span>
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          {datos.proveedor} · RUC {datos.proveedorRuc}
        </p>
        <p className="mt-0.5 text-xs text-slate-500">
          Emitida el {fecha(datos.fechaEmision)} · disponible hasta el {fecha(datos.venceEl)}
          {datos.lugarEntrega ? ` · entrega en ${datos.lugarEntrega}` : ""}
        </p>
      </header>

      {datos.enviada && (
        <Alert tone="good" className="mt-5">
          {recienEnviada ? "Recibimos tu cotización. " : `Enviaste tu cotización el ${fecha(datos.enviada.enviadaEl)}. `}
          Puedes corregirla hasta el {fecha(datos.venceEl)}.
        </Alert>
      )}

      <form onSubmit={onEnviar} className="mt-5 flex flex-col gap-5 pb-28">
        <Card className="overflow-hidden">
          <div className="border-b border-slate-200 px-4 py-3 sm:px-5">
            <h2 className="font-semibold text-slate-900">Productos</h2>
          </div>
          <ul className="divide-y divide-slate-100">
            {datos.lineas.map((l) => {
              const f = filas[l.rfqItemId];
              if (!f) return null;
              const sub = Number(f.precio) > 0 ? Number(f.precio) * l.cantidad : null;
              return (
                <li key={l.rfqItemId} className="px-4 py-3 sm:px-5">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm text-slate-900">
                        <span className="mr-1.5 text-slate-400">{l.numero}.</span>
                        {l.producto}
                        {l.atributo && <span className="text-slate-500"> · {l.atributo}</span>}
                      </p>
                      <p className="mt-0.5 text-xs text-slate-500">
                        <span className="font-mono">{l.sku}</span> · {cantidad(l.cantidad)} {l.unidad}
                      </p>
                      {l.especificacion && <p className="mt-1 text-xs text-slate-500">{l.especificacion}</p>}
                    </div>
                    <div className="flex items-center gap-3 sm:w-64 sm:flex-col sm:items-end sm:gap-1">
                      <CampoNumero
                        decimales={4}
                        maxEnteros={9}
                        value={f.precio}
                        onChange={(v) => cambiar(l.rfqItemId, { precio: v })}
                        placeholder="P. unit. sin IGV"
                        aria-label={`Precio unitario de ${l.producto}`}
                        className={`${inputClass} w-full flex-1 text-right sm:w-40 sm:flex-none`}
                      />
                      <span className="shrink-0 text-xs text-slate-500">
                        {sub != null ? dinero(sub, moneda) : "No cotizo"}
                      </span>
                    </div>
                  </div>
                  {f.conMarca ? (
                    <div className="mt-2 grid grid-cols-2 gap-2 sm:ml-auto sm:max-w-sm">
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
                </li>
              );
            })}
          </ul>
        </Card>

        <Card>
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
            <div className="sm:col-span-3">
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
                  {datos.enviada?.documentoNombre ? "Reemplazar documento adjunto" : "Adjuntar tu cotización (opcional)"}
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
            </div>
          </CardBody>
        </Card>

        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white/95 backdrop-blur">
          <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              {error ? (
                <p className="text-sm text-rose-700">{error}</p>
              ) : (
                <>
                  <p className="text-sm font-semibold text-slate-900">{dinero(total, moneda)}</p>
                  <p className="text-xs text-slate-500">
                    {cotizadas} de {datos.lineas.length} productos · sin IGV
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

function Marco({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50">
      <main className="mx-auto max-w-3xl px-4 py-6 sm:py-10">{children}</main>
    </div>
  );
}
