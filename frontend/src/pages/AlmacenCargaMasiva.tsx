import { useRef, useState } from "react";
import { CheckCircle2, FileSpreadsheet, Upload, XCircle } from "lucide-react";
import { confirmarCargaMasiva, descargarPlantillaIngresos, validarCargaMasiva } from "../api/almacen";
import type { CargaMasiva } from "../api/types";
import { fmtCantidad } from "../components/AlmacenBadges";
import BotonDescarga from "../components/BotonDescarga";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import PageHeader from "../components/ui/PageHeader";
import { useConfirmar } from "../components/ui/Confirmacion";

const thClass = "px-4 py-3";

/**
 * RF-031: carga masiva de ingresos. Se valida el Excel completo antes de
 * tocar stock; confirmar registra todo o nada (una recepcion por OC).
 */
export default function AlmacenCargaMasiva() {
  const confirmar = useConfirmar();
  const inputRef = useRef<HTMLInputElement>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [resultado, setResultado] = useState<CargaMasiva | null>(null);
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
      const res = await validarCargaMasiva(archivo);
      if (res.ok && res.result) setResultado(res.result);
      else {
        setResultado(null);
        setError(res.message);
      }
    } finally {
      setProcesando(null);
    }
  }

  async function onConfirmar() {
    if (!archivo || !resultado) return;
    const r = await confirmar({
      titulo: `¿Registrar ${resultado.validas} ingresos?`,
      mensaje: "Se actualizará el stock de todas las filas válidas.",
      confirmar: "Registrar",
    });
    if (!r) return;
    setProcesando("confirmar");
    setError(null);
    try {
      const res = await confirmarCargaMasiva(archivo);
      if (res.ok && res.result) {
        setResultado(res.result);
        setExito(res.message);
        setArchivo(null);
        if (inputRef.current) inputRef.current.value = "";
      } else {
        setError(res.message);
      }
    } finally {
      setProcesando(null);
    }
  }

  const puedeConfirmar = !!archivo && !!resultado && !resultado.aplicada && resultado.conError === 0 && resultado.validas > 0;

  return (
    <div>
      <PageHeader
        title="Carga masiva de ingresos"
        backTo="/almacen?tab=recibir"
        backLabel="Almacén"
        actions={<BotonDescarga descargar={descargarPlantillaIngresos} etiqueta="Plantilla" tipo="excel" />}
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-3">
          <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 rounded-md border border-dashed border-slate-300 px-4 py-3 text-sm text-slate-600 transition-colors hover:border-slate-400 hover:bg-slate-50">
            <FileSpreadsheet className="size-5 shrink-0 text-slate-400" />
            <span className="truncate">{archivo ? archivo.name : "Seleccionar Excel (.xlsx)"}</span>
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
            Confirmar ingreso
          </Button>
        </CardBody>
      </Card>

      {resultado && (
        <Card className="overflow-hidden">
          <CardBody className="flex flex-wrap gap-x-6 gap-y-1 pb-3 text-sm text-slate-600">
            <span>
              <b className="text-emerald-700">{resultado.validas}</b> {resultado.aplicada ? "registradas" : "válidas"}
            </span>
            <span>
              <b className={resultado.conError > 0 ? "text-rose-700" : "text-slate-700"}>{resultado.conError}</b> con error
            </span>
            <span>
              <b className="text-slate-700">{resultado.ordenes}</b> órdenes
            </span>
            <span>
              <b className="text-slate-700">{resultado.omitidas}</b> sin cantidad
            </span>
          </CardBody>
          {resultado.filas.length > 0 && (
            <div className="overflow-x-auto border-t border-slate-200">
              <table className="tabla">
                <thead>
                  <tr className="border-b border-slate-200 cabecera-tabla">
                    <th className={thClass}>Fila</th>
                    <th className={thClass}>Orden</th>
                    <th className={thClass}>Producto</th>
                    <th className={`${thClass} text-right`}>Cantidad</th>
                    <th className={`${thClass} text-right`}>Pendiente</th>
                    <th className={thClass}>Estado</th>
                    <th className={thClass}>Guía</th>
                    <th className={thClass}>Resultado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {resultado.filas.map((f) => (
                    <tr key={f.fila} className={f.error ? "bg-rose-50/40" : "hover:bg-slate-50"}>
                      <td className="px-4 py-2.5 text-xs tabular-nums text-slate-500">{f.fila}</td>
                      <td className="px-4 py-2.5 font-mono text-xs text-slate-700">{f.orden || "—"}</td>
                      <td className="col-flex px-4 py-2.5">
                        <p className="text-sm text-slate-800">{f.producto || "—"}</p>
                        <p className="font-mono text-xs text-slate-500">{f.sku}</p>
                      </td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{f.cantidad !== null ? fmtCantidad(f.cantidad) : "—"}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums text-slate-500">
                        {f.pendiente !== null ? fmtCantidad(f.pendiente) : "—"}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-slate-600">{f.estadoProducto === "OBSERVADO" ? "Observado" : "Conforme"}</td>
                      <td className="px-4 py-2.5 font-mono text-xs text-slate-600">{f.guia ?? "—"}</td>
                      <td className="px-4 py-2.5 text-xs">
                        {f.error ? (
                          <span className="inline-flex items-start gap-1.5 text-rose-700">
                            <XCircle className="mt-px size-3.5 shrink-0" />
                            {f.error}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-emerald-700">
                            <CheckCircle2 className="size-3.5" />
                            {resultado.aplicada ? "Registrada" : "OK"}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
