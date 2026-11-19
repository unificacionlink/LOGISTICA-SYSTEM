import { useEffect, useState } from "react";
import { Info } from "lucide-react";
import { obtenerDesempenoProveedor } from "../../api/proveedores";
import type { DesempenoProveedor } from "../../api/types";
import { cantidad } from "../compras/formato";
import { ETIQUETA_TIPO_INCIDENCIA } from "../formato";
import Alert from "../ui/Alert";
import { Card } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";

function Indicador({ titulo, valor, detalle, nota }: { titulo: string; valor: string; detalle?: string; nota?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-superficie">
      <p className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
        {titulo}
        {nota && (
          <span className="rounded bg-slate-100 px-1 text-[0.625rem] font-semibold uppercase tracking-wide text-slate-500">{nota}</span>
        )}
      </p>
      <p className="mt-1 font-serif text-[1.625rem] leading-none tabular-nums text-slate-950">{valor}</p>
      {detalle && <p className="mt-1.5 text-xs text-slate-500">{detalle}</p>}
    </div>
  );
}

const pct = (a: number, b: number) => (b === 0 ? "—" : `${Math.round((a * 100) / b)}%`);

/**
 * Desempeno calculado con lo que ya registran Compras y Almacen (DEC-L069).
 * Lo que no se puede medir con los datos actuales se lista como pendiente.
 */
export default function PestanaDesempeno({ supplierId }: { supplierId: string }) {
  const [d, setD] = useState<DesempenoProveedor | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    obtenerDesempenoProveedor(supplierId).then((res) => {
      if (res.ok && res.result) setD(res.result);
      else setError(res.message);
    });
  }, [supplierId]);

  if (error) return <Alert tone="danger">{error}</Alert>;
  if (!d) {
    return (
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-[5.5rem] rounded-xl" />
        ))}
      </div>
    );
  }

  const sinDatos = d.ordenes === 0 && d.cotizaciones === 0;

  return (
    <div className="space-y-4">
      {sinDatos && <Alert tone="info">Este proveedor todavía no tiene compras registradas.</Alert>}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <Indicador
          titulo="Cantidades atendidas"
          valor={d.atencionPct == null ? "—" : `${cantidad(d.atencionPct)}%`}
          detalle={`${cantidad(d.cantidadRecibida)} de ${cantidad(d.cantidadOrdenada)} unidades en ${d.ordenes} OC`}
        />
        <Indicador
          titulo="Entregas en el plazo ofrecido"
          nota="Referencial"
          valor={pct(d.plazoOfrecido.dentroDelPlazo, d.plazoOfrecido.ordenesConPlazo)}
          detalle={`${d.plazoOfrecido.dentroDelPlazo} de ${d.plazoOfrecido.ordenesConPlazo} OC con plazo en la cotización`}
        />
        <Indicador
          titulo="Plazo real promedio"
          valor={d.plazoRealPromedioDias == null ? "—" : `${cantidad(d.plazoRealPromedioDias)} d`}
          detalle="De la liberación de la OC a la primera recepción"
        />
        <Indicador
          titulo="Recepciones observadas"
          valor={pct(d.recepcionesObservadas, d.recepciones)}
          detalle={`${d.recepcionesObservadas} de ${d.recepciones} recepciones`}
        />
        <Indicador
          titulo="Incidencias"
          valor={String(d.incidencias.total)}
          detalle={
            d.incidencias.total === 0
              ? "Sin incidencias"
              : `${d.incidencias.abiertas} abiertas · cierre en ${
                  d.incidencias.diasCierrePromedio == null ? "—" : `${cantidad(d.incidencias.diasCierrePromedio)} d`
                }`
          }
        />
        <Indicador
          titulo="Cotizaciones adjudicadas"
          valor={pct(d.cotizacionesAdjudicadas, d.cotizaciones)}
          detalle={`${d.cotizacionesAdjudicadas} de ${d.cotizaciones}`}
        />
      </div>

      {d.incidencias.porTipo.length > 0 && (
        <Card className="px-5 py-4">
          <p className="rotulo mb-2">Incidencias por tipo</p>
          <ul className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-700">
            {d.incidencias.porTipo.map((t) => (
              <li key={t.tipo}>
                {ETIQUETA_TIPO_INCIDENCIA[t.tipo as keyof typeof ETIQUETA_TIPO_INCIDENCIA] ?? t.tipo}{" "}
                <span className="font-semibold tabular-nums">{t.cantidad}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="px-5 py-4">
        <p className="rotulo mb-2 flex items-center gap-1.5">
          <Info className="size-3.5" /> Pendiente de datos
        </p>
        <ul className="space-y-1.5 text-sm">
          {d.pendientes.map((p) => (
            <li key={p.indicador} className="whitespace-normal text-slate-600">
              <span className="font-medium text-slate-800">{p.indicador}:</span> {p.motivo}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
