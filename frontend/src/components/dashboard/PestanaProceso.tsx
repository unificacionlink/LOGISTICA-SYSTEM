import type { CodigoEtapa, Dashboard as DashboardData } from "../../api/types";
import { Barras, Columnas, Medidor, Panel, Tile, Vacio } from "./comunes";
import { MARCA, fmtMonto, fmtNum, pct } from "./formato";

const ETAPAS: Record<CodigoEtapa, { label: string; to: string }> = {
  POR_APROBAR: { label: "Por aprobar", to: "/aprobaciones" },
  EN_COTIZACION: { label: "En cotización", to: "/compras" },
  OC_EN_CURSO: { label: "OC en curso", to: "/compras/ordenes" },
  POR_DESPACHAR: { label: "Por despachar", to: "/almacen" },
  EN_TRANSITO: { label: "En tránsito", to: "/almacen" },
};

/** Horas si es menos de 2 dias; si no, dias (48 h se lee peor que 2 d). */
function duracionHoras(horas: number | null): { valor: string; unidad: string } {
  if (horas === null) return { valor: "—", unidad: "" };
  if (horas < 48) return { valor: fmtNum(horas), unidad: "h" };
  return { valor: fmtNum(horas / 24), unidad: "días" };
}

function fmtSemana(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}

function TablaProveedores({ filas }: { filas: DashboardData["proveedores"] }) {
  if (filas.length === 0) return <Vacio>No se enviaron RFQ en el período.</Vacio>;
  return (
    <table className="tabla -mx-1 w-[calc(100%+0.5rem)]">
      <thead>
        <tr className="cabecera-tabla bg-transparent">
          <th className="px-1 pb-2">Proveedor</th>
          <th className="px-2 pb-2 text-right">RFQ</th>
          <th className="px-2 pb-2">Respuesta</th>
          <th className="px-2 pb-2 text-right">Días</th>
          <th className="px-1 pb-2 text-right">Elegido</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {filas.map((p) => {
          const porcentaje = pct(p.respondidas, p.enviadas) ?? 0;
          return (
            <tr key={p.proveedor}>
              <td className="col-flex px-1 py-2.5 text-slate-800" title={p.proveedor}>{p.proveedor}</td>
              <td className="px-2 py-2.5 text-right text-slate-600">{p.enviadas}</td>
              <td className="px-2 py-2.5">
                <span className="flex items-center gap-2" title={`${p.respondidas} de ${p.enviadas} respondidas`}>
                  <span className="h-1.5 w-16 overflow-hidden rounded-full bg-brand-100">
                    <span className={`block h-full rounded-full ${MARCA}`} style={{ width: `${porcentaje}%` }} />
                  </span>
                  <span className="w-9 text-right text-xs text-slate-700">{porcentaje}%</span>
                </span>
              </td>
              <td className="px-2 py-2.5 text-right text-slate-600">{fmtNum(p.diasRespuesta)}</td>
              <td className="px-1 py-2.5 text-right text-slate-600">{p.elegidas}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function TablaComparativos({ filas }: { filas: DashboardData["comparativos"] }) {
  if (filas.length === 0) return <Vacio>Aún no hay compras con dos o más cotizaciones en el período.</Vacio>;
  return (
    <table className="tabla -mx-1 w-[calc(100%+0.5rem)]">
      <thead>
        <tr className="cabecera-tabla bg-transparent">
          <th className="px-1 pb-2">Consolidado</th>
          <th className="px-2 pb-2">Proveedor elegido</th>
          <th className="px-2 pb-2 text-right">Elegida vs mayor</th>
          <th className="px-1 pb-2 text-right">Ahorro</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {filas.map((c) => (
          <tr key={`${c.codigo}-${c.fecha}`}>
            <td className="px-1 py-2.5">
              <span className="block font-mono text-xs text-slate-800">{c.codigo}</span>
              <span className="text-[0.6875rem] text-slate-400">{c.cotizaciones} cotizaciones</span>
            </td>
            <td className="col-flex px-2 py-2.5 text-slate-700" title={c.proveedor}>{c.proveedor}</td>
            <td className="px-2 py-2.5 text-right text-slate-600">
              {fmtMonto(c.moneda, c.elegido)}
              <span className="block text-[0.6875rem] text-slate-400">vs {fmtMonto(c.moneda, c.mayor)}</span>
            </td>
            <td className="px-1 py-2.5 text-right font-medium text-slate-900">{fmtMonto(c.moneda, c.ahorro)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** "Desde stock 90% · Con compra 70%": solo los grupos con entregas. */
function detallePlazo(c: DashboardData["cumplimiento"]): string {
  const partes: string[] = [];
  const s = pct(c.desdeStockATiempo, c.desdeStock);
  const k = pct(c.conCompraATiempo, c.conCompra);
  if (s !== null) partes.push(`Desde stock ${s}%`);
  if (k !== null) partes.push(`Con compra ${k}%`);
  return partes.length > 0 ? partes.join(" · ") : "Sin entregas en el período";
}

export default function PestanaProceso({ data }: { data: DashboardData }) {
  const respuesta = pct(data.rfqRespondidas, data.rfqEnviadas);
  const aTiempo = pct(data.cumplimiento.aTiempo, data.cumplimiento.entregados);
  const aprobacion = duracionHoras(data.horasAprobacion);
  const ahorroPen = data.ahorro.find((a) => a.moneda === "PEN");
  const ahorroOtras = data.ahorro.filter((a) => a.moneda !== "PEN");
  const totalSemanas = data.semanas.reduce((a, s) => a + s.creados, 0);
  const maxSemana = Math.max(1, ...data.semanas.map((s) => s.creados));

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <Tile
          rotulo="Abiertos"
          valor={data.abiertos}
          detalle={
            <>
              {data.vencidos > 0 && <span className="block font-medium text-rose-700">{data.vencidos} fuera de plazo</span>}
              {data.creados} creados · {data.completados} completados
            </>
          }
        />
        <Tile rotulo="Aprobación" valor={aprobacion.valor} unidad={aprobacion.unidad} detalle="Promedio hasta aprobarse" />
        <Tile
          rotulo="Ciclo completo"
          valor={fmtNum(data.diasCiclo)}
          unidad={data.diasCiclo !== null ? "días" : undefined}
          detalle="De creado a entregado en destino"
        />
        <Tile
          rotulo="A tiempo"
          valor={aTiempo ?? "—"}
          unidad={aTiempo !== null ? "%" : undefined}
          detalle={detallePlazo(data.cumplimiento)}
        >
          <Medidor valor={aTiempo} />
        </Tile>
        <Tile
          rotulo="Respuesta proveedores"
          valor={respuesta ?? "—"}
          unidad={respuesta !== null ? "%" : undefined}
          detalle={`${data.rfqRespondidas} de ${data.rfqEnviadas} RFQ · ${fmtNum(data.diasRespuesta)} días`}
        >
          <Medidor valor={respuesta} />
        </Tile>
        <Tile
          rotulo="Ahorro por comparar"
          valor={ahorroPen ? fmtMonto("PEN", ahorroPen.monto) : "—"}
          detalle={
            <>
              {ahorroOtras.map((a) => (
                <span key={a.moneda} className="block text-slate-700">+ {fmtMonto(a.moneda, a.monto)}</span>
              ))}
              {data.comprasConComparativo} de {data.comprasTotal} compras con 2+ cotizaciones
            </>
          }
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <Panel titulo="Trabajo pendiente por etapa" nota="Hoy. Un requerimiento puede estar en más de una etapa.">
            <Barras
              anchoEtiqueta="7.5rem"
              vacio="Sin trabajo pendiente."
              barras={data.etapas.map((e) => ({
                clave: e.codigo,
                etiqueta: ETAPAS[e.codigo].label,
                valor: e.cantidad,
                texto: String(e.cantidad),
                titulo: `${ETAPAS[e.codigo].label}: ${e.cantidad} requerimiento(s)`,
                to: ETAPAS[e.codigo].to,
              }))}
            />
          </Panel>
        </div>
        <div className="lg:col-span-3">
          <Panel titulo="Requerimientos creados por semana">
            <Columnas
              vacio="No se crearon requerimientos en el período."
              resumen={
                <>
                  <span className="font-semibold text-slate-900">{totalSemanas}</span> en el período
                </>
              }
              textoMax={String(maxSemana)}
              aria={`Requerimientos creados por semana: ${totalSemanas} en total, máximo ${maxSemana} en una semana`}
              columnas={data.semanas.map((s) => ({
                clave: s.semana,
                eje: fmtSemana(s.semana),
                rotulo: `Semana del ${fmtSemana(s.semana)}`,
                valor: s.creados,
                texto: `${s.creados} creados`,
              }))}
            />
          </Panel>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel titulo="Desempeño de proveedores" nota="RFQ recibidas, % respondidas, días en responder y veces elegido.">
          <TablaProveedores filas={data.proveedores} />
        </Panel>
        <Panel titulo="Comparativo de cotizaciones" nota="Oferta adjudicada frente a la más cara del mismo consolidado.">
          <TablaComparativos filas={data.comparativos} />
        </Panel>
      </div>
    </div>
  );
}
