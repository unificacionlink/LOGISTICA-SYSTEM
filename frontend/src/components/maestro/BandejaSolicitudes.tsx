import { useState } from "react";
import { AlertTriangle, PackagePlus } from "lucide-react";
import { aprobarSolicitudProducto, rechazarSolicitudProducto } from "../../api/maestro";
import type { Clasificacion, SolicitudProducto } from "../../api/types";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import Field, { inputClass } from "../ui/Field";
import { ETIQUETA_ATRIBUTO, SelectorFamilia, SelectorUnidad } from "./SelectoresMaestro";

export const PENDIENTE = "PENDIENTE_APROBACION_LOGISTICA";

export function EstadoSolicitud({ estado }: { estado: SolicitudProducto["estado"] }) {
  if (estado === PENDIENTE) return <Badge tone="amarillo">Pendiente</Badge>;
  if (estado === "APROBADO") return <Badge tone="verde">Aprobada</Badge>;
  if (estado === "RECHAZADO") return <Badge tone="rojo">Rechazada</Badge>;
  return <Badge tone="gris">{estado}</Badge>;
}

function fmtFecha(iso: string): string {
  return new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "short" });
}

/** Bandeja de Logistica: aprobar (crea el SKU, con nombre/familia/unidad corregibles) o rechazar con motivo. */
export default function BandejaSolicitudes({
  solicitudes,
  clasificacion,
  onActualizada,
}: {
  solicitudes: SolicitudProducto[];
  clasificacion: Clasificacion;
  onActualizada: (s: SolicitudProducto, mensaje: string) => void;
}) {
  const pendientes = solicitudes.filter((s) => s.estado === PENDIENTE);
  const revisadas = solicitudes.filter((s) => s.estado !== PENDIENTE);

  return (
    <div className="flex flex-col gap-4">
      {pendientes.length === 0 ? (
        <Card>
          <EmptyState icon={PackagePlus} title="No hay solicitudes pendientes" />
        </Card>
      ) : (
        pendientes.map((s) => (
          <Pendiente key={s.id} s={s} clasificacion={clasificacion} onActualizada={onActualizada} />
        ))
      )}
      {revisadas.length > 0 && (
        <details className="group">
          <summary className="cursor-pointer list-none text-xs font-medium text-slate-500 hover:text-slate-800">
            Revisadas ({revisadas.length})
          </summary>
          <Card className="mt-2 overflow-hidden">
            <ul className="divide-y divide-slate-100">
              {revisadas.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center gap-3 px-4 py-2.5 text-sm">
                  <span className="font-mono text-xs text-slate-500">{s.codigo}</span>
                  <span className="min-w-0 flex-1 truncate text-slate-700">{s.nombre}</span>
                  {s.skuGenerado && <span className="font-mono text-xs text-slate-700">{s.skuGenerado}</span>}
                  {s.comentarioRevision && <span className="text-xs text-slate-500">{s.comentarioRevision}</span>}
                  <EstadoSolicitud estado={s.estado} />
                </li>
              ))}
            </ul>
          </Card>
        </details>
      )}
    </div>
  );
}

function Pendiente({
  s,
  clasificacion,
  onActualizada,
}: {
  s: SolicitudProducto;
  clasificacion: Clasificacion;
  onActualizada: (s: SolicitudProducto, mensaje: string) => void;
}) {
  const [modo, setModo] = useState<"ver" | "aprobar" | "rechazar">("ver");
  const [nombre, setNombre] = useState(s.nombre);
  const [familyId, setFamilyId] = useState(s.familyId);
  const [uomId, setUomId] = useState(s.uomId);
  const [motivo, setMotivo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function ejecutar(accion: () => ReturnType<typeof rechazarSolicitudProducto>) {
    setGuardando(true);
    setError(null);
    try {
      const res = await accion();
      if (res.ok && res.result) onActualizada(res.result, res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-slate-900">{s.nombre}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            <span className="font-mono">{s.codigo}</span> · {s.solicitante} ({s.empresaCode}) · {fmtFecha(s.solicitadaEn)}
          </p>
        </div>
        {modo === "ver" && (
          <div className="flex gap-2">
            <Button size="sm" onClick={() => setModo("aprobar")}>
              Aprobar
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setModo("rechazar")}>
              Rechazar
            </Button>
          </div>
        )}
      </div>

      <dl className="mt-3 grid gap-x-4 gap-y-1.5 text-xs sm:grid-cols-4">
        <div>
          <dt className="rotulo">Familia › Subfamilia</dt>
          <dd className="text-slate-700">{s.familia}</dd>
        </div>
        <div>
          <dt className="rotulo">Unidad</dt>
          <dd className="text-slate-700">{s.unidad}</dd>
        </div>
        <div>
          <dt className="rotulo">Atributo</dt>
          <dd className="text-slate-700">
            {s.atributoTipo ? `${ETIQUETA_ATRIBUTO[s.atributoTipo]} ${s.atributoValor}` : "—"}
          </dd>
        </div>
        <div className="sm:col-span-4">
          <dt className="rotulo">Para qué se necesita</dt>
          <dd className="text-slate-700">{s.justificacion}</dd>
        </div>
        {s.descripcionTecnica && (
          <div className="sm:col-span-4">
            <dt className="rotulo">Descripción técnica</dt>
            <dd className="text-slate-700">{s.descripcionTecnica}</dd>
          </div>
        )}
      </dl>

      {s.posibleDuplicado && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-amber-800">
          <AlertTriangle className="size-3.5" />
          Posible duplicado: {s.posibleDuplicado}
        </p>
      )}

      {modo === "aprobar" && (
        <div className="mt-4 grid gap-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3 sm:grid-cols-[2fr_1fr_1fr]">
          <Field label="Nombre del producto" required>
            <input value={nombre} onChange={(e) => setNombre(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <Field label="Familia › Subfamilia" required>
            <SelectorFamilia clasificacion={clasificacion} value={familyId} onChange={setFamilyId} />
          </Field>
          <Field label="Unidad" required>
            <SelectorUnidad clasificacion={clasificacion} value={uomId} onChange={setUomId} />
          </Field>
          <div className="flex gap-2 sm:col-span-3">
            <Button
              size="sm"
              loading={guardando}
              onClick={() => ejecutar(() => aprobarSolicitudProducto(s.id, familyId, nombre, uomId))}
            >
              Crear SKU
            </Button>
            <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setModo("ver")}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      {modo === "rechazar" && (
        <div className="mt-4 flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50/60 p-3">
          <Field label="Motivo del rechazo" required>
            <input autoFocus value={motivo} onChange={(e) => setMotivo(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="danger"
              loading={guardando}
              disabled={!motivo.trim()}
              onClick={() => ejecutar(() => rechazarSolicitudProducto(s.id, motivo))}
            >
              Confirmar rechazo
            </Button>
            <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setModo("ver")}>
              Cancelar
            </Button>
          </div>
        </div>
      )}

      {error && <Alert tone="danger" className="mt-3">{error}</Alert>}
    </Card>
  );
}
