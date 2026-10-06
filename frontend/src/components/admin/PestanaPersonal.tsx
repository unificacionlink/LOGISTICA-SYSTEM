import { useRef, useState } from "react";
import { CheckCircle2, FileSpreadsheet, Upload, XCircle } from "lucide-react";
import { confirmarPersonal, descargarPlantillaPersonal, validarPersonal } from "../../api/admin";
import type { PersonalCarga } from "../../api/types";
import BotonDescarga from "../BotonDescarga";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";

const ACCION: Record<string, string> = {
  NUEVO: "Nuevo",
  ACTUALIZADO: "Actualizado",
  DESACTIVADO: "Cese",
  SIN_CAMBIOS: "Sin cambios",
};

const thClass = "px-4 py-2.5";

/**
 * Padron de personal de las empresas sin RR.HH. (GOYDEL/METRIN, DEC-L028):
 * la plantilla trae al personal actual; se edita, se valida sin escribir y
 * se confirma todo o nada.
 */
export default function PestanaPersonal({ empresas, recargar }: { empresas: string[]; recargar: () => void }) {
  const confirmar = useConfirmar();
  const inputRef = useRef<HTMLInputElement>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [resultado, setResultado] = useState<PersonalCarga | null>(null);
  const [procesando, setProcesando] = useState<"validar" | "confirmar" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);

  function onElegir(file: File | null) {
    setArchivo(file);
    setResultado(null);
    setError(null);
    setExito(null);
  }

  async function onValidar() {
    if (!archivo) return;
    setProcesando("validar");
    setError(null);
    setExito(null);
    try {
      const res = await validarPersonal(archivo);
      if (res.ok && res.result) setResultado(res.result);
      else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  async function onConfirmar() {
    if (!archivo || !resultado) return;
    const r = await confirmar({
      titulo: "¿Aplicar los cambios de personal?",
      mensaje: `${resultado.nuevos} nuevos, ${resultado.actualizados} actualizados y ${resultado.desactivados} ceses.`,
      confirmar: "Aplicar",
    });
    if (!r) return;
    setProcesando("confirmar");
    setError(null);
    try {
      const res = await confirmarPersonal(archivo);
      if (res.ok && res.result) {
        setResultado(res.result);
        setExito(res.message);
        setArchivo(null);
        if (inputRef.current) inputRef.current.value = "";
        recargar();
      } else {
        setError(res.message);
      }
    } finally {
      setProcesando(null);
    }
  }

  const cambios = resultado ? resultado.nuevos + resultado.actualizados + resultado.desactivados : 0;
  const puedeConfirmar = !!archivo && !!resultado && !resultado.aplicada && resultado.conError === 0 && cambios > 0;

  return (
    <>
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-3">
          <BotonDescarga descargar={descargarPlantillaPersonal} etiqueta="Plantilla" tipo="excel" />
          <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 rounded-md border border-dashed border-slate-300 px-4 py-3 text-sm text-slate-600 transition-colors hover:border-slate-400 hover:bg-slate-50">
            <FileSpreadsheet className="size-5 shrink-0 text-slate-400" />
            <span className="truncate">{archivo ? archivo.name : `Excel de personal ${empresas.join(" / ")} (.xlsx)`}</span>
            <input
              ref={inputRef}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="sr-only"
              onChange={(e) => onElegir(e.target.files?.[0] ?? null)}
            />
          </label>
          <Button
            variant="secondary"
            onClick={onValidar}
            disabled={!archivo || procesando !== null}
            loading={procesando === "validar"}
            icon={<Upload className="size-4" />}
          >
            Validar
          </Button>
          <Button onClick={onConfirmar} disabled={!puedeConfirmar || procesando !== null} loading={procesando === "confirmar"}>
            Aplicar cambios
          </Button>
        </CardBody>
      </Card>

      {resultado && (
        <Card className="overflow-hidden">
          <CardBody className="flex flex-wrap gap-x-6 gap-y-1 pb-3 text-sm text-slate-600">
            <span>
              <b className="text-emerald-700">{resultado.nuevos}</b> nuevos
            </span>
            <span>
              <b className="text-slate-700">{resultado.actualizados}</b> actualizados
            </span>
            <span>
              <b className="text-slate-700">{resultado.desactivados}</b> ceses
            </span>
            <span>
              <b className="text-slate-700">{resultado.sinCambios}</b> sin cambios
            </span>
            <span>
              <b className={resultado.conError > 0 ? "text-rose-700" : "text-slate-700"}>{resultado.conError}</b> con error
            </span>
          </CardBody>
          <div className="overflow-x-auto border-t border-slate-200">
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                  <th className={thClass}>Fila</th>
                  <th className={thClass}>Persona</th>
                  <th className={`${thClass} col-sec`}>Empresa</th>
                  <th className={thClass}>Resultado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {resultado.filas
                  .filter((f) => f.accion !== "SIN_CAMBIOS")
                  .map((f) => (
                    <tr key={f.fila} className={f.error ? "bg-rose-50/40" : "hover:bg-slate-50"}>
                      <td className="px-4 py-2.5 text-xs tabular-nums text-slate-500">{f.fila}</td>
                      <td className="col-flex px-4 py-2.5">
                        <p className="text-sm text-slate-800">{f.nombre || "—"}</p>
                        <p className="truncate text-xs text-slate-500">
                          <span className="font-mono">{f.dni || "—"}</span> · {f.correo || "—"}
                        </p>
                      </td>
                      <td className="col-sec px-4 py-2.5 font-mono text-xs text-slate-600">{f.empresa || "—"}</td>
                      <td className="px-4 py-2.5 text-xs">
                        {f.error ? (
                          <span className="inline-flex items-start gap-1.5 text-rose-700">
                            <XCircle className="mt-px size-3.5 shrink-0" />
                            {f.error}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-emerald-700">
                            <CheckCircle2 className="size-3.5" />
                            {ACCION[f.accion ?? ""]}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
}
