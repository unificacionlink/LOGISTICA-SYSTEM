import type { ClaseAbc, DemandaDashboard, EstadoRequerimiento } from "../../api/types";
import EstadoBadge from "../EstadoBadge";
import { ETIQUETA_ESTADO, fmtCantidad, fmtDia } from "../formato";
import { Barras, Columnas, Panel, Tile, Vacio } from "./comunes";
import { fmtMes, fmtMonto, fmtNum } from "./formato";

/**
 * Matriz ABC: es un orden (A > B > C), no tres identidades, asi que va en un
 * solo tono de la marca de oscuro a claro (secuencial).
 */
const TONO_CLASE: Record<ClaseAbc, string> = {
  A: "bg-brand-700",
  B: "bg-brand-400",
  C: "bg-brand-200",
};

const REGLA_CLASE: Record<ClaseAbc, string> = {
  A: "80% del valor",
  B: "siguiente 15%",
  C: "último 5%",
};

function MatrizAbc({ abc }: { abc: DemandaDashboard["abc"] }) {
  if (abc.productos.length === 0) {
    return (
      <Vacio>
        No hubo despachos valorizados en el período.
        {abc.sinCosto > 0 && ` ${abc.sinCosto} producto(s) despachado(s) sin costo de compra en soles.`}
      </Vacio>
    );
  }
  return (
    <div className="flex flex-col gap-5">
      {/* Participacion de cada clase en el valor: una barra apilada con 2px de separacion. */}
      <div>
        <div className="flex h-3.5 gap-0.5" role="img" aria-label="Participación de cada clase en el valor despachado">
          {abc.clases
            .filter((c) => c.valor > 0)
            .map((c, i, todas) => (
              <span
                key={c.clase}
                className={`${TONO_CLASE[c.clase]} ${i === 0 ? "rounded-l-[4px]" : ""} ${i === todas.length - 1 ? "rounded-r-[4px]" : ""}`}
                style={{ width: `${(c.valor / abc.total) * 100}%` }}
                title={`Clase ${c.clase}: ${fmtMonto("PEN", c.valor)}`}
              />
            ))}
        </div>
        <ul className="mt-3 grid grid-cols-3 gap-3">
          {abc.clases.map((c) => (
            <li key={c.clase} className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-semibold text-slate-900">
                <span className={`size-2.5 rounded-[3px] ${TONO_CLASE[c.clase]}`} aria-hidden />
                Clase {c.clase}
              </span>
              <span className="block text-xs text-slate-500">
                {c.productos} producto(s) · {REGLA_CLASE[c.clase]}
              </span>
              <span className="block text-sm tabular-nums text-slate-800">{fmtMonto("PEN", c.valor)}</span>
            </li>
          ))}
        </ul>
      </div>

      <table className="tabla -mx-1 w-[calc(100%+0.5rem)]">
        <thead>
          <tr className="cabecera-tabla bg-transparent">
            <th className="px-1 pb-2">Producto</th>
            <th className="px-2 pb-2 text-right">Unidades</th>
            <th className="px-2 pb-2 text-right">Valor</th>
            <th className="px-2 pb-2 text-right">%</th>
            <th className="px-1 pb-2 text-center">Clase</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {abc.productos.map((p) => (
            <tr key={p.sku}>
              <td className="col-flex px-1 py-2" title={`${p.sku} · ${p.nombre}`}>
                <span className="font-mono text-xs text-slate-500">{p.sku}</span>{" "}
                <span className="text-slate-800">{p.nombre}</span>
              </td>
              <td className="px-2 py-2 text-right tabular-nums text-slate-600">{fmtCantidad(p.unidades)}</td>
              <td className="px-2 py-2 text-right tabular-nums text-slate-800">{fmtMonto("PEN", p.valor)}</td>
              <td className="px-2 py-2 text-right tabular-nums text-slate-600">{fmtNum(p.pct)}</td>
              <td className="px-1 py-2 text-center text-xs font-semibold text-slate-800">{p.clase}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {abc.sinCosto > 0 && (
        <p className="text-xs text-slate-500">{abc.sinCosto} producto(s) despachado(s) sin costo de compra en soles.</p>
      )}
    </div>
  );
}

function Detalle({ data }: { data: DemandaDashboard }) {
  if (data.detalle.length === 0) return <Vacio>No hay líneas solicitadas en el período.</Vacio>;
  return (
    <div className="-mx-5 -mb-5 overflow-x-auto">
      <table className="tabla w-full">
        <thead>
          <tr className="cabecera-tabla">
            <th className="px-5 py-2.5">Requerimiento</th>
            <th className="px-3 py-2.5">Fecha</th>
            <th className="px-3 py-2.5">CECO</th>
            <th className="px-3 py-2.5">Estado</th>
            <th className="px-3 py-2.5">Familia</th>
            <th className="px-3 py-2.5">Producto</th>
            <th className="px-5 py-2.5 text-right">Cantidad</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {data.detalle.map((l, i) => (
            <tr key={`${l.requerimiento}-${l.sku}-${i}`}>
              <td className="px-5 py-2 font-mono text-xs text-slate-800">
                {l.requerimiento}
                <span className="ml-1.5 font-sans text-[0.6875rem] text-slate-400">{l.empresa}</span>
              </td>
              <td className="px-3 py-2 text-slate-600">{fmtDia(l.fecha)}</td>
              <td className="px-3 py-2 text-slate-600">{l.ceco ?? "—"}</td>
              <td className="px-3 py-2">
                <EstadoBadge estado={l.estado} />
              </td>
              <td className="px-3 py-2 text-slate-600" title={`${l.familia} › ${l.subfamilia}`}>{l.familia}</td>
              <td className="col-flex px-3 py-2" title={`${l.sku} · ${l.producto}`}>
                <span className="font-mono text-xs text-slate-500">{l.sku}</span>{" "}
                <span className="text-slate-800">{l.producto}</span>
              </td>
              <td className="px-5 py-2 text-right tabular-nums text-slate-800">{fmtCantidad(l.cantidad)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function PestanaDemanda({ data }: { data: DemandaDashboard }) {
  const maxMes = Math.max(1, ...data.porMes.map((m) => m.lineas));
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <Tile rotulo="Requerimientos" valor={data.requerimientos} detalle="Enviados en el período" />
        <Tile rotulo="Líneas solicitadas" valor={data.lineas} />
        <Tile rotulo="SKU distintos" valor={data.skus} />
        <Tile rotulo="Familias" valor={data.familias} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel titulo="Líneas por familia">
          <Barras
            vacio="No hay líneas solicitadas en el período."
            barras={data.porFamilia.map((f) => ({
              clave: f.clave,
              etiqueta: f.clave,
              valor: f.lineas,
              texto: String(f.lineas),
              titulo: `${f.clave}: ${f.lineas} línea(s), ${fmtCantidad(f.unidades)} unidades`,
            }))}
          />
        </Panel>
        <Panel titulo="Líneas por mes">
          <Columnas
            vacio="No hay líneas solicitadas en el período."
            resumen={
              <>
                <span className="font-semibold text-slate-900">{data.lineas}</span> líneas en el período
              </>
            }
            textoMax={String(maxMes)}
            aria={`Líneas solicitadas por mes: ${data.lineas} en total`}
            columnas={data.porMes.map((m) => ({
              clave: m.clave,
              eje: fmtMes(m.clave),
              rotulo: fmtMes(m.clave),
              valor: m.lineas,
              texto: `${m.lineas} líneas · ${fmtCantidad(m.unidades)} u.`,
            }))}
          />
        </Panel>
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <Panel titulo="Productos más solicitados">
            <Barras
              anchoEtiqueta="minmax(0, 15rem)"
              vacio="No hay líneas solicitadas en el período."
              barras={data.productos.map((p) => ({
                clave: p.sku,
                etiqueta: `${p.sku} · ${p.nombre}`,
                valor: p.lineas,
                texto: String(p.lineas),
                titulo: `${p.sku} · ${p.nombre}: ${p.lineas} línea(s), ${fmtCantidad(p.unidades)} unidades`,
              }))}
            />
          </Panel>
        </div>
        <div className="lg:col-span-2">
          <Panel titulo="Requerimientos por estado">
            <Barras
              anchoEtiqueta="10.5rem"
              vacio="No hay requerimientos en el período."
              barras={data.porEstado.map((e) => {
                const etiqueta = ETIQUETA_ESTADO[e.clave as EstadoRequerimiento] ?? e.clave;
                return {
                  clave: e.clave,
                  etiqueta,
                  valor: e.lineas,
                  texto: String(e.lineas),
                  titulo: `${etiqueta}: ${e.lineas} requerimiento(s)`,
                };
              })}
            />
          </Panel>
        </div>
      </div>

      <Panel titulo="Matriz ABC" nota="Lo despachado del almacén, valorizado al último costo de compra (sin IGV).">
        <MatrizAbc abc={data.abc} />
      </Panel>

      <Panel
        titulo="Detalle de líneas"
        nota={data.totalDetalle > data.detalle.length ? `Últimas ${data.detalle.length} de ${data.totalDetalle}` : undefined}
      >
        <Detalle data={data} />
      </Panel>
    </div>
  );
}
