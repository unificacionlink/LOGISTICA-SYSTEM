import { useEffect, useMemo, useState } from "react";
import { Search, Send } from "lucide-react";
import { candidatosRfq } from "../../api/compras";
import type { CandidatoRfq, Consolidado } from "../../api/types";
import Alert from "../ui/Alert";
import Button from "../ui/Button";
import { inputClass } from "../ui/Field";
import PanelLateral, { SeccionPanel } from "../ui/PanelLateral";
import { Skeleton } from "../ui/Skeleton";

const normal = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

/**
 * Pedir cotizacion eligiendo a quien (DEC-L082): lista los proveedores
 * habilitados de los consolidados elegidos, primero los que cubren mas.
 * No se puede confirmar hasta que cada producto tenga al menos un proveedor.
 */
export default function PanelPedirCotizacion({
  consolidados,
  onCerrar,
  onConfirmar,
}: {
  consolidados: Consolidado[];
  onCerrar: () => void;
  onConfirmar: (supplierIds: string[]) => Promise<boolean>;
}) {
  const [candidatos, setCandidatos] = useState<CandidatoRfq[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [elegidos, setElegidos] = useState<Set<string>>(new Set());
  const [busqueda, setBusqueda] = useState("");
  const [enviando, setEnviando] = useState(false);
  const ids = useMemo(() => consolidados.map((c) => c.consolidationId), [consolidados]);

  useEffect(() => {
    let vigente = true;
    void candidatosRfq(ids).then((res) => {
      if (!vigente) return;
      if (res.ok && res.result) setCandidatos(res.result);
      else setError(res.message);
    });
    return () => {
      vigente = false;
    };
  }, [ids]);

  const cubiertos = useMemo(() => {
    const s = new Set<string>();
    for (const c of candidatos ?? []) if (elegidos.has(c.supplierId)) c.consolidados.forEach((id) => s.add(id));
    return s;
  }, [candidatos, elegidos]);
  const sinCubrir = consolidados.filter((c) => !cubiertos.has(c.consolidationId));
  const sinProveedor = consolidados.filter(
    (c) => candidatos && !candidatos.some((p) => p.consolidados.includes(c.consolidationId)),
  );
  const visibles = (candidatos ?? []).filter((c) => {
    const q = normal(busqueda.trim());
    return !q || normal(`${c.nombre} ${c.ruc}`).includes(q);
  });

  function alternar(id: string) {
    setElegidos((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  async function confirmar() {
    setEnviando(true);
    try {
      await onConfirmar(Array.from(elegidos));
    } finally {
      setEnviando(false);
    }
  }

  const etiqueta = (c: Consolidado) => c.codigoVariante ?? c.skuBase;

  return (
    <PanelLateral
      titulo="Pedir cotización"
      subtitulo={`${consolidados.length} producto(s)`}
      onCerrar={onCerrar}
      pie={
        <div className="flex items-center justify-between gap-3">
          <span className="truncate text-sm text-slate-500">
            {sinCubrir.length > 0 ? `Falta proveedor para ${sinCubrir.length} producto(s)` : "Todos los productos cubiertos"}
          </span>
          <Button
            icon={<Send className="size-4" />}
            loading={enviando}
            disabled={elegidos.size === 0 || sinCubrir.length > 0}
            onClick={confirmar}
          >
            Pedir a {elegidos.size} proveedor(es)
          </Button>
        </div>
      }
    >
      {error && <Alert tone="danger">{error}</Alert>}
      {sinProveedor.length > 0 && (
        <Alert tone="info">
          Sin proveedor habilitado: {sinProveedor.map(etiqueta).join(", ")}. Asígnalo en el Catálogo de productos.
        </Alert>
      )}

      <SeccionPanel titulo="Proveedores">
        {!candidatos ? (
          <div className="flex flex-col gap-2">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : candidatos.length === 0 ? (
          <p className="text-sm text-slate-500">Ningún proveedor habilitado para estos productos.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {candidatos.length > 8 && (
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <input
                  className={`${inputClass} w-full pl-9`}
                  placeholder="Buscar por razón social o RUC"
                  value={busqueda}
                  onChange={(e) => setBusqueda(e.target.value)}
                />
              </div>
            )}
            <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
              {visibles.map((c) => (
                <li key={c.supplierId}>
                  <label className="flex cursor-pointer items-center gap-3 px-3 py-2.5 hover:bg-slate-50">
                    <input type="checkbox" checked={elegidos.has(c.supplierId)} onChange={() => alternar(c.supplierId)} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-slate-800" title={c.nombre}>
                        {c.nombre}
                      </span>
                      <span className="font-mono text-xs text-slate-500">{c.ruc}</span>
                      {c.critico && <span className="ml-2 text-xs font-medium text-amber-700">Crítico</span>}
                    </span>
                    <span className="shrink-0 text-xs tabular-nums text-slate-500">
                      {c.consolidados.length} de {consolidados.length}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </div>
        )}
      </SeccionPanel>

      {candidatos && sinCubrir.length > 0 && elegidos.size > 0 && (
        <SeccionPanel titulo="Sin proveedor elegido">
          <p className="whitespace-normal text-sm text-slate-600">{sinCubrir.map(etiqueta).join(", ")}</p>
        </SeccionPanel>
      )}
    </PanelLateral>
  );
}
