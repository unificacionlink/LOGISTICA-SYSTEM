import { useState } from "react";
import type { GastoDashboard, MontoGasto } from "../../api/types";
import { EMPRESAS } from "../empresas";
import { Barras, Columnas, Panel, Tile, Vacio, type Barra } from "./comunes";
import { fmtMes, fmtMonto, fmtMontoCorto } from "./formato";

function nombreEmpresa(code: string | null): string {
  return EMPRESAS.find((e) => e.code === code)?.label ?? code ?? "—";
}

function aBarras(filas: MontoGasto[], moneda: string, etiqueta: (m: MontoGasto) => string): Barra[] {
  return filas
    .filter((f) => f.moneda === moneda)
    .map((f) => {
      const texto = etiqueta(f);
      const ordenes = f.ordenes != null ? ` · ${f.ordenes} OC` : "";
      return {
        clave: f.clave ?? "-",
        etiqueta: texto,
        valor: f.monto,
        texto: fmtMontoCorto(moneda, f.monto),
        titulo: `${texto}${f.detalle ? ` · ${f.detalle}` : ""}: ${fmtMonto(moneda, f.monto)}${ordenes}`,
      };
    });
}

export default function PestanaGasto({ data }: { data: GastoDashboard }) {
  const [elegida, setElegida] = useState<string | null>(null);
  if (data.monedas.length === 0) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white">
        <Vacio>No se aprobaron órdenes de compra en el período.</Vacio>
      </section>
    );
  }
  // PEN primero (el servidor ya ordena); si se elige una moneda que ya no esta, vuelve a la primera.
  const actual = data.monedas.find((m) => m.moneda === elegida) ?? data.monedas[0];
  const moneda = actual.moneda;
  const porMes = new Map(data.porMes.filter((m) => m.moneda === moneda).map((m) => [m.clave, m]));
  const maxMes = Math.max(0, ...Array.from(porMes.values()).map((m) => m.monto));

  return (
    <div className="flex flex-col gap-4">
      {data.monedas.length > 1 && (
        <div role="radiogroup" aria-label="Moneda" className="inline-flex self-start rounded-md border border-slate-200 bg-white p-0.5">
          {data.monedas.map((m) => (
            <button
              key={m.moneda}
              type="button"
              role="radio"
              aria-checked={m.moneda === moneda}
              onClick={() => setElegida(m.moneda)}
              className={`rounded px-3 py-1 text-[0.8125rem] transition-colors ${
                m.moneda === moneda ? "bg-brand-700 font-medium text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              {m.moneda}
            </button>
          ))}
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 xl:grid-cols-3">
        <Tile rotulo="Gasto sin IGV" valor={fmtMonto(moneda, actual.monto)} detalle="Órdenes de compra aprobadas" />
        <Tile rotulo="Órdenes de compra" valor={actual.ordenes} />
        <Tile rotulo="Proveedores" valor={actual.proveedores} detalle="Con al menos una OC" />
      </div>

      <div className="grid gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <Panel titulo="Gasto por mes">
            <Columnas
              vacio="Sin gasto en el período."
              resumen={
                <>
                  <span className="font-semibold text-slate-900">{fmtMonto(moneda, actual.monto)}</span> en el período
                </>
              }
              textoMax={fmtMontoCorto(moneda, maxMes)}
              aria={`Gasto por mes en ${moneda}: ${fmtMonto(moneda, actual.monto)} en total`}
              columnas={data.meses.map((mes) => {
                const m = porMes.get(mes);
                return {
                  clave: mes,
                  eje: fmtMes(mes),
                  rotulo: fmtMes(mes),
                  valor: m?.monto ?? 0,
                  texto: m ? `${fmtMonto(moneda, m.monto)} · ${m.ordenes} OC` : fmtMonto(moneda, 0),
                };
              })}
            />
          </Panel>
        </div>
        <div className="lg:col-span-2">
          <Panel titulo="Por empresa">
            <Barras
              anchoEtiqueta="6rem"
              vacio="Sin gasto en el período."
              barras={aBarras(data.porEmpresa, moneda, (f) => nombreEmpresa(f.clave))}
            />
          </Panel>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel titulo="Por CECO" nota="Una OC consolidada se reparte entre los CECOs de sus requerimientos.">
          <Barras
            anchoEtiqueta="minmax(0, 12rem)"
            vacio="Sin gasto en el período."
            barras={aBarras(data.porCeco, moneda, (f) => (f.clave ? `${f.clave} · ${f.detalle ?? ""}` : "Para stock (sin CECO)"))}
          />
        </Panel>
        <Panel titulo="Por familia">
          <Barras
            anchoEtiqueta="minmax(0, 12rem)"
            vacio="Sin gasto en el período."
            barras={aBarras(data.porFamilia, moneda, (f) => f.clave ?? "—")}
          />
        </Panel>
      </div>

      <Panel titulo="Proveedores con más compra">
        <Barras
          anchoEtiqueta="minmax(0, 18rem)"
          vacio="Sin gasto en el período."
          barras={aBarras(data.proveedores, moneda, (f) => f.clave ?? "—")}
        />
      </Panel>
    </div>
  );
}
