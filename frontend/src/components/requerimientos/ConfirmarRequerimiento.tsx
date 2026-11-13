import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Check, X } from "lucide-react";
import { Miniatura } from "../BuscadorProductos";
import { EMPRESAS } from "../empresas";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import Field, { inputClass } from "../ui/Field";

/** Un producto del pedido ya repartido entre almacen, compra y caja chica (estimado al momento). */
export interface LineaConfirmacion {
  key: string;
  nombre: string;
  detalle: string;
  imagenUrl: string | null;
  cantidad: number;
  delAlmacen: number;
  aComprar: number;
  cajaChica: number;
  /** Hay stock en almacen (lo unico que ve quien no es Logistica, DEC-L059). */
  conStock: boolean;
}

const PRIORIDAD: Record<string, { label: string; clase: string }> = {
  NORMAL: { label: "Normal", clase: "bg-slate-100 text-slate-700" },
  URGENTE: { label: "Urgente", clase: "bg-amber-50 text-amber-700" },
  CRITICA: { label: "Crítica", clase: "bg-rose-50 text-rose-700" },
};

const PROPOSITO: Record<string, string> = {
  CONSUMO: "Consumo",
  VENTA: "Venta",
  PRESTAMO: "Préstamo",
  IMPLEMENTACION: "Implementación",
};

function fecha(iso: string) {
  return new Date(`${iso}T12:00:00`).toLocaleDateString("es-PE", { day: "2-digit", month: "short", year: "numeric" });
}

const n = (v: number) => v.toLocaleString("es-PE");

/**
 * Ventana unica para cerrar un requerimiento: resumen del pedido y, en el
 * mismo paso, guardarlo y enviarlo a aprobacion. Si el control presupuestal
 * pide justificacion (DEC-L038) se escribe aqui mismo, sin salir.
 */
export default function ConfirmarRequerimiento({
  editando,
  empresaCode,
  cecoCodigo,
  cecoNombre,
  prioridad,
  proposito,
  fechaRequerida,
  atencionEstimada,
  lineas,
  conCantidades,
  procesando,
  guardado,
  excepcion,
  error,
  onCancelar,
  onConfirmar,
}: {
  editando: boolean;
  empresaCode: string | null;
  cecoCodigo: string;
  cecoNombre: string;
  prioridad: string;
  proposito: string;
  fechaRequerida: string;
  atencionEstimada: string | null;
  lineas: LineaConfirmacion[];
  /** Logistica/Admin: reparto almacen / compra en unidades. El resto solo ve con o sin stock. */
  conCantidades: boolean;
  procesando: boolean;
  /** Ya quedo guardado como borrador (falta solo enviarlo). */
  guardado: boolean;
  /** Mensaje del control presupuestal cuando el pedido sale del presupuesto. */
  excepcion: string | null;
  error: string | null;
  onCancelar: () => void;
  onConfirmar: (enviarAhora: boolean, justificacionPresupuestal?: string) => void;
}) {
  const [enviarAhora, setEnviarAhora] = useState(true);
  const [justificacion, setJustificacion] = useState("");

  // Esc cierra; al cerrar el foco vuelve a donde estaba. Un solo registro (con
  // la ultima version de las props) para no robar el foco mientras se escribe.
  const ultimo = useRef({ procesando, onCancelar });
  useEffect(() => {
    ultimo.current = { procesando, onCancelar };
  });
  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !ultimo.current.procesando) ultimo.current.onCancelar();
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      anterior?.focus?.();
    };
  }, []);

  const empresa = EMPRESAS.find((e) => e.code === empresaCode);
  const prio = PRIORIDAD[prioridad] ?? PRIORIDAD.NORMAL;
  const unidades = lineas.reduce((s, l) => s + l.cantidad, 0);
  const delAlmacen = lineas.reduce((s, l) => s + l.delAlmacen, 0);
  const aComprar = lineas.reduce((s, l) => s + l.aComprar, 0);
  const cajaChica = lineas.reduce((s, l) => s + l.cajaChica, 0);
  const conStock = lineas.filter((l) => l.conStock).length;
  const faltaJustificacion = !!excepcion && !justificacion.trim();

  function aceptar(e: React.FormEvent) {
    e.preventDefault();
    if (faltaJustificacion) return;
    onConfirmar(excepcion ? true : enviarAhora, excepcion ? justificacion.trim() : undefined);
  }

  const principal = excepcion
    ? "Enviar con justificación"
    : enviarAhora
      ? "Confirmar y enviar"
      : guardado
        ? "Ver requerimiento"
        : editando
          ? "Guardar cambios"
          : "Guardar borrador";

  return createPortal(
    <div
      className="animate-capa-in fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/30 p-4"
      onMouseDown={(e) => e.target === e.currentTarget && !procesando && onCancelar()}
    >
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmar-req-titulo"
        onSubmit={aceptar}
        className="flex max-h-full w-full max-w-xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-flotante"
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0">
            <h2 id="confirmar-req-titulo" className="text-[1.0625rem] font-semibold text-slate-900">
              Confirmar requerimiento
            </h2>
            <p className="mt-0.5 truncate text-sm text-slate-500">
              {empresa?.label ?? empresaCode} · <span className="font-mono text-xs">{cecoCodigo}</span> {cecoNombre}
            </p>
          </div>
          <button
            type="button"
            onClick={onCancelar}
            disabled={procesando}
            aria-label="Cerrar"
            className="-mr-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50"
          >
            <X className="size-4.5" />
          </button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-5 py-4">
          <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-4">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${prio.clase}`}>{prio.label}</span>
              <span className="text-slate-600">{PROPOSITO[proposito] ?? proposito}</span>
              {fechaRequerida && <span className="text-slate-500">· Para el {fecha(fechaRequerida)}</span>}
              {atencionEstimada && <span className="text-slate-500">· Atención estimada {atencionEstimada}</span>}
            </div>
            {conCantidades ? (
              <dl className={`mt-3 grid gap-3 border-t border-slate-200 pt-3 ${cajaChica > 0 ? "grid-cols-2 sm:grid-cols-4" : "grid-cols-3"}`}>
                <Indicador etiqueta="Productos" valor={n(lineas.length)} detalle={`${n(unidades)} unidades`} />
                <Indicador etiqueta="Del almacén" valor={n(delAlmacen)} detalle="unidades" tono="text-emerald-700" />
                <Indicador etiqueta="Por comprar" valor={n(aComprar)} detalle="unidades" tono={aComprar > 0 ? "text-amber-700" : undefined} />
                {cajaChica > 0 && <Indicador etiqueta="Caja chica" valor={n(cajaChica)} detalle="unidades" />}
              </dl>
            ) : (
              <dl className={`mt-3 grid gap-3 border-t border-slate-200 pt-3 ${cajaChica > 0 ? "grid-cols-3" : "grid-cols-2"}`}>
                <Indicador etiqueta="Productos" valor={n(lineas.length)} detalle={`${n(unidades)} unidades`} />
                <Indicador etiqueta="Con stock" valor={n(conStock)} detalle={conStock === 1 ? "producto" : "productos"} tono="text-emerald-700" />
                {cajaChica > 0 && <Indicador etiqueta="Caja chica" valor={n(cajaChica)} detalle="unidades" />}
              </dl>
            )}
          </div>

          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
            {lineas.map((l) => (
              <li key={l.key} className="flex items-center gap-3 px-3 py-2.5">
                <Miniatura url={l.imagenUrl} titulo={l.nombre} detalle={l.detalle} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-slate-900" title={l.nombre}>
                    {l.nombre}
                  </p>
                  <div className="mt-0.5 flex flex-wrap gap-1.5 text-xs">
                    {conCantidades ? (
                      <>
                        {l.delAlmacen > 0 && <span className="text-emerald-700">Almacén {n(l.delAlmacen)}</span>}
                        {l.aComprar > 0 && <span className="text-amber-700">Se compran {n(l.aComprar)}</span>}
                      </>
                    ) : l.conStock ? (
                      <span className="text-emerald-700">Con stock</span>
                    ) : (
                      <span className="text-slate-500">Sin stock</span>
                    )}
                    {l.cajaChica > 0 && <span className="text-slate-600">Caja chica {n(l.cajaChica)}</span>}
                  </div>
                </div>
                <span className="shrink-0 text-sm font-semibold tabular-nums text-slate-900">{n(l.cantidad)}</span>
              </li>
            ))}
          </ul>

          {excepcion ? (
            <div className="flex flex-col gap-3">
              <Alert tone="info">{excepcion}</Alert>
              <Field label="Justificación presupuestal" required>
                <textarea
                  autoFocus
                  rows={3}
                  maxLength={500}
                  value={justificacion}
                  onChange={(e) => setJustificacion(e.target.value)}
                  className={`${inputClass} w-full`}
                />
              </Field>
            </div>
          ) : (
            <label className="flex cursor-pointer items-start gap-2.5 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={enviarAhora}
                onChange={(e) => setEnviarAhora(e.target.checked)}
                className="mt-0.5 size-4 rounded border-slate-300 accent-brand-700"
              />
              <span>
                Enviar a aprobación ahora
                <span className="block text-xs text-slate-500">
                  {enviarAhora ? "Tu aprobador lo recibe al instante." : "Queda en borrador para enviarlo después."}
                </span>
              </span>
            </label>
          )}

          {error && <Alert tone="danger">{error}</Alert>}
        </div>

        <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
          <Button variant="secondary" onClick={onCancelar} disabled={procesando}>
            {guardado ? "Cerrar" : "Seguir editando"}
          </Button>
          <Button type="submit" autoFocus={!excepcion} loading={procesando} disabled={faltaJustificacion} icon={<Check className="size-4" />}>
            {principal}
          </Button>
        </div>
      </form>
    </div>,
    document.body,
  );
}

function Indicador({ etiqueta, valor, detalle, tono }: { etiqueta: string; valor: string; detalle: string; tono?: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{etiqueta}</dt>
      <dd className={`mt-0.5 text-lg font-semibold tabular-nums ${tono ?? "text-slate-900"}`}>{valor}</dd>
      <dd className="text-xs text-slate-500">{detalle}</dd>
    </div>
  );
}
