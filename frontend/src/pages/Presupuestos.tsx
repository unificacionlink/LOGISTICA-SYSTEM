import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { CalendarRange, PiggyBank } from "lucide-react";
import {
  cerrarPeriodo,
  controlPeriodo,
  crearPeriodo,
  crearPresupuesto,
  listarPeriodos,
  listarPresupuestos,
} from "../api/presupuestos";
import type { EstadoPresupuesto, PeriodosPresupuestales, PresupuestoResumen } from "../api/types";
import PresupuestoEstadoBadge from "../components/PresupuestoEstadoBadge";
import Dialogo from "../components/presupuestos/Dialogo";
import { ETIQUETA_PRESUPUESTO, etiquetaPeriodo, soles } from "../components/presupuestos/formato";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import EmptyState from "../components/ui/EmptyState";
import Field, { inputClass } from "../components/ui/Field";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";

const ESTADOS = Object.keys(ETIQUETA_PRESUPUESTO) as EstadoPresupuesto[];

export default function Presupuestos() {
  const navigate = useNavigate();
  const confirmar = useConfirmar();
  const [presupuestos, setPresupuestos] = useState<PresupuestoResumen[]>([]);
  const [periodos, setPeriodos] = useState<PeriodosPresupuestales | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const [anio, setAnio] = useState("");
  const [estado, setEstado] = useState<EstadoPresupuesto | "">("");
  const [verPeriodos, setVerPeriodos] = useState(false);
  const [dialogo, setDialogo] = useState<"presupuesto" | "periodo" | null>(null);

  function cargarPeriodos() {
    listarPeriodos().then((res) => {
      if (res.ok && res.result) setPeriodos(res.result);
      else setError(res.message);
    });
  }

  useEffect(cargarPeriodos, []);

  useEffect(() => {
    listarPresupuestos({ anio: anio ? Number(anio) : undefined, estado: estado || undefined })
      .then((res) => {
        if (res.ok) setPresupuestos(res.result ?? []);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, [anio, estado]);

  const esPyc = (periodos?.cecosConfigurables.length ?? 0) > 0;
  const disponibles = useMemo(
    () => periodos?.periodos.filter((p) => !p.cerrado && !p.presupuestoId) ?? [],
    [periodos],
  );
  const anios = useMemo(
    () => [...new Set(periodos?.periodos.map((p) => p.anio) ?? [])].sort((a, b) => b - a),
    [periodos],
  );

  async function onCerrarPeriodo(periodId: string, cerrado: boolean, etiqueta: string) {
    const ok = await confirmar({
      titulo: cerrado ? `¿Cerrar el periodo ${etiqueta}?` : `¿Reabrir el periodo ${etiqueta}?`,
      mensaje: cerrado ? "Su presupuesto quedará solo de consulta." : undefined,
      confirmar: cerrado ? "Cerrar periodo" : "Reabrir",
      peligro: cerrado,
    });
    if (!ok) return;
    const res = await cerrarPeriodo(periodId, cerrado);
    if (!res.ok) {
      setError(res.message);
      return;
    }
    setExito(res.message);
    cargarPeriodos();
  }

  async function onControl(periodId: string, obligatorio: boolean, etiqueta: string) {
    const ok = await confirmar({
      titulo: obligatorio ? `¿Activar el control presupuestal en ${etiqueta}?` : `¿Desactivar el control en ${etiqueta}?`,
      mensaje: obligatorio
        ? "Los requerimientos de este CECO y semestre se validarán contra el presupuesto aprobado al enviarse."
        : undefined,
      confirmar: obligatorio ? "Activar" : "Desactivar",
    });
    if (!ok) return;
    const res = await controlPeriodo(periodId, obligatorio);
    if (!res.ok) {
      setError(res.message);
      return;
    }
    setExito(res.message);
    cargarPeriodos();
  }

  return (
    <div>
      <PageHeader
        title="Presupuestos"
        actions={
          <>
            {esPyc && (
              <MenuAcciones
                acciones={[
                  { label: verPeriodos ? "Ocultar periodos" : "Periodos presupuestales", onClick: () => setVerPeriodos((v) => !v) },
                  { label: "Habilitar periodo", onClick: () => setDialogo("periodo") },
                ]}
              />
            )}
            {periodos?.puedeCrear && (
              <Button onClick={() => setDialogo("presupuesto")} disabled={disponibles.length === 0}
                title={disponibles.length === 0 ? "No hay periodos habilitados sin presupuesto" : undefined}>
                Nuevo presupuesto
              </Button>
            )}
          </>
        }
      />

      <div className="mb-4 flex flex-col gap-3">
        {error && <Alert tone="danger">{error}</Alert>}
        {exito && <Alert tone="good">{exito}</Alert>}
      </div>

      {verPeriodos && periodos && (
        <Card className="mb-4 overflow-hidden">
          <CardBody className="pb-3">
            <div className="rotulo">Periodos presupuestales</div>
          </CardBody>
          {periodos.periodos.length === 0 ? (
            <EmptyState icon={CalendarRange} title="Todavía no hay periodos habilitados" />
          ) : (
            <table className="tabla">
              <thead>
                <tr className="border-y border-slate-200 cabecera-tabla">
                  <th className="px-5 py-2.5">CECO</th>
                  <th className="px-5 py-2.5">Periodo</th>
                  <th className="px-5 py-2.5">Presupuesto</th>
                  <th className="px-5 py-2.5">Estado</th>
                  <th className="px-5 py-2.5">Control</th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {periodos.periodos.map((p) => (
                  <tr key={p.periodId}>
                    <td className="col-flex px-5 py-2.5 text-slate-700" title={p.cecoNombre}>
                      <span className="font-mono text-xs text-slate-500">{p.empresa} · {p.cecoCode}</span> {p.cecoNombre}
                    </td>
                    <td className="px-5 py-2.5 font-mono text-xs text-slate-700">{etiquetaPeriodo(p.anio, p.semestre)}</td>
                    <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{p.presupuestoCodigo ?? "—"}</td>
                    <td className="px-5 py-2.5">
                      <Badge tone={p.cerrado ? "gris" : "verde"}>{p.cerrado ? "Cerrado" : "Habilitado"}</Badge>
                    </td>
                    <td className="px-5 py-2.5">
                      <Badge tone={p.controlObligatorio ? "azul" : "gris"}>
                        {p.controlObligatorio ? "Obligatorio" : "Desactivado"}
                      </Badge>
                    </td>
                    <td className="px-5 py-2.5">
                      {periodos.cecosConfigurables.some((c) => c.cecoId === p.cecoId) && (
                        <MenuAcciones
                          etiqueta={`Acciones del periodo ${etiquetaPeriodo(p.anio, p.semestre)}`}
                          acciones={[
                            {
                              label: p.controlObligatorio ? "Desactivar control" : "Activar control presupuestal",
                              onClick: () => onControl(p.periodId, !p.controlObligatorio, etiquetaPeriodo(p.anio, p.semestre)),
                            },
                            {
                              label: p.cerrado ? "Reabrir periodo" : "Cerrar periodo",
                              peligro: !p.cerrado,
                              onClick: () => onCerrarPeriodo(p.periodId, !p.cerrado, etiquetaPeriodo(p.anio, p.semestre)),
                            },
                          ]}
                        />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}

      <div className="mb-4 flex flex-wrap gap-3">
        {periodos === null ? (
          <Skeleton className="h-9 w-64" />
        ) : (
          <>
            <select
              value={anio}
              onChange={(e) => {
                setCargando(true);
                setAnio(e.target.value);
              }} aria-label="Año" className={`${inputClass} w-32`}>
              <option value="">Todos los años</option>
              {anios.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
            <select
              value={estado}
              onChange={(e) => {
                setCargando(true);
                setEstado(e.target.value as EstadoPresupuesto | "");
              }}
              aria-label="Estado"
              className={`${inputClass} w-44`}
            >
              <option value="">Todos los estados</option>
              {ESTADOS.map((e) => (
                <option key={e} value={e}>
                  {ETIQUETA_PRESUPUESTO[e]}
                </option>
              ))}
            </select>
          </>
        )}
      </div>

      <Card className="overflow-hidden">
        {cargando ? (
          <SkeletonTable cols={5} />
        ) : presupuestos.length === 0 ? (
          <EmptyState icon={PiggyBank} title="No hay presupuestos para esta selección" />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 cabecera-tabla">
                <th className="px-5 py-3">Código</th>
                <th className="px-5 py-3">CECO</th>
                <th className="px-5 py-3">Periodo</th>
                <th className="px-5 py-3 text-right">Total</th>
                <th className="px-5 py-3">Estado</th>
                <th className="col-sec px-5 py-3">Solicitante</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {presupuestos.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-5 py-3">
                    <Link to={`/presupuestos/${p.id}`} className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700">
                      {p.codigo}
                    </Link>
                  </td>
                  <td className="col-flex px-5 py-3 text-slate-700" title={p.cecoNombre}>
                    <span className="font-mono text-xs text-slate-500">{p.empresa} · {p.cecoCode}</span> {p.cecoNombre}
                  </td>
                  <td className="px-5 py-3 font-mono text-xs text-slate-700">{etiquetaPeriodo(p.anio, p.semestre)}</td>
                  <td className="px-5 py-3 text-right tabular-nums text-slate-700">
                    {p.total == null ? "—" : soles.format(p.total)}
                    {p.lineasSinPrecio > 0 && <span className="block text-xs text-amber-600">{p.lineasSinPrecio} sin precio</span>}
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <PresupuestoEstadoBadge estado={p.estado} />
                      {p.porDecidir && <Badge tone="azul">Por decidir</Badge>}
                    </div>
                  </td>
                  <td className="col-sec px-5 py-3 text-xs text-slate-500">{p.solicitante}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {dialogo === "presupuesto" && (
        <DialogoPresupuesto
          periodos={disponibles}
          onCerrar={() => setDialogo(null)}
          onCreado={(id, mensaje) => navigate(`/presupuestos/${id}?msg=${encodeURIComponent(mensaje)}`)}
        />
      )}
      {dialogo === "periodo" && periodos && (
        <DialogoPeriodo
          cecos={periodos.cecosConfigurables}
          onCerrar={() => setDialogo(null)}
          onCreado={(mensaje) => {
            setDialogo(null);
            setExito(mensaje);
            setVerPeriodos(true);
            cargarPeriodos();
          }}
        />
      )}
    </div>
  );
}

function DialogoPresupuesto({
  periodos,
  onCerrar,
  onCreado,
}: {
  periodos: PeriodosPresupuestales["periodos"];
  onCerrar: () => void;
  onCreado: (id: string, mensaje: string) => void;
}) {
  const [periodId, setPeriodId] = useState(periodos.length === 1 ? periodos[0].periodId : "");
  const [observaciones, setObservaciones] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function guardar() {
    if (!periodId) {
      setError("Selecciona el CECO y periodo.");
      return;
    }
    setGuardando(true);
    setError(null);
    const res = await crearPresupuesto(periodId, observaciones);
    setGuardando(false);
    if (res.ok && res.result) onCreado(res.result.id, res.message);
    else setError(res.message);
  }

  return (
    <Dialogo titulo="Nuevo presupuesto semestral" textoGuardar="Crear" guardando={guardando} error={error}
      onGuardar={guardar} onCerrar={onCerrar}>
      <Field label="CECO y periodo" required>
        <select autoFocus value={periodId} onChange={(e) => setPeriodId(e.target.value)} className={`${inputClass} w-full`}>
          <option value="">Selecciona…</option>
          {periodos.map((p) => (
            <option key={p.periodId} value={p.periodId}>
              {p.empresa} · {p.cecoCode} {p.cecoNombre} · {etiquetaPeriodo(p.anio, p.semestre)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Observaciones">
        <textarea value={observaciones} onChange={(e) => setObservaciones(e.target.value)} maxLength={500} rows={2}
          className={`${inputClass} w-full`} />
      </Field>
    </Dialogo>
  );
}

function DialogoPeriodo({
  cecos,
  onCerrar,
  onCreado,
}: {
  cecos: PeriodosPresupuestales["cecosConfigurables"];
  onCerrar: () => void;
  onCreado: (mensaje: string) => void;
}) {
  const hoy = new Date();
  const [cecoId, setCecoId] = useState(cecos.length === 1 ? cecos[0].cecoId : "");
  const [anio, setAnio] = useState(String(hoy.getFullYear()));
  const [semestre, setSemestre] = useState(hoy.getMonth() < 6 ? "1" : "2");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function guardar() {
    const a = Number(anio);
    if (!cecoId || !Number.isInteger(a) || a < 2020 || a > 2100) {
      setError("Selecciona el CECO e ingresa un año entre 2020 y 2100.");
      return;
    }
    setGuardando(true);
    setError(null);
    const res = await crearPeriodo(cecoId, a, Number(semestre));
    setGuardando(false);
    if (res.ok) onCreado(res.message);
    else setError(res.message);
  }

  return (
    <Dialogo titulo="Habilitar periodo presupuestal" textoGuardar="Habilitar" guardando={guardando} error={error}
      onGuardar={guardar} onCerrar={onCerrar}>
      <Field label="CECO" required>
        <select autoFocus value={cecoId} onChange={(e) => setCecoId(e.target.value)} className={`${inputClass} w-full`}>
          <option value="">Selecciona…</option>
          {cecos.map((c) => (
            <option key={c.cecoId} value={c.cecoId}>
              {c.empresa} · {c.code} {c.nombre}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Año" required>
          <input type="number" min={2020} max={2100} value={anio} onChange={(e) => setAnio(e.target.value)}
            className={`${inputClass} w-full tabular-nums`} />
        </Field>
        <Field label="Semestre" required>
          <select value={semestre} onChange={(e) => setSemestre(e.target.value)} className={`${inputClass} w-full`}>
            <option value="1">S1 · enero a junio</option>
            <option value="2">S2 · julio a diciembre</option>
          </select>
        </Field>
      </div>
    </Dialogo>
  );
}
