import { memo } from "react";
import { Clock3 } from "lucide-react";
import type { EtapaTablero, FilaBandeja, TableroRequerimientos } from "../../api/types";
import { EMPRESAS } from "../empresas";
import PrioridadBadge from "../PrioridadBadge";
import { fmtDia } from "../formato";
import { Skeleton } from "../ui/Skeleton";
import { etiquetaAccion } from "./acciones";
import BarraAvance from "./BarraAvance";
import { diasParaVencer, textoVencimiento } from "./bandeja";

const MARCA = new Map(EMPRESAS.map((e) => [e.code, e]));

/** Color de cada columna, del inicio al final del flujo. */
const PUNTO: Record<EtapaTablero, string> = {
  aprobacion: "bg-slate-400",
  abastecimiento: "bg-amber-500",
  preparacion: "bg-sky-500",
  transito: "bg-blue-600",
  diferencias: "bg-rose-500",
  atendido: "bg-emerald-600",
};

const ETAPAS: { id: EtapaTablero; label: string }[] = [
  { id: "aprobacion", label: "Por aprobar" },
  { id: "abastecimiento", label: "Abastecimiento" },
  { id: "preparacion", label: "Por despachar" },
  { id: "transito", label: "En tránsito" },
  { id: "diferencias", label: "Con diferencias" },
  { id: "atendido", label: "Atendido" },
];

const PREPARACION: Record<string, string> = {
  PICKING: "Picking pendiente",
  EMBALAJE: "Embalaje pendiente",
  ENVIO: "Registrar envío",
};

type Motivo = { texto: string; tono: "accion" | "espera" | "alerta" };

/**
 * Por que el requerimiento aun no esta atendido: lo que Logistica puede hacer
 * primero, luego lo que espera de otros y las diferencias en destino.
 */
function motivos(f: FilaBandeja): Motivo[] {
  const lista: Motivo[] = [];
  if (f.status === "OBSERVADO") lista.push({ texto: "Observado: espera corrección", tono: "alerta" });
  if (f.tablero.preparacion) lista.push({ texto: PREPARACION[f.tablero.preparacion], tono: "accion" });
  for (const a of f.acciones) {
    if (a.codigo === "CORRECCION") continue;
    lista.push({ texto: etiquetaAccion(a), tono: a.espera ? "espera" : "accion" });
  }
  if (f.tablero.observado > 0) {
    lista.push({ texto: `${f.tablero.observado} u. observadas en destino`, tono: "alerta" });
  }
  return lista.toSorted((a, b) => orden(a.tono) - orden(b.tono));
}

function orden(t: Motivo["tono"]): number {
  return t === "accion" ? 0 : t === "alerta" ? 1 : 2;
}

const ESTILO_MOTIVO: Record<Motivo["tono"], string> = {
  accion: "border-brand-200 bg-brand-50 text-brand-800",
  espera: "border-slate-200 bg-slate-50 text-slate-600",
  alerta: "border-rose-200 bg-rose-50 text-rose-700",
};

const Tarjeta = memo(function Tarjeta({
  f,
  conEmpresa,
  seleccionada,
  onAbrir,
}: {
  f: FilaBandeja;
  conEmpresa: boolean;
  seleccionada: boolean;
  onAbrir: (id: string) => void;
}) {
  const empresa = MARCA.get(f.empresaCode);
  const nombres = (f.resumenItems ?? "").split(" · ").filter(Boolean);
  const venc = textoVencimiento(diasParaVencer(f));
  const lista = motivos(f);
  const visibles = lista.slice(0, 3);
  const resto = lista.slice(3);

  return (
    <li>
      <button
        type="button"
        onClick={() => onAbrir(f.id)}
        aria-current={seleccionada || undefined}
        className={`flex w-full flex-col gap-2 rounded-lg border bg-white p-3 text-left shadow-superficie transition-colors hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 ${
          seleccionada ? "border-brand-400" : "border-slate-200"
        }`}
      >
        <div className="flex items-center gap-2">
          {conEmpresa && (
            <span
              className={`size-1.5 shrink-0 rounded-full ${empresa?.marca ?? "bg-slate-400"}`}
              title={empresa?.label ?? f.empresaCode}
              aria-hidden="true"
            />
          )}
          <span className="font-mono text-xs font-medium text-brand-700">{f.code}</span>
          {f.priority !== "NORMAL" && (
            <span className="ml-auto">
              <PrioridadBadge prioridad={f.priority} />
            </span>
          )}
        </div>
        <p className="flex min-w-0 items-baseline gap-1.5" title={f.resumenItems ?? ""}>
          <span className="truncate text-sm text-slate-900">{nombres[0] ?? "Sin productos"}</span>
          {f.itemsCount > 1 && <span className="shrink-0 text-xs text-slate-400">+{f.itemsCount - 1}</span>}
        </p>
        <p className="truncate text-xs text-slate-500" title={`${f.cecoCode ?? ""} ${f.cecoNombre ?? ""}`}>
          {f.cecoNombre ?? "—"}
          {f.solicitante && <span className="text-slate-400"> · {f.solicitante}</span>}
        </p>
        {f.tablero.etapa !== "aprobacion" && <BarraAvance avance={f.avance} />}
        {visibles.length > 0 && (
          <ul className="flex flex-wrap gap-1">
            {visibles.map((m) => (
              <li
                key={m.texto}
                className={`inline-flex max-w-full items-center gap-1 rounded-md border px-1.5 py-0.5 text-[0.6875rem] font-medium ${ESTILO_MOTIVO[m.tono]}`}
              >
                {m.tono === "espera" && <Clock3 className="size-3 shrink-0" />}
                <span className="truncate">{m.texto}</span>
              </li>
            ))}
            {resto.length > 0 && (
              <li className="text-[0.6875rem] text-slate-400" title={resto.map((m) => m.texto).join(" · ")}>
                +{resto.length}
              </li>
            )}
          </ul>
        )}
        <div className="flex items-center gap-2 text-[0.6875rem]">
          {f.requiredDate ? (
            <span className="tabular-nums text-slate-500">Requerido {fmtDia(f.requiredDate)}</span>
          ) : (
            <span className="text-slate-400">Sin fecha</span>
          )}
          {venc && <span className={`ml-auto font-medium ${venc.tono}`}>{venc.texto}</span>}
        </div>
      </button>
    </li>
  );
});

/**
 * Tablero Kanban de Logistica (DEC-L067): cada requerimiento en curso va en la
 * primera etapa del flujo que aun tiene algo pendiente, y su tarjeta dice por
 * que no avanza. Clic en la tarjeta abre el mismo panel que la lista.
 */
export default function TableroRequerimientosVista({
  datos,
  conEmpresa,
  reqAbierto,
  onAbrir,
}: {
  datos: TableroRequerimientos | null;
  conEmpresa: boolean;
  reqAbierto: string | null;
  onAbrir: (id: string) => void;
}) {
  return (
    <div className="-mx-1 flex snap-x gap-3 overflow-x-auto px-1 pb-3" aria-label="Tablero de requerimientos">
      {(datos?.columnas ?? ETAPAS.map((e) => ({ ...e, total: 0, tarjetas: [] as FilaBandeja[] }))).map((c) => (
        <section
          key={c.id}
          aria-labelledby={`col-${c.id}`}
          className="flex w-[17.5rem] shrink-0 snap-start flex-col rounded-xl border border-slate-200 bg-slate-50/80"
        >
          <header className="flex items-center gap-2 px-3 py-2.5">
            <span className={`size-2 rounded-full ${PUNTO[c.id]}`} aria-hidden="true" />
            <h2 id={`col-${c.id}`} className="text-sm font-semibold text-slate-800">
              {c.label}
            </h2>
            <span className="rounded-md bg-white px-1.5 font-mono text-xs tabular-nums text-slate-500 ring-1 ring-slate-200">
              {datos ? c.total : "–"}
            </span>
          </header>
          <ul className="flex max-h-[calc(100dvh-17rem)] min-h-24 flex-col gap-2 overflow-y-auto px-2 pb-2">
            {!datos ? (
              [0, 1, 2].map((k) => (
                <li key={k} className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="h-3.5 w-4/5" />
                  <Skeleton className="h-3 w-3/5" />
                  <Skeleton className="h-1.5 w-full" />
                </li>
              ))
            ) : c.tarjetas.length === 0 ? (
              <li className="px-1 py-6 text-center text-xs text-slate-400">Sin requerimientos</li>
            ) : (
              c.tarjetas.map((f) => (
                <Tarjeta key={f.id} f={f} conEmpresa={conEmpresa} seleccionada={f.id === reqAbierto} onAbrir={onAbrir} />
              ))
            )}
            {datos && c.total > c.tarjetas.length && (
              <li className="px-1 py-1 text-center text-[0.6875rem] text-slate-400">
                {c.id === "atendido"
                  ? `Los ${c.tarjetas.length} más recientes de ${c.total}`
                  : `Mostrando ${c.tarjetas.length} de ${c.total}`}
              </li>
            )}
          </ul>
        </section>
      ))}
    </div>
  );
}
