import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { ArrowDownLeft, ArrowUpRight, SlidersHorizontal, X } from "lucide-react";
import { fijarMinimoStock, obtenerKardex } from "../../api/stock";
import type { MovimientoKardex, SaldoStock } from "../../api/types";
import { fmtCantidad } from "../AlmacenBadges";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import { Skeleton } from "../ui/Skeleton";
import EstadoStock from "./EstadoStock";

const ORIGEN: Record<string, string> = {
  RECEPCION: "Recepción de OC",
  SALIDA: "Despacho",
  CAJA_CHICA: "Caja chica",
  AJUSTE: "Ajuste manual",
  CARGA_STOCK: "Inventario",
};

function fmtFecha(iso: string): string {
  return new Date(iso).toLocaleString("es-PE", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/**
 * Panel lateral de un SKU (DEC-L045): saldo, stock mínimo editable y kardex
 * (cada entrada, salida y ajuste con su documento, motivo y usuario).
 */
export default function PanelKardex({
  saldo,
  onCerrar,
  onActualizado,
}: {
  saldo: SaldoStock;
  onCerrar: () => void;
  onActualizado: (s: SaldoStock) => void;
}) {
  const [movimientos, setMovimientos] = useState<MovimientoKardex[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [minimo, setMinimo] = useState(saldo.minimo != null ? String(saldo.minimo) : "");
  const [guardando, setGuardando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  useEffect(() => {
    obtenerKardex(saldo.variantId).then((res) => {
      if (res.ok && res.result) setMovimientos(res.result.movimientos);
      else setError(res.message);
    });
  }, [saldo.variantId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCerrar();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar]);

  const minimoActual = saldo.minimo != null ? String(saldo.minimo) : "";
  const cambioMinimo = minimo.trim() !== minimoActual;

  async function onGuardarMinimo(e: React.FormEvent) {
    e.preventDefault();
    const valor = minimo.trim() === "" ? null : Number(minimo);
    if (valor != null && (Number.isNaN(valor) || valor < 0)) {
      setAviso("Ingresa un número mayor o igual a 0.");
      return;
    }
    setGuardando(true);
    setAviso(null);
    try {
      const res = await fijarMinimoStock(saldo.variantId, valor === 0 ? null : valor);
      if (res.ok && res.result) {
        onActualizado(res.result);
        setMinimo(res.result.minimo != null ? String(res.result.minimo) : "");
      } else setAviso(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return createPortal(
    <div className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20" onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}>
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="kardex-titulo"
        className="flex h-full w-full max-w-lg flex-col border-l border-slate-200 bg-white shadow-flotante"
      >
        <header className="flex items-start gap-3 border-b border-slate-200 px-5 py-4">
          <div className="min-w-0 flex-1">
            <p className="font-mono text-xs text-slate-500">{saldo.codigoVariante}</p>
            <h2 id="kardex-titulo" className="mt-0.5 text-base font-semibold text-slate-900">
              {saldo.nombreProducto}
              {saldo.atributo && <span className="font-normal text-slate-500"> · {saldo.atributo}</span>}
            </h2>
          </div>
          <button
            type="button"
            onClick={onCerrar}
            aria-label="Cerrar"
            className="rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X className="size-5" />
          </button>
        </header>

        <div className="grid grid-cols-3 gap-px border-b border-slate-200 bg-slate-200">
          {[
            ["Físico", saldo.fisico],
            ["Reservado", saldo.reservado],
            ["Disponible", saldo.disponible],
          ].map(([etiqueta, valor]) => (
            <div key={etiqueta as string} className="bg-white px-5 py-3">
              <p className="rotulo">{etiqueta}</p>
              <p className="mt-0.5 text-lg font-semibold tabular-nums text-slate-900">
                {fmtCantidad(valor as number)} <span className="text-xs font-normal text-slate-400">{saldo.unidad}</span>
              </p>
            </div>
          ))}
        </div>

        <form onSubmit={onGuardarMinimo} className="flex flex-wrap items-end gap-2 border-b border-slate-200 px-5 py-3">
          <label className="flex flex-col gap-1">
            <span className="rotulo">Stock mínimo</span>
            <input
              type="number"
              min={0}
              step="any"
              value={minimo}
              onChange={(e) => setMinimo(e.target.value)}
              placeholder="Sin mínimo"
              className={`${inputClass} w-32`}
            />
          </label>
          {cambioMinimo && (
            <Button type="submit" size="sm" variant="secondary" loading={guardando}>
              Guardar
            </Button>
          )}
          <span className="ml-auto self-center">
            <EstadoStock saldo={saldo} />
          </span>
          {aviso && <p className="w-full text-xs text-rose-700">{aviso}</p>}
        </form>

        <div className="flex items-center justify-between px-5 pb-2 pt-4">
          <span className="rotulo">Movimientos</span>
          <Button href={`/stock/ajustes?variante=${saldo.variantId}`} size="sm" variant="ghost" icon={<SlidersHorizontal className="size-3.5" />}>
            Ajustar
          </Button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-5">
          {error ? (
            <Alert tone="danger">{error}</Alert>
          ) : !movimientos ? (
            <div className="flex flex-col gap-3">
              {[0, 1, 2, 3].map((k) => (
                <Skeleton key={k} className="h-12 w-full" />
              ))}
            </div>
          ) : movimientos.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">Sin movimientos registrados.</p>
          ) : (
            <ol className="divide-y divide-slate-100">
              {movimientos.map((m, i) => {
                const entra = m.cantidad > 0;
                return (
                  <li key={i} className="flex items-start gap-3 py-2.5">
                    <span
                      className={`mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md ${
                        entra ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"
                      }`}
                    >
                      {entra ? <ArrowDownLeft className="size-4" /> : <ArrowUpRight className="size-4" />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm text-slate-800">
                        {ORIGEN[m.origen] ?? m.origen}
                        {m.referencia && <span className="font-mono text-xs text-slate-500"> · {m.referencia}</span>}
                      </p>
                      <p className="truncate text-xs text-slate-500" title={m.motivo ?? undefined}>
                        {fmtFecha(m.fecha)} · {m.usuario}
                        {m.empresa !== "TDEMSRL" && ` · ${m.empresa}`}
                        {m.motivo && ` · ${m.motivo}`}
                      </p>
                    </div>
                    <div className="shrink-0 text-right tabular-nums">
                      <p className={`text-sm font-medium ${entra ? "text-emerald-700" : "text-rose-700"}`}>
                        {entra ? "+" : "−"}
                        {fmtCantidad(Math.abs(m.cantidad))}
                      </p>
                      <p className="text-xs text-slate-400">saldo {fmtCantidad(m.saldo)}</p>
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}
