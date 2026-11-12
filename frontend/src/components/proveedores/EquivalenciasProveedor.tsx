import { useEffect, useState } from "react";
import { ChevronDown, Trash2 } from "lucide-react";
import { listarEquivalencias, quitarEquivalencia } from "../../api/proveedores";
import type { EquivalenciaProveedor } from "../../api/types";
import Alert from "../ui/Alert";
import { Card } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";
import { useConfirmar } from "../ui/Confirmacion";

/**
 * Como llama este proveedor a nuestros productos (DEC-L053). Se aprende solo al
 * guardar sus cotizaciones; aqui se revisa y se quita lo que quedo mal.
 */
export default function EquivalenciasProveedor({ supplierId }: { supplierId: string }) {
  const confirmar = useConfirmar();
  const [items, setItems] = useState<EquivalenciaProveedor[] | null>(null);
  const [abierto, setAbierto] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [quitando, setQuitando] = useState<string | null>(null);

  useEffect(() => {
    listarEquivalencias(supplierId).then((res) => {
      if (res.ok) setItems(res.result ?? []);
      else setError(res.message);
    });
  }, [supplierId]);

  async function quitar(e: EquivalenciaProveedor) {
    const r = await confirmar({
      titulo: "¿Quitar esta equivalencia?",
      mensaje: `«${e.supplierDescription}» dejará de reconocerse como ${e.codigoVariante ?? e.skuBase}.`,
      confirmar: "Quitar",
      peligro: true,
    });
    if (!r) return;
    setQuitando(e.equivalenceId);
    try {
      const res = await quitarEquivalencia(e.equivalenceId);
      if (res.ok) setItems((prev) => (prev ?? []).filter((x) => x.equivalenceId !== e.equivalenceId));
      else setError(res.message);
    } finally {
      setQuitando(null);
    }
  }

  if (items === null && !error) {
    return <Skeleton className="mt-6 h-12 w-full rounded-xl" />;
  }

  return (
    <Card className="mt-6 overflow-hidden">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        className="flex w-full items-center justify-between px-5 py-3.5 text-left"
      >
        <span className="font-semibold text-slate-900">Cómo llama a nuestros productos ({items?.length ?? 0})</span>
        <ChevronDown className={`size-4 text-slate-400 transition-transform ${abierto ? "rotate-180" : ""}`} />
      </button>
      {abierto && (
        <div className="border-t border-slate-200">
          {error && <Alert tone="danger" className="m-4">{error}</Alert>}
          {items && items.length === 0 ? (
            <p className="px-5 py-4 text-sm text-slate-500">Aún no hay: se aprenden al revisar sus cotizaciones.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {(items ?? []).map((e) => (
                <li key={e.equivalenceId} className="flex items-start gap-3 px-5 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-slate-800">
                      «{e.supplierDescription}»
                      {e.supplierCode && <span className="ml-1.5 font-mono text-xs text-slate-500">{e.supplierCode}</span>}
                    </p>
                    <p className="mt-0.5 truncate text-xs text-slate-500">
                      <span className="font-mono">{e.codigoVariante ?? e.skuBase}</span> · {e.nombreProducto}
                      {e.atributo ? ` · ${e.atributo}` : ""}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => quitar(e)}
                    disabled={quitando === e.equivalenceId}
                    title="Quitar"
                    aria-label={`Quitar equivalencia ${e.supplierDescription}`}
                    className="shrink-0 text-slate-400 hover:text-rose-600 disabled:opacity-50"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}
