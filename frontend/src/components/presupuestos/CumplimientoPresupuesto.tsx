import { useEffect, useState } from "react";
import { descargarCumplimientoExcel, obtenerCumplimiento } from "../../api/presupuestos";
import type { Cumplimiento, MontosCumplimiento } from "../../api/types";
import BotonDescarga from "../BotonDescarga";
import Alert from "../ui/Alert";
import { Card, CardBody } from "../ui/Card";
import { SkeletonTable } from "../ui/Skeleton";
import { soles } from "./formato";

const COLUMNAS = ["Presupuesto", "Real", "Pendiente", "Saldo", "Disponible"];
const cantidad = new Intl.NumberFormat("es-PE", { maximumFractionDigits: 3 });

function fmtFechaHora(iso: string) {
  return new Date(iso).toLocaleString("es-PE", { dateStyle: "short", timeStyle: "short" });
}

function Monto({ valor, vacio }: { valor: number | null; vacio: string }) {
  if (valor == null) return <span className="text-xs text-amber-600">{vacio}</span>;
  return <span className={valor < 0 ? "text-rose-600" : undefined}>{soles.format(valor)}</span>;
}

/**
 * Cumplimiento por cantidades y precios congelados de la versión vigente
 * (RES-01). Real = recepcionado en destino. Con costos, se elige ver los
 * montos a precio de venta o de compra (evita duplicar columnas).
 */
export default function CumplimientoPresupuesto({ presupuestoId }: { presupuestoId: string }) {
  const [datos, setDatos] = useState<Cumplimiento | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [base, setBase] = useState<"venta" | "compra">("venta");

  useEffect(() => {
    obtenerCumplimiento(presupuestoId).then((res) => {
      if (res.ok) setDatos(res.result);
      else setError(res.message);
    });
  }, [presupuestoId]);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!datos) {
    return (
      <Card className="overflow-hidden">
        <SkeletonTable cols={6} />
      </Card>
    );
  }

  const montos = (venta: MontosCumplimiento, compra: MontosCumplimiento | null) =>
    base === "compra" && compra ? compra : venta;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm text-slate-500">
          Versión {datos.version} · corte {fmtFechaHora(datos.corte)}
        </span>
        {datos.conCompra && (
          <div role="tablist" aria-label="Base de los montos" className="flex rounded-md border border-slate-200 p-0.5">
            {(["venta", "compra"] as const).map((b) => (
              <button
                key={b}
                type="button"
                role="tab"
                aria-selected={base === b}
                onClick={() => setBase(b)}
                className={`rounded px-2.5 py-1 text-xs font-medium ${
                  base === b ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100"
                }`}
              >
                {b === "venta" ? "Precio de venta" : "Precio de compra"}
              </button>
            ))}
          </div>
        )}
        {datos.puedeDescargar && (
          <span className="ml-auto">
            <BotonDescarga tipo="excel" etiqueta="Excel" descargar={() => descargarCumplimientoExcel(presupuestoId, datos.codigo)} />
          </span>
        )}
      </div>

      <Card className="overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Resumen por familia · PEN neto</div>
        </CardBody>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-y border-slate-200 bg-slate-50/70 text-left text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                <th className="px-4 py-2.5">Familia</th>
                {COLUMNAS.map((c) => (
                  <th key={c} className="px-3 py-2.5 text-right">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {datos.resumen.map((r) => (
                <tr key={r.familia} className={r.familia === "TOTAL" ? "font-medium text-slate-900" : "text-slate-700"}>
                  <td className="px-4 py-2.5">{r.familia}</td>
                  {montos(r.venta, r.compra).map((v, i) => (
                    <td key={i} className="px-3 py-2.5 text-right tabular-nums">
                      <Monto valor={v} vacio="Incompleto" />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card className="overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Detalle por SKU</div>
        </CardBody>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-y border-slate-200 bg-slate-50/70 text-left text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                <th className="px-4 py-2.5">SKU</th>
                <th className="px-3 py-2.5 text-right">Programado</th>
                <th className="px-3 py-2.5 text-right">Real</th>
                <th className="px-3 py-2.5 text-right">Pendiente</th>
                <th className="px-3 py-2.5 text-right">Saldo</th>
                <th className="px-3 py-2.5 text-right">Disponible</th>
                <th className="px-4 py-2.5 text-right">Disponible S/</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {datos.filas.map((f) => (
                <tr key={f.productId} className="text-slate-700">
                  <td className="max-w-64 px-4 py-2.5">
                    <div className="font-mono text-xs text-slate-600">
                      {f.sku}
                      {f.sinPresupuesto && <span className="ml-1.5 font-sans text-amber-600">sin presupuesto</span>}
                    </div>
                    <div className="truncate text-xs text-slate-500" title={f.nombre}>
                      {f.nombre}
                    </div>
                  </td>
                  {[f.programado, f.real, f.pendiente, f.saldo, f.disponible].map((q, i) => (
                    <td key={i} className={`px-3 py-2.5 text-right tabular-nums ${q < 0 ? "text-rose-600" : ""}`}>
                      {cantidad.format(q)}
                    </td>
                  ))}
                  <td className="px-4 py-2.5 text-right tabular-nums">
                    <Monto valor={montos(f.venta, f.compra)[4]} vacio="Sin precio" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
