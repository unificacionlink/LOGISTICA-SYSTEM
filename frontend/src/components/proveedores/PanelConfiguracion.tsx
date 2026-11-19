import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import {
  guardarPlantillaEvaluacion,
  guardarRequisitosDocumento,
  obtenerConfiguracionProveedores,
} from "../../api/proveedores";
import type { ConfiguracionProveedores, PlantillaEvaluacion, TipoEvaluacion } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import CampoNumero from "../ui/CampoNumero";
import Field, { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";
import Pestanas from "../ui/Pestanas";
import { Skeleton } from "../ui/Skeleton";
import { fmtNota } from "./estados";

type Vista = TipoEvaluacion | "DOCUMENTOS";

interface CriterioEditable {
  criterionId: string | null;
  name: string;
  weight: string;
  /** Una opcion por linea: "10 = Si cumple". Vacio = puntaje libre. */
  opciones: string;
}

function editable(t: PlantillaEvaluacion) {
  return {
    name: t.name,
    code: t.code,
    scaleMax: String(t.scaleMax),
    approvalThreshold: String(t.approvalThreshold),
    validityMonths: String(t.validityMonths),
    alertDays: String(t.alertDays),
    criterios: t.criterios.map<CriterioEditable>((c) => ({
      criterionId: c.criterionId,
      name: c.name,
      weight: String(c.weight),
      opciones: c.opciones.map((o) => `${fmtNota(o.score)} = ${o.label}`).join("\n"),
    })),
  };
}

function leerOpciones(texto: string): { score: number; label: string }[] | string {
  const lineas = texto.split("\n").map((l) => l.trim()).filter(Boolean);
  const opciones: { score: number; label: string }[] = [];
  for (const l of lineas) {
    const m = l.match(/^(\d+(?:[.,]\d+)?)\s*=\s*(.+)$/);
    if (!m) return `Escribe cada respuesta como «puntaje = texto» (${l}).`;
    opciones.push({ score: Number(m[1].replace(",", ".")), label: m[2].trim() });
  }
  return opciones;
}

/**
 * Configuracion del modulo (DEC-L070/L071): plantillas F-LOG de seleccion y
 * de evaluacion (criterios, pesos, nota aprobatoria, vigencia) y requisitos
 * de documentos. Guardar una plantilla sube su version; lo ya evaluado no cambia.
 */
export default function PanelConfiguracion({ onCerrar }: { onCerrar: () => void }) {
  const [config, setConfig] = useState<ConfiguracionProveedores | null>(null);
  const [vista, setVista] = useState<Vista>("SELECCION");
  const [form, setForm] = useState<ReturnType<typeof editable> | null>(null);
  const [requisitos, setRequisitos] = useState<ConfiguracionProveedores["requisitos"]>([]);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    obtenerConfiguracionProveedores().then((res) => {
      if (res.ok && res.result) {
        setConfig(res.result);
        setRequisitos(res.result.requisitos);
        const t = res.result.plantillas.find((p) => p.kind === "SELECCION");
        setForm(t ? editable(t) : null);
      } else setError(res.message);
    });
  }, []);

  function cambiarVista(v: Vista) {
    setVista(v);
    const t = config?.plantillas.find((p) => p.kind === v);
    setForm(t ? editable(t) : null);
    setError(null);
    setAviso(null);
  }

  const suma = form?.criterios.reduce((s, c) => s + (Number(c.weight) || 0), 0) ?? 0;

  function cambiarCriterio(i: number, cambios: Partial<CriterioEditable>) {
    setForm((f) => f && { ...f, criterios: f.criterios.map((c, j) => (j === i ? { ...c, ...cambios } : c)) });
  }

  async function guardarPlantilla() {
    if (!form || vista === "DOCUMENTOS") return;
    const criterios = [];
    for (const c of form.criterios) {
      const opciones = leerOpciones(c.opciones);
      if (typeof opciones === "string") {
        setError(opciones);
        return;
      }
      criterios.push({ criterionId: c.criterionId, name: c.name, weight: Number(c.weight), opciones });
    }
    setGuardando(true);
    setError(null);
    try {
      const res = await guardarPlantillaEvaluacion(vista, {
        name: form.name,
        code: form.code,
        scaleMax: Number(form.scaleMax),
        approvalThreshold: Number(form.approvalThreshold),
        validityMonths: Number(form.validityMonths),
        alertDays: Number(form.alertDays),
        criterios,
      });
      if (res.ok && res.result && config) {
        const nueva = res.result;
        setConfig({ ...config, plantillas: config.plantillas.map((p) => (p.kind === vista ? nueva : p)) });
        setForm(editable(nueva));
        setAviso(`Guardado como versión ${nueva.version}.`);
      } else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  async function guardarRequisitos() {
    setGuardando(true);
    setError(null);
    try {
      const res = await guardarRequisitosDocumento(
        requisitos.map((r) => ({ ...r, requirementId: r.requirementId || null })),
      );
      if (res.ok && res.result) {
        setRequisitos(res.result);
        setAviso("Requisitos guardados.");
      } else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo="Configuración de proveedores"
      ancho="max-w-2xl"
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cerrar
          </Button>
          <Button
            loading={guardando}
            onClick={vista === "DOCUMENTOS" ? guardarRequisitos : guardarPlantilla}
            disabled={!config || (vista !== "DOCUMENTOS" && Math.abs(suma - 100) > 0.001)}
          >
            Guardar
          </Button>
        </>
      }
    >
      <div className="px-5 pt-4">
        <Pestanas<Vista>
          pestanas={[
            { id: "SELECCION", label: "Selección" },
            { id: "DESEMPENO", label: "Evaluación" },
            { id: "DOCUMENTOS", label: "Documentos" },
          ]}
          activa={vista}
          onCambiar={cambiarVista}
        />
        {error && <Alert tone="danger" className="mb-3">{error}</Alert>}
        {aviso && <Alert tone="good" className="mb-3">{aviso}</Alert>}
      </div>

      {!config ? (
        <div className="space-y-3 px-5 py-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-10" />
          ))}
        </div>
      ) : vista === "DOCUMENTOS" ? (
        <SeccionPanel
          titulo="Requisitos de documentos"
          accion={
            <Button
              size="sm"
              variant="ghost"
              icon={<Plus className="size-4" />}
              onClick={() =>
                setRequisitos((r) => [
                  ...r,
                  { requirementId: "", name: "", description: null, required: false, hasExpiry: false, sortOrder: r.length + 1, active: true },
                ])
              }
            >
              Agregar
            </Button>
          }
        >
          <ul className="divide-y divide-slate-100">
            {requisitos.map((r, i) => (
              <li key={r.requirementId || `nuevo-${i}`} className={`flex flex-col gap-2 py-3 ${r.active ? "" : "opacity-60"}`}>
                <input
                  className={`${inputClass} w-full`}
                  value={r.name}
                  maxLength={150}
                  placeholder="Nombre del documento"
                  onChange={(e) => setRequisitos((l) => l.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))}
                />
                <div className="flex flex-wrap gap-4 text-sm text-slate-700">
                  {(
                    [
                      ["required", "Obligatorio"],
                      ["hasExpiry", "Tiene vencimiento"],
                      ["active", "Activo"],
                    ] as const
                  ).map(([campo, label]) => (
                    <label key={campo} className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={r[campo]}
                        onChange={(e) =>
                          setRequisitos((l) => l.map((x, j) => (j === i ? { ...x, [campo]: e.target.checked } : x)))
                        }
                      />
                      {label}
                    </label>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </SeccionPanel>
      ) : (
        form && (
          <>
            <SeccionPanel titulo="Formato">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                <Field label="Código">
                  <input className={`${inputClass} w-full`} value={form.code} maxLength={30} onChange={(e) => setForm({ ...form, code: e.target.value })} />
                </Field>
                <Field label="Nombre" className="col-span-2">
                  <input className={`${inputClass} w-full`} value={form.name} maxLength={150} onChange={(e) => setForm({ ...form, name: e.target.value })} />
                </Field>
                <Field label="Escala máxima">
                  <CampoNumero className={`${inputClass} w-full`} decimales={2} maxEnteros={3} value={form.scaleMax} onChange={(v) => setForm({ ...form, scaleMax: v })} />
                </Field>
                <Field label="Nota aprobatoria">
                  <CampoNumero className={`${inputClass} w-full`} decimales={2} maxEnteros={3} value={form.approvalThreshold} onChange={(v) => setForm({ ...form, approvalThreshold: v })} />
                </Field>
                <Field label="Vigencia (meses)">
                  <CampoNumero className={`${inputClass} w-full`} maxEnteros={2} max={60} value={form.validityMonths} onChange={(v) => setForm({ ...form, validityMonths: v })} />
                </Field>
                <Field label="Aviso antes de vencer (días)">
                  <CampoNumero className={`${inputClass} w-full`} maxEnteros={3} max={365} value={form.alertDays} onChange={(v) => setForm({ ...form, alertDays: v })} />
                </Field>
              </div>
            </SeccionPanel>
            <SeccionPanel
              titulo="Criterios"
              accion={
                <span className={`text-xs font-semibold tabular-nums ${Math.abs(suma - 100) > 0.001 ? "text-rose-700" : "text-emerald-700"}`}>
                  Suma {fmtNota(suma)}%
                </span>
              }
            >
              <ul className="space-y-3">
                {form.criterios.map((c, i) => (
                  <li key={c.criterionId ?? `nuevo-${i}`} className="rounded-lg border border-slate-200 p-3">
                    <div className="flex items-start gap-2">
                      <input
                        className={`${inputClass} min-w-0 flex-1`}
                        value={c.name}
                        maxLength={250}
                        placeholder="Criterio"
                        onChange={(e) => cambiarCriterio(i, { name: e.target.value })}
                      />
                      <div className="flex w-24 items-center gap-1">
                        <CampoNumero
                          className={`${inputClass} w-full text-right`}
                          decimales={2}
                          maxEnteros={3}
                          max={100}
                          value={c.weight}
                          onChange={(v) => cambiarCriterio(i, { weight: v })}
                          aria-label="Peso"
                        />
                        <span className="text-sm text-slate-500">%</span>
                      </div>
                      <button
                        type="button"
                        aria-label="Quitar criterio"
                        onClick={() => setForm({ ...form, criterios: form.criterios.filter((_, j) => j !== i) })}
                        className="rounded-md p-2 text-slate-400 hover:bg-rose-50 hover:text-rose-700"
                      >
                        <Trash2 className="size-4" />
                      </button>
                    </div>
                    <textarea
                      className={`${inputClass} mt-2 w-full whitespace-normal font-mono text-xs`}
                      rows={Math.max(2, c.opciones.split("\n").length)}
                      value={c.opciones}
                      placeholder={"10 = Sí cumple\n0 = No cumple"}
                      onChange={(e) => cambiarCriterio(i, { opciones: e.target.value })}
                      aria-label="Respuestas y puntaje"
                    />
                  </li>
                ))}
              </ul>
              <Button
                size="sm"
                variant="ghost"
                className="mt-3"
                icon={<Plus className="size-4" />}
                onClick={() =>
                  setForm({ ...form, criterios: [...form.criterios, { criterionId: null, name: "", weight: "", opciones: "" }] })
                }
              >
                Agregar criterio
              </Button>
            </SeccionPanel>
          </>
        )
      )}
    </PanelLateral>
  );
}
