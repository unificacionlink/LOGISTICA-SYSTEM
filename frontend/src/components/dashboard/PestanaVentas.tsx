import type { VentasDashboard } from "../../api/types";
import { fmtCantidad, fmtDia } from "../formato";
import { nombreEmpresa } from "../empresas";
import { Barras, Columnas, Panel, Tile, Vacio } from "./comunes";
import { fmtMes, fmtMonto, fmtMontoCorto } from "./formato";

/**
 * Ventas de TDEM a GOYDEL y METRIN (DEC-L086): lo despachado a sus proyectos
 * (menos lo devuelto) y lo que el proveedor les entregó directo, al precio de
 * venta de cada empresa. Sirve para facturar fuera del ERP.
 */
export default function PestanaVentas({ data }: { data: VentasDashboard }) {
  if (data.totales.length === 0) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white">
        <Vacio>TDEM no entregó nada a proyectos de GOYDEL ni de METRIN en el período.</Vacio>
      </section>
    );
  }
  const total = data.totales.reduce((s, t) => s + t.monto, 0);
  const sinPrecio = data.totales.reduce((s, t) => s + t.sinPrecio, 0);
  const porMes = new Map<string, number>();
  for (const m of data.porMes) porMes.set(m.clave, (porMes.get(m.clave) ?? 0) + m.monto);
  const maxMes = Math.max(0, ...porMes.values());

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 xl:grid-cols-3">
        {data.totales.map((t) => (
          <Tile
            key={t.empresa}
            rotulo={`Ventas a ${nombreEmpresa(t.empresa)}`}
            valor={fmtMonto("PEN", t.monto)}
            detalle={
              t.sinPrecio > 0 ? (
                <span className="text-amber-700">
                  {t.sinPrecio} {t.sinPrecio === 1 ? "línea sin precio de venta" : "líneas sin precio de venta"}
                </span>
              ) : (
                `${t.lineas} ${t.lineas === 1 ? "línea" : "líneas"}`
              )
            }
          />
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <Panel titulo="Ventas por mes">
            <Columnas
              vacio="Sin ventas valorizadas en el período."
              resumen={
                <>
                  <span className="font-semibold text-slate-900">{fmtMonto("PEN", total)}</span> en el período
                </>
              }
              textoMax={fmtMontoCorto("PEN", maxMes)}
              aria={`Ventas por mes: ${fmtMonto("PEN", total)} en total`}
              columnas={data.meses.map((mes) => ({
                clave: mes,
                eje: fmtMes(mes),
                rotulo: fmtMes(mes),
                valor: porMes.get(mes) ?? 0,
                texto: fmtMonto("PEN", porMes.get(mes) ?? 0),
              }))}
            />
          </Panel>
        </div>
        <div className="lg:col-span-2">
          <Panel titulo="Por CECO">
            <Barras
              anchoEtiqueta="minmax(0, 12rem)"
              vacio="Sin ventas valorizadas en el período."
              barras={data.porCeco.map((c) => {
                const etiqueta = `${c.clave ?? "—"} · ${c.detalle ?? ""}`;
                return {
                  clave: `${c.empresa}-${c.clave}`,
                  etiqueta,
                  valor: c.monto,
                  texto: fmtMontoCorto("PEN", c.monto),
                  titulo: `${nombreEmpresa(c.empresa)} · ${etiqueta}: ${fmtMonto("PEN", c.monto)}`,
                };
              })}
            />
          </Panel>
        </div>
      </div>

      <Panel titulo="Últimas entregas vendidas" nota={sinPrecio > 0 ? "Las líneas sin precio no suman al total: registra su precio de venta en el Catálogo." : undefined}>
        <div className="-m-5 overflow-hidden">
          <table className="tabla">
            <thead>
              <tr className="border-b border-slate-200 cabecera-tabla">
                <th className="px-4 py-2.5">Fecha</th>
                <th className="px-4 py-2.5">Documento</th>
                <th className="px-4 py-2.5">Empresa</th>
                <th className="px-4 py-2.5">CECO</th>
                <th className="px-4 py-2.5">Producto</th>
                <th className="px-4 py-2.5 text-right">Cantidad</th>
                <th className="px-4 py-2.5 text-right">Precio</th>
                <th className="px-4 py-2.5 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.lineas.map((l, i) => (
                <tr key={`${l.documento}-${l.sku}-${i}`}>
                  <td className="px-4 py-2.5 text-slate-600">{fmtDia(l.fecha)}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-slate-700">{l.documento}</td>
                  <td className="px-4 py-2.5 text-slate-600">{nombreEmpresa(l.empresa)}</td>
                  <td className="px-4 py-2.5 text-slate-600" title={l.cecoNombre ?? undefined}>
                    {l.ceco ?? "—"}
                  </td>
                  <td className="col-flex px-4 py-2.5 text-slate-700" title={l.producto}>
                    <span className="font-mono text-xs text-slate-500">{l.sku}</span> {l.producto}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">{fmtCantidad(l.cantidad)}</td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-slate-700">
                    {l.precio !== null ? fmtMonto("PEN", l.precio) : <span className="text-amber-700">Sin precio</span>}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums font-medium text-slate-900">
                    {l.total !== null ? fmtMonto("PEN", l.total) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </div>
  );
}
