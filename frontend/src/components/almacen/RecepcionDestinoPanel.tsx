import { useId, useRef, useState } from "react";
import { CheckCircle2, ClipboardCheck } from "lucide-react";
import { recibirEnDestino, resolverObservado } from "../../api/almacen";
import type { AccionObservado, NotaInventarioDetalle, NotaItem, ResultadoRecepcion } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import CampoNumero from "../ui/CampoNumero";
import { inputClass } from "../ui/Field";
import { fmtCantidad, fmtFecha } from "../formato";

const ENLACE = /^https?:\/\/\S+$/i;
const labelClass = "text-[0.8125rem] font-medium text-slate-700";

function sku(it: NotaItem) {
  return it.codigoVariante ?? it.skuBase;
}

/**
 * Recepcion en destino con diferencias (ALM-03 de la V38.7, DEC-L063), entrega
 * directa al CECO: cantidades por producto, resultado conforme o no conforme,
 * nota firmada (archivo) o enlace de la conformidad. Lo observado se subsana o
 * se devuelve al almacen central con enlace de evidencia.
 */
export default function RecepcionDestinoPanel({
  detalle,
  onCambio,
  onError,
}: {
  detalle: NotaInventarioDetalle;
  onCambio: (d: NotaInventarioDetalle, mensaje: string) => void;
  onError: (mensaje: string) => void;
}) {
  const { tieneAlgunRol } = useAuth();
  const { nota, items, recepciones, resoluciones } = detalle;
  const envio = nota.envio;
  const opera = tieneAlgunRol("LOGISTICA", "GERENCIA", "ADMIN");
  const recibible = !!envio && (envio.status === "EN_TRANSITO" || envio.status === "RECEPCION_PARCIAL");
  const observados = items.filter((i) => i.destObservado > 0);
  if (!envio) return null;

  return (
    <>
      {opera && recibible && <FormularioRecepcion detalle={detalle} onCambio={onCambio} onError={onError} />}
      {opera && observados.length > 0 && (
        <Card className="mb-4">
          <CardBody className="flex flex-col gap-3">
            <div className="rotulo">Observado por resolver</div>
            {observados.map((it) => (
              <FilaObservada key={it.issueItemId} item={it} shipmentId={envio.shipmentId} onCambio={onCambio} onError={onError} />
            ))}
          </CardBody>
        </Card>
      )}
      {(recepciones.length > 0 || resoluciones.length > 0) && (
        <Card className="mb-4">
          <CardBody className="flex flex-col gap-3">
            <div className="rotulo">Recepciones en destino</div>
            <ul className="flex flex-col gap-2 text-sm">
              {recepciones.map((r) => (
                <li key={r.destinationReceiptId} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="font-medium text-slate-800">Recepción {r.numero}</span>
                  <Badge tone={r.resultado === "CONFORME" ? "verde" : "rojo"}>
                    {r.resultado === "CONFORME" ? "Conforme" : "No conforme"}
                  </Badge>
                  <span className="text-slate-600">
                    {r.items.map((i) => `${i.sku} × ${fmtCantidad(i.qty)}`).join(" · ")}
                  </span>
                  <span className="text-xs text-slate-400">
                    {fmtFecha(r.fecha)} · {r.recibidoPor}
                  </span>
                  {(r.documentoUrl ?? r.enlaceConformidad) && (
                    <a
                      href={r.documentoUrl ?? r.enlaceConformidad ?? undefined}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-brand-700 hover:underline"
                    >
                      {r.documentoUrl ? "Nota firmada" : "Conformidad firmada"}
                    </a>
                  )}
                  {r.observacion && <span className="w-full whitespace-normal text-slate-500">{r.observacion}</span>}
                </li>
              ))}
              {resoluciones.map((r) => (
                <li key={r.resolutionId} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="font-medium text-slate-800">{r.accion === "SUBSANAR" ? "Subsanado" : "Devuelto"}</span>
                  <span className="text-slate-600">
                    {r.sku} × {fmtCantidad(r.qty)}
                  </span>
                  {r.accion === "DEVOLVER" && (
                    <Badge tone={r.estado === "EN_TRANSITO" ? "amarillo" : "gris"}>
                      {r.estado === "EN_TRANSITO" ? "Retorno en camino" : "En cuarentena"}
                    </Badge>
                  )}
                  <span className="text-xs text-slate-400">
                    {fmtFecha(r.fecha)} · {r.registradoPor}
                  </span>
                  <a href={r.evidencia} target="_blank" rel="noopener noreferrer" className="text-brand-700 hover:underline">
                    Evidencia
                  </a>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      )}
    </>
  );
}

function FormularioRecepcion({
  detalle,
  onCambio,
  onError,
}: {
  detalle: NotaInventarioDetalle;
  onCambio: (d: NotaInventarioDetalle, mensaje: string) => void;
  onError: (mensaje: string) => void;
}) {
  const { nota, items } = detalle;
  const pendientes = items.filter((i) => i.qty - i.destRecibido > 0);
  const [token, setToken] = useState(() => crypto.randomUUID());
  const [cantidades, setCantidades] = useState<Record<string, string>>({});
  const [resultado, setResultado] = useState<ResultadoRecepcion>("CONFORME");
  const [observacion, setObservacion] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enlace, setEnlace] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const primerCampo = useRef<HTMLInputElement>(null);
  const obsRef = useRef<HTMLTextAreaElement>(null);
  const base = useId();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const lineas = pendientes.map((i) => ({ issueItemId: i.issueItemId, qty: Number(cantidades[i.issueItemId] || 0) }));
    if (!lineas.some((l) => l.qty > 0)) {
      setError("Registra cantidades positivas sin superar el saldo enviado.");
      primerCampo.current?.focus();
      return;
    }
    if (resultado === "NO_CONFORME" && !observacion.trim()) {
      setError("Describe la observación.");
      obsRef.current?.focus();
      return;
    }
    if (!archivo && !ENLACE.test(enlace.trim())) {
      setError("Adjunta la nota firmada o registra su enlace.");
      return;
    }
    setError(null);
    setOcupado(true);
    try {
      const res = await recibirEnDestino(
        nota.envio!.shipmentId,
        {
          clientToken: token,
          items: lineas,
          resultado,
          observacion: observacion.trim() || undefined,
          enlaceConformidad: archivo ? undefined : enlace.trim(),
        },
        archivo,
      );
      if (res.ok && res.result) {
        setToken(crypto.randomUUID());
        setCantidades({});
        setObservacion("");
        setArchivo(null);
        setEnlace("");
        setResultado("CONFORME");
        onCambio(res.result, "Recepción registrada. La atención del requerimiento se recalculó con lo aceptado.");
      } else onError(res.message);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <Card className="mb-4">
      <CardBody>
        <form onSubmit={onSubmit} aria-labelledby={`${base}-titulo`} className="flex flex-col gap-4" noValidate>
          <div id={`${base}-titulo`} className="rotulo">
            Confirmar recepción en destino
          </div>
          <fieldset className="flex flex-col gap-2">
            <legend className={`${labelClass} mb-1`}>Cantidad recibida</legend>
            {pendientes.map((it, k) => {
              const saldo = it.qty - it.destRecibido;
              const id = `${base}-q-${it.issueItemId}`;
              return (
                <div key={it.issueItemId} className="flex items-center gap-3">
                  <label htmlFor={id} className="col-flex min-w-0 flex-1 truncate text-sm text-slate-700" title={it.nombreProducto}>
                    <span className="font-mono text-xs text-slate-500">{sku(it)}</span> {it.nombreProducto}
                    <span className="text-slate-400"> · saldo {fmtCantidad(saldo)}</span>
                  </label>
                  <CampoNumero
                    id={id}
                    ref={k === 0 ? primerCampo : undefined}
                    decimales={0}
                    max={saldo}
                    value={cantidades[it.issueItemId] ?? ""}
                    onChange={(v) => {
                      setCantidades({ ...cantidades, [it.issueItemId]: v });
                      setError(null);
                    }}
                    placeholder="0"
                    className={`${inputClass} w-24 text-right`}
                  />
                </div>
              );
            })}
          </fieldset>

          <fieldset className="flex flex-wrap items-center gap-4">
            <legend className={`${labelClass} mb-1`}>Resultado de esta recepción</legend>
            {(["CONFORME", "NO_CONFORME"] as const).map((r) => (
              <span key={r} className="flex items-center gap-2">
                <input
                  type="radio"
                  id={`${base}-r-${r}`}
                  name="resultado"
                  value={r}
                  checked={resultado === r}
                  onChange={() => setResultado(r)}
                  className="accent-brand-700"
                />
                <label htmlFor={`${base}-r-${r}`} className="text-sm text-slate-700">
                  {r === "CONFORME" ? "Conforme" : "No conforme"}
                </label>
              </span>
            ))}
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${base}-obs`} className={labelClass}>
              Observaciones
              {resultado === "NO_CONFORME" && (
                <span className="text-rose-600" aria-hidden="true">
                  {" "}
                  *
                </span>
              )}
            </label>
            <textarea
              id={`${base}-obs`}
              ref={obsRef}
              rows={2}
              maxLength={500}
              required={resultado === "NO_CONFORME"}
              value={observacion}
              onChange={(e) => {
                setObservacion(e.target.value);
                setError(null);
              }}
              className={`${inputClass} w-full whitespace-normal`}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${base}-file`} className={labelClass}>
                Nota de Inventario firmada
              </label>
              <input
                id={`${base}-file`}
                type="file"
                accept="application/pdf,image/png,image/jpeg"
                onChange={(e) => {
                  setArchivo(e.target.files?.[0] ?? null);
                  setError(null);
                }}
                className="text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:text-slate-700 hover:file:bg-slate-200"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${base}-url`} className={labelClass}>
                Enlace de la conformidad firmada (si no adjuntas la nota)
              </label>
              <input
                id={`${base}-url`}
                type="url"
                inputMode="url"
                maxLength={500}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="https://"
                disabled={!!archivo}
                value={enlace}
                onChange={(e) => {
                  setEnlace(e.target.value);
                  setError(null);
                }}
                className={`${inputClass} w-full`}
              />
            </div>
          </div>

          {error && (
            <p role="alert" className="text-sm text-rose-700">
              {error}
            </p>
          )}
          <div>
            <Button type="submit" loading={ocupado} icon={<CheckCircle2 className="size-4" />}>
              Registrar recepción
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}

function FilaObservada({
  item,
  shipmentId,
  onCambio,
  onError,
}: {
  item: NotaItem;
  shipmentId: string;
  onCambio: (d: NotaInventarioDetalle, mensaje: string) => void;
  onError: (mensaje: string) => void;
}) {
  const [abierto, setAbierto] = useState(false);
  const [qty, setQty] = useState("");
  const [accion, setAccion] = useState<AccionObservado>("SUBSANAR");
  const [evidencia, setEvidencia] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const base = useId();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(qty);
    if (!qty || n <= 0 || n > item.destObservado || !ENLACE.test(evidencia.trim())) {
      setError("Resolución, cantidad o evidencia no válida.");
      return;
    }
    setError(null);
    setOcupado(true);
    try {
      const res = await resolverObservado(shipmentId, {
        issueItemId: item.issueItemId,
        qty: n,
        accion,
        evidencia: evidencia.trim(),
      });
      if (res.ok && res.result) {
        setAbierto(false);
        onCambio(res.result, accion === "SUBSANAR" ? "Observado subsanado." : "Devolución registrada: va en camino al almacén central.");
      } else onError(res.message);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 border-t border-slate-100 pt-3 first-of-type:border-t-0 first-of-type:pt-0">
      <div className="flex items-center gap-3">
        <span className="col-flex min-w-0 flex-1 truncate text-sm text-slate-700" title={item.nombreProducto}>
          <span className="font-mono text-xs text-slate-500">{sku(item)}</span> {item.nombreProducto}
        </span>
        <span className="text-sm text-rose-700">{fmtCantidad(item.destObservado)} observado</span>
        {!abierto && (
          <Button size="sm" variant="secondary" icon={<ClipboardCheck className="size-3.5" />} onClick={() => setAbierto(true)}>
            Resolver
          </Button>
        )}
      </div>
      {abierto && (
        <form onSubmit={onSubmit} aria-label={`Resolver ${sku(item)}`} className="flex flex-wrap items-end gap-3" noValidate>
          <div className="flex w-24 flex-col gap-1.5">
            <label htmlFor={`${base}-q`} className={labelClass}>
              Cantidad
            </label>
            <CampoNumero
              id={`${base}-q`}
              decimales={0}
              max={item.destObservado}
              value={qty}
              onChange={setQty}
              className={`${inputClass} w-full text-right`}
            />
          </div>
          <fieldset className="flex items-center gap-3 pb-2">
            <legend className="sr-only">Acción</legend>
            {(["SUBSANAR", "DEVOLVER"] as const).map((a) => (
              <span key={a} className="flex items-center gap-1.5">
                <input
                  type="radio"
                  id={`${base}-${a}`}
                  name={`${base}-accion`}
                  checked={accion === a}
                  onChange={() => setAccion(a)}
                  className="accent-brand-700"
                />
                <label htmlFor={`${base}-${a}`} className="text-sm text-slate-700">
                  {a === "SUBSANAR" ? "Subsanar" : "Devolver"}
                </label>
              </span>
            ))}
          </fieldset>
          <div className="flex min-w-0 flex-1 flex-col gap-1.5">
            <label htmlFor={`${base}-ev`} className={labelClass}>
              Enlace de evidencia
            </label>
            <input
              id={`${base}-ev`}
              type="url"
              inputMode="url"
              maxLength={500}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="https://"
              value={evidencia}
              onChange={(e) => setEvidencia(e.target.value)}
              className={`${inputClass} w-full`}
            />
          </div>
          <Button type="submit" size="sm" loading={ocupado}>
            Guardar
          </Button>
          <Button type="button" size="sm" variant="secondary" onClick={() => setAbierto(false)}>
            Cancelar
          </Button>
          {error && (
            <p role="alert" className="w-full text-sm text-rose-700">
              {error}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
