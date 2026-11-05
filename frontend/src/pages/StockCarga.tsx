import { useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, FileSpreadsheet, Upload, XCircle } from "lucide-react";
import { confirmarInventario, descargarPlantillaInventario, validarInventario } from "../api/stock";
import type { CargaStock } from "../api/types";
import { fmtCantidad } from "../components/AlmacenBadges";
import BotonDescarga from "../components/BotonDescarga";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import PageHeader from "../components/ui/PageHeader";

const th = "px-4 py-3";

/**
 * Inventario por Excel (DEC-L045): la plantilla trae todos los SKU con su
 * stock en el sistema; se escribe lo contado y el sistema ajusta solo las
 * diferencias. Validar no escribe; aplicar es todo o nada.
 */
export default function StockCarga() {
  const confirmar = useConfirmar();
  const inputRef = useRef<HTMLInputElement>(null);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [resultado, setResultado] = useState<CargaStock | null>(null);
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
      const res = await validarInventario(archivo);
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
    const partes = [
      resultado.ajustes > 0 ? `${resultado.ajustes} ajustes de stock` : null,
      resultado.minimos > 0 ? `${resultado.minimos} mínimos` : null,
    ].filter(Boolean);
    const r = await confirmar({
      titulo: "¿Aplicar el inventario?",
      mensaje: `Se registrarán ${partes.join(" y ")}.`,
      confirmar: "Aplicar",
    });
    if (!r) return;
    setProcesando("confirmar");
    setError(null);
    try {
      const res = await confirmarInventario(archivo);
      if (res.ok && res.result) {
        setResultado(res.result);
        setExito(res.message);
        setArchivo(null);
        if (inputRef.current) inputRef.current.value = "";
      } else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  const cambios = resultado ? resultado.ajustes + resultado.minimos : 0;
  const puedeConfirmar = !!archivo && !!resultado && !resultado.aplicada && resultado.conError === 0 && cambios > 0;

  return (
    <div>
      <PageHeader
        title="Inventario por Excel"
        backTo="/stock"
        backLabel="Stock"
        actions={<BotonDescarga descargar={descargarPlantillaInventario} etiqueta="Plantilla" tipo="excel" />}
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}

      <Card className="mb-4">
        <CardBody className="flex flex-wrap items-center gap-3">
          <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 rounded-md border border-dashed border-slate-300 px-4 py-3 text-sm text-slate-600 transition-colors hover:border-slate-400 hover:bg-slate-50">
            <FileSpreadsheet className="size-5 shrink-0 text-slate-400" />
            <span className="truncate">{archivo ? archivo.name : "Plantilla con el stock contado (.xlsx)"}</span>
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
            Aplicar inventario
          </Button>
        </CardBody>
      </Card>

      {resultado && (
        <Card className="overflow-hidden">
          <CardBody className="flex flex-wrap gap-x-6 gap-y-1 pb-3 text-sm text-slate-600">
            {resultado.codigo && (
              <span className="font-mono font-medium text-slate-900">{resultado.codigo}</span>
            )}
            <span>
              <b className="text-slate-900">{resultado.contadas}</b> contados
            </span>
            <span>
              <b className="text-brand-700">{resultado.ajustes}</b> {resultado.aplicada ? "ajustados" : "con diferencia"}
            </span>
            <span>
              <b className="text-slate-900">{resultado.minimos}</b> mínimos
            </span>
            <span>
              <b className="text-slate-900">{resultado.sinCambios}</b> sin cambios
            </span>
            <span>
              <b className={resultado.conError > 0 ? "text-rose-700" : "text-slate-900"}>{resultado.conError}</b> con error
            </span>
          </CardBody>
          {resultado.detalle.length > 0 && (
            <div className="border-t border-slate-200">
              <table className="tabla">
                <thead>
                  <tr className="cabecera-tabla border-b border-slate-200">
                    <th className={`${th} col-sec`}>Fila</th>
                    <th className={th}>Producto</th>
                    <th className={`${th} col-sec text-right`}>Sistema</th>
                    <th className={`${th} text-right`}>Contado</th>
                    <th className={`${th} text-right`}>Diferencia</th>
                    <th className={`${th} col-sec text-right`}>Mínimo</th>
                    <th className={th}>Resultado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {resultado.detalle.map((f) => (
                    <tr key={f.fila} className={f.error ? "bg-rose-50/40" : ""}>
                      <td className="col-sec px-4 py-2.5 text-xs tabular-nums text-slate-500">{f.fila}</td>
                      <td className="col-flex px-4 py-2.5" title={f.producto ?? undefined}>
                        <p className="truncate text-sm text-slate-800">{f.producto ?? "—"}</p>
                        <p className="font-mono text-xs text-slate-500">{f.codigoVariante || "—"}</p>
                      </td>
                      <td className="col-sec px-4 py-2.5 text-right text-slate-500">{fmtCantidad(f.fisico)}</td>
                      <td className="px-4 py-2.5 text-right text-slate-800">{fmtCantidad(f.contado)}</td>
                      <td
                        className={`px-4 py-2.5 text-right font-medium ${
                          f.diferencia == null || f.diferencia === 0
                            ? "text-slate-400"
                            : f.diferencia > 0
                              ? "text-emerald-700"
                              : "text-rose-700"
                        }`}
                      >
                        {f.diferencia == null ? "—" : `${f.diferencia > 0 ? "+" : ""}${fmtCantidad(f.diferencia)}`}
                      </td>
                      <td className="col-sec px-4 py-2.5 text-right text-xs text-slate-600">
                        {f.minimoAnterior === f.minimoNuevo
                          ? fmtCantidad(f.minimoNuevo)
                          : `${fmtCantidad(f.minimoAnterior)} → ${fmtCantidad(f.minimoNuevo)}`}
                      </td>
                      <td className="px-4 py-2.5 text-xs">
                        {f.error ? (
                          <span className="inline-flex items-start gap-1.5 text-rose-700">
                            <XCircle className="mt-px size-3.5 shrink-0" />
                            {f.error}
                          </span>
                        ) : f.aviso ? (
                          <span className="inline-flex items-start gap-1.5 whitespace-normal text-amber-700" title={f.aviso}>
                            <AlertTriangle className="mt-px size-3.5 shrink-0" />
                            Menos que lo reservado
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-emerald-700">
                            <CheckCircle2 className="size-3.5" />
                            {resultado.aplicada ? "Aplicado" : "OK"}
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
