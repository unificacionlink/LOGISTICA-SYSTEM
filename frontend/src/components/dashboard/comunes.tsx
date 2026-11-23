import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { MARCA, fmtNum } from "./formato";

// ---------------------------------------------------------------------------

export function Tile({
  rotulo,
  valor,
  unidad,
  detalle,
  children,
}: {
  rotulo: string;
  valor: ReactNode;
  unidad?: string;
  detalle?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="flex flex-col rounded-lg border border-slate-200 bg-white px-4 py-4">
      <span className="rotulo">{rotulo}</span>
      <span className="mt-2.5 flex items-baseline gap-1.5">
        <span className="text-[1.75rem] font-semibold leading-none tracking-[-0.02em] text-slate-950">{valor}</span>
        {unidad && <span className="text-sm text-slate-500">{unidad}</span>}
      </span>
      {children}
      {detalle && <span className="mt-auto pt-2.5 text-xs text-slate-500">{detalle}</span>}
    </div>
  );
}

/** Medidor de un porcentaje: pista y relleno del mismo tono. */
export function Medidor({ valor, className = "mt-3" }: { valor: number | null; className?: string }) {
  return (
    <span className={`block h-1.5 overflow-hidden rounded-full bg-brand-100 ${className}`}>
      <span className={`block h-full rounded-full ${MARCA}`} style={{ width: `${valor ?? 0}%` }} />
    </span>
  );
}

export function Panel({
  titulo,
  nota,
  accion,
  children,
}: {
  titulo: string;
  nota?: string;
  accion?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3.5">
        <div className="min-w-0">
          <h2 className="text-[0.9375rem] font-semibold text-slate-900">{titulo}</h2>
          {nota && <p className="mt-0.5 truncate text-xs text-slate-500">{nota}</p>}
        </div>
        {accion}
      </header>
      <div className="p-5">{children}</div>
    </section>
  );
}

export function Vacio({ children }: { children: ReactNode }) {
  return <p className="py-8 text-center text-sm text-slate-400">{children}</p>;
}

export interface Barra {
  clave: string;
  etiqueta: string;
  valor: number;
  /** Texto a la derecha (si no, el valor). */
  texto?: string;
  /** Tooltip de la fila. */
  titulo?: string;
  to?: string;
}

/**
 * Barras horizontales de magnitud, una por fila, de un solo tono. La fila
 * entera es la zona de hover (title) y, si tiene destino, enlace.
 */
export function Barras({ barras, vacio, anchoEtiqueta = "8.5rem" }: { barras: Barra[]; vacio: string; anchoEtiqueta?: string }) {
  if (barras.length === 0) return <Vacio>{vacio}</Vacio>;
  const max = Math.max(1, ...barras.map((b) => b.valor));
  return (
    <ul className="flex flex-col gap-0.5">
      {barras.map((b) => {
        const contenido = (
          <>
            <span className="truncate text-sm text-slate-700 group-hover:text-slate-950">{b.etiqueta}</span>
            <span className="h-3.5">
              {b.valor > 0 && (
                <span
                  className={`block h-full rounded-r-[4px] ${MARCA} transition-[width] duration-500 ease-(--ease-suave)`}
                  style={{ width: `${Math.max(2, (b.valor / max) * 100)}%` }}
                />
              )}
            </span>
            <span className="text-right text-sm font-medium tabular-nums text-slate-900">{b.texto ?? fmtNum(b.valor)}</span>
          </>
        );
        const clase = "group grid items-center gap-3 rounded-md px-1.5 py-1.5 transition-colors hover:bg-slate-50";
        const estilo = { gridTemplateColumns: `${anchoEtiqueta} 1fr auto` };
        return (
          <li key={b.clave}>
            {b.to ? (
              <Link to={b.to} className={clase} style={estilo} title={b.titulo ?? b.etiqueta}>
                {contenido}
              </Link>
            ) : (
              <div className={clase} style={estilo} title={b.titulo ?? b.etiqueta}>
                {contenido}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export interface Columna {
  clave: string;
  /** Etiqueta del eje (corta). */
  eje: string;
  /** Lo que se lee arriba al pasar el cursor. */
  rotulo: string;
  valor: number;
  texto: string;
}

/**
 * Columnas verticales (serie temporal) con lectura arriba al pasar el cursor;
 * eje con solo el maximo (grilla tenue). `resumen` se ve sin hover.
 */
export function Columnas({
  columnas,
  resumen,
  vacio,
  textoMax,
  aria,
}: {
  columnas: Columna[];
  resumen: ReactNode;
  vacio: string;
  textoMax: string;
  aria: string;
}) {
  const [activa, setActiva] = useState<number | null>(null);
  const max = Math.max(1, ...columnas.map((c) => c.valor));
  if (columnas.every((c) => c.valor === 0)) return <Vacio>{vacio}</Vacio>;
  // Etiquetas del eje X espaciadas: ~6 visibles como maximo.
  const paso = Math.max(1, Math.ceil(columnas.length / 6));
  const sel = activa !== null ? columnas[activa] : null;

  return (
    <div>
      <div className="mb-3 flex h-5 items-baseline gap-2 text-sm">
        {sel ? (
          <>
            <span className="text-slate-500">{sel.rotulo}</span>
            <span className="font-semibold text-slate-900">{sel.texto}</span>
          </>
        ) : (
          <span className="text-slate-500">{resumen}</span>
        )}
      </div>
      <div className="relative">
        <div className="pointer-events-none absolute inset-x-0 top-0 border-t border-slate-100">
          <span className="absolute -top-2.5 right-0 bg-white pl-1 text-[0.625rem] tabular-nums text-slate-400">{textoMax}</span>
        </div>
        <div
          className="flex h-44 items-end gap-0.5 border-b border-slate-200"
          onMouseLeave={() => setActiva(null)}
          role="img"
          aria-label={aria}
        >
          {columnas.map((c, i) => (
            <button
              key={c.clave}
              type="button"
              aria-label={`${c.rotulo}: ${c.texto}`}
              onMouseEnter={() => setActiva(i)}
              onFocus={() => setActiva(i)}
              onBlur={() => setActiva(null)}
              className="flex h-full flex-1 items-end justify-center focus-visible:outline-none"
            >
              <span
                className={`block w-[68%] max-w-10 rounded-t-[4px] transition-[height,opacity] duration-500 ease-(--ease-suave) ${MARCA} ${
                  activa !== null && activa !== i ? "opacity-40" : ""
                }`}
                style={{ height: c.valor > 0 ? `${Math.max(2, (c.valor / max) * 100)}%` : "0" }}
              />
            </button>
          ))}
        </div>
        <div className="mt-1.5 flex gap-0.5">
          {columnas.map((c, i) => (
            <span key={c.clave} className="flex-1 text-center text-[0.625rem] tabular-nums text-slate-400">
              {i % paso === 0 ? c.eje : ""}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
