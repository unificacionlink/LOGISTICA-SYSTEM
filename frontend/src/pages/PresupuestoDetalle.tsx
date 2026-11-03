import { useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { X } from "lucide-react";
import {
  anularPresupuesto,
  catalogoPresupuesto,
  decidirPresupuesto,
  emitirPresupuesto,
  guardarLineas,
  modificarPresupuesto,
  obtenerPresupuesto,
} from "../api/presupuestos";
import type { ApiResponse } from "../api/http";
import type { PresupuestoDetalle as Detalle, ProductoPresupuesto } from "../api/types";
import PresupuestoEstadoBadge from "../components/PresupuestoEstadoBadge";
import BuscadorSku from "../components/presupuestos/BuscadorSku";
import CumplimientoPresupuesto from "../components/presupuestos/CumplimientoPresupuesto";
import ExcepcionesPresupuesto from "../components/presupuestos/ExcepcionesPresupuesto";
import { etiquetaPeriodo, mesesDelSemestre, soles } from "../components/presupuestos/formato";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import { inputClass } from "../components/ui/Field";
import MenuAcciones, { type AccionMenu } from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";

/** Partida en edicion: las cantidades se editan como texto. */
interface Borrador {
  productId: string;
  sku: string;
  nombre: string;
  familia: string;
  unidad: string;
  precio: number | null;
  meses: string[];
}

const ACCION: Record<string, string> = {
  CREAR: "Creado",
  EMITIR: "Emitido",
  OBSERVAR: "Observado",
  APROBAR: "Aprobado",
  MODIFICAR: "En modificación",
  ANULAR: "Anulado",
};

const cantidad = new Intl.NumberFormat("es-PE", { maximumFractionDigits: 3 });

function fmtFechaHora(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

export default function PresupuestoDetalle() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const confirmar = useConfirmar();
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(searchParams.get("msg"));
  const [procesando, setProcesando] = useState(false);
  const [borrador, setBorrador] = useState<Borrador[] | null>(null);
  const [catalogo, setCatalogo] = useState<ProductoPresupuesto[] | null>(null);
  const [vista, setVista] = useState<"programacion" | "cumplimiento">("programacion");

  useEffect(() => {
    if (!id) return;
    obtenerPresupuesto(id)
      .then((res) => {
        if (res.ok) setDetalle(res.result);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, [id]);

  const excluir = useMemo(() => new Set(borrador?.map((b) => b.productId)), [borrador]);

  /** Ejecuta una accion que devuelve el detalle actualizado. */
  async function ejecutar(accion: () => Promise<ApiResponse<Detalle>>) {
    setProcesando(true);
    setError(null);
    setExito(null);
    const res = await accion();
    setProcesando(false);
    if (res.ok && res.result) {
      setDetalle(res.result);
      setExito(res.message);
      return true;
    }
    setError(res.message);
    return false;
  }

  function editar(d: Detalle) {
    setBorrador(
      d.lineas.map((l) => ({
        productId: l.productId,
        sku: l.sku,
        nombre: l.nombre,
        familia: l.familia,
        unidad: l.unidad,
        precio: l.precio,
        meses: l.meses.map((q) => String(Number(q))),
      })),
    );
    if (!catalogo && id) {
      catalogoPresupuesto(id).then((res) => {
        if (res.ok) setCatalogo(res.result ?? []);
        else setError(res.message);
      });
    }
  }

  async function guardar() {
    if (!id || !borrador) return;
    const lineas = borrador.map((b) => ({ productId: b.productId, meses: b.meses.map((q) => Number(q || 0)) }));
    if (lineas.some((l) => l.meses.some((q) => !Number.isFinite(q) || q < 0))) {
      setError("Ingresa cantidades mayores o iguales a cero.");
      return;
    }
    if (await ejecutar(() => guardarLineas(id, lineas))) setBorrador(null);
  }

  async function observar() {
    if (!id) return;
    const r = await confirmar({ titulo: "¿Observar el presupuesto?", confirmar: "Observar", motivo: "Motivo de la observación" });
    if (r) await ejecutar(() => decidirPresupuesto(id, "OBSERVADO", r.motivo));
  }

  async function anular() {
    if (!id) return;
    const r = await confirmar({ titulo: "¿Anular el presupuesto?", confirmar: "Anular", peligro: true, motivo: "Motivo de la anulación" });
    if (r) await ejecutar(() => anularPresupuesto(id, r.motivo));
  }

  async function emitir(d: Detalle) {
    if (!id) return;
    const ok = await confirmar({
      titulo: "¿Emitir el presupuesto?",
      mensaje: `Pasa a aprobación con ${d.lineas.length} ${d.lineas.length === 1 ? "partida" : "partidas"}.`,
      confirmar: "Emitir",
    });
    if (ok) await ejecutar(() => emitirPresupuesto(id));
  }

  async function modificar() {
    if (!id) return;
    const ok = await confirmar({
      titulo: "¿Modificar el presupuesto aprobado?",
      mensaje: "La versión aprobada sigue rigiendo hasta que se apruebe la nueva.",
      confirmar: "Modificar",
    });
    if (ok) await ejecutar(() => modificarPresupuesto(id));
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Card className="mb-4">
          <CardBody className="grid gap-4 sm:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </CardBody>
        </Card>
        <Card className="overflow-hidden">
          <SkeletonTable cols={8} />
        </Card>
      </div>
    );
  }
  if (!detalle) return error ? <Alert tone="danger">{error}</Alert> : null;

  const d = detalle;
  const meses = mesesDelSemestre(d.semestre);
  const editando = borrador !== null;
  const menu: AccionMenu[] = [];
  if (d.permisos.anular) menu.push({ label: "Anular presupuesto", peligro: true, disabled: procesando, onClick: anular });

  return (
    <div>
      <PageHeader
        title={<span className="font-mono">{d.codigo}</span>}
        subtitle={
          <>
            {d.empresa} · {d.cecoCode} {d.cecoNombre} · {etiquetaPeriodo(d.anio, d.semestre)}
          </>
        }
        backTo="/presupuestos"
        backLabel="Presupuestos"
        actions={
          <>
            {d.controlObligatorio && <Badge tone="azul">Control obligatorio</Badge>}
            <PresupuestoEstadoBadge estado={d.estado} />
          </>
        }
      />

      <div className="mb-4 flex flex-col gap-3">
        {exito && <Alert tone="good">{exito}</Alert>}
        {error && <Alert tone="danger">{error}</Alert>}
        {d.periodoCerrado && <Alert tone="info">Periodo cerrado: solo consulta.</Alert>}
        {(d.estado === "OBSERVADO" || d.estado === "ANULADO") && d.motivoDecision && (
          <Alert tone="danger">
            {d.estado === "OBSERVADO" ? "Observado" : "Anulado"}: {d.motivoDecision}
          </Alert>
        )}
        {d.estado === "MODIFICADO" && d.versionVigente && (
          <Alert tone="info">Rige la versión {d.versionVigente} aprobada hasta que se apruebe la nueva.</Alert>
        )}
      </div>

      {(d.permisos.decidir || d.permisos.editar || d.permisos.modificar || menu.length > 0) && (
        <Card className="mb-4">
          <CardBody className="flex flex-wrap items-center gap-2">
            {d.permisos.decidir ? (
              <>
                <Button loading={procesando} onClick={() => ejecutar(() => decidirPresupuesto(d.id, "APROBADO"))}>
                  Aprobar
                </Button>
                <Button variant="ghost" disabled={procesando} onClick={observar}>
                  Observar
                </Button>
              </>
            ) : editando ? (
              <>
                <Button loading={procesando} onClick={guardar}>
                  Guardar programación
                </Button>
                <Button variant="ghost" disabled={procesando} onClick={() => setBorrador(null)}>
                  Cancelar
                </Button>
              </>
            ) : d.permisos.editar ? (
              <>
                {d.permisos.emitir && (
                  <Button loading={procesando} onClick={() => emitir(d)}>
                    Emitir
                  </Button>
                )}
                <Button variant={d.permisos.emitir ? "ghost" : "primary"} disabled={procesando} onClick={() => editar(d)}>
                  {d.lineas.length === 0 ? "Programar partidas" : "Editar programación"}
                </Button>
              </>
            ) : d.permisos.modificar ? (
              <Button loading={procesando} onClick={modificar}>
                Modificar
              </Button>
            ) : null}
            {!editando && <MenuAcciones acciones={menu} />}
          </CardBody>
        </Card>
      )}

      <Card className="mb-4">
        <CardBody className="grid gap-4 sm:grid-cols-4">
          <div>
            <div className="rotulo">Solicitante</div>
            <div className="mt-1 text-sm text-slate-700">{d.solicitante}</div>
          </div>
          <div>
            <div className="rotulo">Aprobador</div>
            <div className="mt-1 text-sm text-slate-700">{d.aprobador ?? "Se asigna al emitir"}</div>
          </div>
          <div>
            <div className="rotulo">Versión vigente</div>
            <div className="mt-1 text-sm text-slate-700">
              {d.versionVigente ? (
                <>
                  v{d.versionVigente}{" "}
                  <span className="text-xs text-slate-400">
                    · {fmtFechaHora(d.aprobadoEn)} · {d.aprobadoPor}
                  </span>
                </>
              ) : (
                "Sin aprobar"
              )}
            </div>
          </div>
          <div>
            <div className="rotulo">Total PEN neto</div>
            <div className="mt-1 text-sm font-medium tabular-nums text-slate-900">
              {d.total == null ? "—" : soles.format(d.total)}
              {d.lineasSinPrecio > 0 && (
                <span className="block text-xs font-normal text-amber-600">{d.lineasSinPrecio} SKU sin precio de venta</span>
              )}
            </div>
          </div>
          {d.observaciones && (
            <div className="sm:col-span-4">
              <div className="rotulo">Observaciones</div>
              <div className="mt-1 text-sm text-slate-700">{d.observaciones}</div>
            </div>
          )}
        </CardBody>
      </Card>

      <ExcepcionesPresupuesto excepciones={d.excepciones} />

      {d.versionVigente && !editando && (
        <div role="tablist" aria-label="Vista del presupuesto" className="mb-4 flex gap-1 border-b border-slate-200">
          {(["programacion", "cumplimiento"] as const).map((v) => (
            <button
              key={v}
              type="button"
              role="tab"
              aria-selected={vista === v}
              onClick={() => setVista(v)}
              className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                vista === v ? "border-brand-700 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              {v === "programacion" ? "Programación" : "Cumplimiento"}
            </button>
          ))}
        </div>
      )}

      {vista === "cumplimiento" && d.versionVigente && !editando ? (
        <div className="mb-4">
          <CumplimientoPresupuesto presupuestoId={d.id} />
        </div>
      ) : (
      <Card className="mb-4 overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">
            Partidas ({editando ? borrador.length : d.lineas.length})
            {!editando && d.lineas.some((l) => l.precioCongelado) && " · precios congelados al aprobar"}
          </div>
          {editando && (
            <div className="mt-3 max-w-xl">
              <BuscadorSku
                productos={catalogo}
                excluir={excluir}
                onSeleccionar={(p) =>
                  setBorrador((prev) => [
                    ...(prev ?? []),
                    { ...p, meses: ["0", "0", "0", "0", "0", "0"] },
                  ])
                }
              />
            </div>
          )}
        </CardBody>
        {(editando ? borrador.length : d.lineas.length) === 0 ? (
          <p className="px-5 pb-5 text-sm text-slate-500">Todavía no hay partidas.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-y border-slate-200 bg-slate-50/70 text-left text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className="px-4 py-2.5">SKU</th>
                  {meses.map((m) => (
                    <th key={m} className="px-2 py-2.5 text-right">
                      {m}
                    </th>
                  ))}
                  <th className="px-3 py-2.5 text-right">Total</th>
                  <th className="px-3 py-2.5 text-right">Precio</th>
                  <th className="px-4 py-2.5 text-right">Importe</th>
                  {editando && <th className="px-2 py-2.5" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {editando
                  ? borrador.map((b, fila) => {
                      const total = b.meses.reduce((s, q) => s + (Number(q) || 0), 0);
                      return (
                        <tr key={b.productId}>
                          <td className="max-w-64 px-4 py-2">
                            <div className="font-mono text-xs text-slate-600">{b.sku}</div>
                            <div className="truncate text-xs text-slate-500" title={b.nombre}>{b.nombre}</div>
                          </td>
                          {b.meses.map((q, m) => (
                            <td key={m} className="px-1 py-2">
                              <input
                                type="number"
                                min={0}
                                step="any"
                                inputMode="decimal"
                                aria-label={`${b.sku} ${meses[m]}`}
                                value={q}
                                onChange={(e) =>
                                  setBorrador((prev) =>
                                    prev!.map((x, i) =>
                                      i === fila ? { ...x, meses: x.meses.map((y, j) => (j === m ? e.target.value : y)) } : x,
                                    ),
                                  )
                                }
                                className={`${inputClass} w-20 px-2 text-right tabular-nums`}
                              />
                            </td>
                          ))}
                          <td className="px-3 py-2 text-right tabular-nums text-slate-700">
                            {cantidad.format(total)} <span className="text-xs text-slate-400">{b.unidad}</span>
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-slate-600">
                            {b.precio == null ? <span className="text-xs text-amber-600">Sin precio</span> : soles.format(b.precio)}
                          </td>
                          <td className="px-4 py-2 text-right tabular-nums text-slate-700">
                            {b.precio == null ? "—" : soles.format(b.precio * total)}
                          </td>
                          <td className="px-2 py-2">
                            <button
                              type="button"
                              aria-label={`Quitar ${b.sku}`}
                              onClick={() => setBorrador((prev) => prev!.filter((_, i) => i !== fila))}
                              className="text-slate-400 hover:text-rose-600"
                            >
                              <X className="size-4" />
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  : d.lineas.map((l) => (
                      <tr key={l.productId}>
                        <td className="max-w-64 px-4 py-2.5">
                          <div className="font-mono text-xs text-slate-600">{l.sku}</div>
                          <div className="truncate text-xs text-slate-500" title={l.nombre}>{l.nombre}</div>
                        </td>
                        {l.meses.map((q, m) => (
                          <td key={m} className="px-2 py-2.5 text-right tabular-nums text-slate-600">
                            {Number(q) === 0 ? <span className="text-slate-300">0</span> : cantidad.format(q)}
                          </td>
                        ))}
                        <td className="px-3 py-2.5 text-right tabular-nums text-slate-700">
                          {cantidad.format(l.cantidad)} <span className="text-xs text-slate-400">{l.unidad}</span>
                        </td>
                        <td className="px-3 py-2.5 text-right tabular-nums text-slate-600">
                          {l.precio == null ? <span className="text-xs text-amber-600">Sin precio</span> : soles.format(l.precio)}
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">
                          {l.importe == null ? "—" : soles.format(l.importe)}
                        </td>
                      </tr>
                    ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      )}

      {d.historial.length > 0 && (
        <details className="group rounded-xl border border-slate-200 bg-white">
          <summary className="cursor-pointer px-5 py-3 text-sm font-medium text-slate-700">
            Historial ({d.historial.length})
          </summary>
          <ul className="flex flex-col gap-2 px-5 pb-4">
            {d.historial.map((h, i) => (
              <li key={i} className="text-sm text-slate-600">
                <span className="font-medium text-slate-800">{ACCION[h.accion] ?? h.accion}</span> · {h.usuario ?? "—"} ·{" "}
                <span className="text-xs text-slate-400">{fmtFechaHora(h.fecha)}</span>
                {h.motivo && <span className="block text-xs text-slate-500">{h.motivo}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
