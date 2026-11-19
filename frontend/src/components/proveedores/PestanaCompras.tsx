import { useState } from "react";
import { useDatos } from "../../api/useDatos";
import { ChevronDown } from "lucide-react";
import { obtenerComprasProveedor } from "../../api/proveedores";
import { cantidad, dinero } from "../compras/formato";
import { fmtDia, fmtFecha } from "../formato";
import Alert from "../ui/Alert";
import { Card } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";
import { ESTADO_COTIZACION, ESTADO_OC } from "./estados";

type Vista = "ordenes" | "productos" | "cotizaciones";

function Dato({ titulo, valor }: { titulo: string; valor: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-superficie">
      <p className="text-xs font-medium text-slate-500">{titulo}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">{valor}</p>
    </div>
  );
}

/**
 * Historial comercial (DEC-L069): solicitudes de cotizacion, cotizaciones,
 * ordenes y productos comprados con su precio, leidos de Compras tal cual.
 * Los montos van por moneda: no hay tipo de cambio registrado.
 */
export default function PestanaCompras({ supplierId }: { supplierId: string }) {
  // Memoria de la pestana (DEC-L073): volver a la pestana no la vacia.
  const compras = useDatos(`proveedor-compras:${supplierId}`, () => obtenerComprasProveedor(supplierId));
  const datos = compras.datos;
  const error = datos ? null : compras.error;
  const [vista, setVista] = useState<Vista>("ordenes");
  const [abierto, setAbierto] = useState<string | null>(null);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!datos) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-[4.5rem] rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-48 rounded-xl" />
      </div>
    );
  }

  const comprado = datos.totales.length ? datos.totales.map((t) => dinero(t.total, t.moneda)).join(" · ") : "—";
  const vistas: { id: Vista; label: string; n: number }[] = [
    { id: "ordenes", label: "Órdenes de compra", n: datos.ordenes.length },
    { id: "productos", label: "Productos y precios", n: datos.productos.length },
    { id: "cotizaciones", label: "Cotizaciones", n: datos.cotizaciones.length },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Dato titulo="Comprado" valor={comprado} />
        <Dato titulo="Órdenes" valor={String(datos.totales.reduce((s, t) => s + t.ordenes, 0))} />
        <Dato titulo="Solicitudes respondidas" valor={`${datos.rfqs.respondidas} de ${datos.rfqs.enviadas}`} />
        <Dato
          titulo="Respuesta promedio"
          valor={datos.rfqs.diasRespuestaPromedio == null ? "—" : `${cantidad(datos.rfqs.diasRespuestaPromedio)} días`}
        />
      </div>

      <Card className="overflow-hidden">
        <div className="flex gap-1 border-b border-slate-200 px-3 py-2">
          {vistas.map((v) => (
            <button
              key={v.id}
              type="button"
              aria-pressed={vista === v.id}
              onClick={() => setVista(v.id)}
              className={`rounded-md px-2.5 py-1.5 text-sm font-medium ${
                vista === v.id ? "bg-brand-50 text-brand-800" : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              {v.label} <span className="tabular-nums text-slate-400">{v.n}</span>
            </button>
          ))}
        </div>

        {vista === "ordenes" &&
          (datos.ordenes.length === 0 ? (
            <p className="px-5 py-6 text-sm text-slate-500">Sin órdenes de compra.</p>
          ) : (
            <div className="overflow-hidden">
              <table className="tabla">
                <thead>
                  <tr className="cabecera-tabla border-b border-slate-200">
                    <th className="px-5 py-3">Orden</th>
                    <th className="px-5 py-3">Emitida</th>
                    <th className="px-5 py-3">Estado</th>
                    <th className="px-5 py-3 text-right">Total</th>
                    <th className="px-5 py-3">Primera recepción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {datos.ordenes.map((o) => (
                    <tr key={o.orderId}>
                      <td className="col-flex px-5 py-3 font-medium text-slate-800" title={o.orderCode}>
                        {o.orderCode} <span className="text-xs font-normal text-slate-500">{o.empresa}</span>
                      </td>
                      <td className="px-5 py-3 tabular-nums text-slate-600">{fmtDia(o.emitida)}</td>
                      <td className="px-5 py-3 text-slate-600">{ESTADO_OC[o.status] ?? o.status}</td>
                      <td className="px-5 py-3 text-right tabular-nums text-slate-800">{dinero(o.total, o.moneda)}</td>
                      <td className="px-5 py-3 tabular-nums text-slate-600">{fmtFecha(o.primeraRecepcion)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}

        {vista === "productos" &&
          (datos.productos.length === 0 ? (
            <p className="px-5 py-6 text-sm text-slate-500">Todavía no se le compró nada.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {datos.productos.map((p) => {
                const clave = `${p.productId}|${p.variante}|${p.moneda}`;
                return (
                  <li key={clave}>
                    <button
                      type="button"
                      aria-expanded={abierto === clave}
                      onClick={() => setAbierto((a) => (a === clave ? null : clave))}
                      className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-slate-50"
                    >
                      <ChevronDown className={`size-4 shrink-0 text-slate-400 transition-transform ${abierto === clave ? "rotate-180" : ""}`} />
                      <span className="min-w-0 flex-1 truncate text-sm text-slate-800" title={p.nombre}>
                        <span className="font-mono text-xs text-slate-500">{p.variante ?? p.skuBase}</span> {p.nombre}
                      </span>
                      <span className="text-xs tabular-nums text-slate-500">
                        {p.ordenes} OC · {cantidad(p.cantidad)} u.
                      </span>
                      <span className="w-28 text-right text-sm font-medium tabular-nums text-slate-900">
                        {dinero(p.ultimoPrecio, p.moneda)}
                      </span>
                    </button>
                    {abierto === clave && (
                      <div className="border-t border-slate-100 bg-slate-50/60 px-5 py-3 text-sm">
                        <p className="mb-2 text-slate-600">
                          Mínimo {dinero(p.precioMinimo, p.moneda)} · Máximo {dinero(p.precioMaximo, p.moneda)}
                        </p>
                        <ul className="grid grid-cols-2 gap-x-6 gap-y-1 sm:grid-cols-3">
                          {p.precios.map((h, i) => (
                            <li key={`${h.orderCode}-${i}`} className="flex justify-between gap-3 tabular-nums text-slate-700">
                              <span className="text-slate-500">{fmtDia(h.fecha)}</span>
                              {dinero(h.precio, p.moneda)}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          ))}

        {vista === "cotizaciones" &&
          (datos.cotizaciones.length === 0 ? (
            <p className="px-5 py-6 text-sm text-slate-500">Sin cotizaciones.</p>
          ) : (
            <div className="overflow-hidden">
              <table className="tabla">
                <thead>
                  <tr className="cabecera-tabla border-b border-slate-200">
                    <th className="px-5 py-3">Solicitud</th>
                    <th className="px-5 py-3">Recibida</th>
                    <th className="px-5 py-3">Estado</th>
                    <th className="px-5 py-3 text-right">Plazo</th>
                    <th className="px-5 py-3 text-right">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {datos.cotizaciones.map((q) => (
                    <tr key={q.quotationId}>
                      <td className="col-flex px-5 py-3 text-slate-800" title={q.rfqCode}>
                        {q.rfqCode}
                        {q.numero && <span className="ml-2 text-xs text-slate-500">N.º {q.numero}</span>}
                      </td>
                      <td className="px-5 py-3 tabular-nums text-slate-600">{fmtFecha(q.recibida)}</td>
                      <td className="px-5 py-3 text-slate-600">{ESTADO_COTIZACION[q.status] ?? q.status}</td>
                      <td className="px-5 py-3 text-right tabular-nums text-slate-600">
                        {q.plazoDias != null ? `${q.plazoDias} d` : "—"}
                      </td>
                      <td className="px-5 py-3 text-right tabular-nums text-slate-800">{dinero(q.total, q.moneda)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </Card>
    </div>
  );
}
