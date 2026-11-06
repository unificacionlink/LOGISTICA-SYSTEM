import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { obtenerCatalogoFormulario } from "../api/requerimientos";
import { registrarAjusteStock } from "../api/stock";
import type { VarianteCatalogo } from "../api/types";
import { fmtCantidad } from "../components/formato";
import ProductoCombobox from "../components/ProductoCombobox";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

type Tipo = "ingreso" | "salida";

/**
 * Ajuste manual de un SKU en el almacén central del grupo (DEC-L045): ingreso
 * o salida con motivo obligatorio, que queda en el historial del SKU.
 */
export default function StockAjustes() {
  const [params] = useSearchParams();
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [variantes, setVariantes] = useState<VarianteCatalogo[]>([]);

  const [variante, setVariante] = useState<VarianteCatalogo | null>(null);
  const [tipo, setTipo] = useState<Tipo>("ingreso");
  const [qty, setQty] = useState("");
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mensaje, setMensaje] = useState<string | null>(null);

  useEffect(() => {
    obtenerCatalogoFormulario()
      .then((res) => {
        if (res.ok && res.result) {
          setVariantes(res.result.variantes);
          const inicial = params.get("variante");
          if (inicial) setVariante(res.result.variantes.find((v) => v.variantId === inicial) ?? null);
        } else setErrorCarga(res.message);
      })
      .finally(() => setCargando(false));
    // Solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const cantidad = Number(qty);
    if (!variante || !(cantidad > 0) || !motivo.trim()) {
      setError("Selecciona un producto, una cantidad mayor a 0 y el motivo.");
      return;
    }
    setError(null);
    setMensaje(null);
    setEnviando(true);
    try {
      const res = await registrarAjusteStock({
        variantId: variante.variantId,
        qty: tipo === "ingreso" ? cantidad : -cantidad,
        motivo: motivo.trim(),
      });
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      const s = res.result;
      setMensaje(`${s.codigoVariante}: físico ${fmtCantidad(s.fisico)} · disponible ${fmtCantidad(s.disponible)} ${s.unidad}.`);
      setVariante(null);
      setQty("");
      setMotivo("");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <PageHeader title="Ajuste manual" backTo="/stock" backLabel="Stock" />

      {cargando ? (
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
          </CardBody>
        </Card>
      ) : errorCarga ? (
        <Alert tone="danger">{errorCarga}</Alert>
      ) : (
        <Card className="max-w-2xl">
          <CardBody>
            <form onSubmit={onSubmit} className="flex flex-col gap-5">
              <Field label="Producto" required>
                <ProductoCombobox variantes={variantes} valor={variante} onSeleccionar={setVariante} />
              </Field>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Movimiento" required>
                  <div role="radiogroup" className="grid grid-cols-2 gap-1 rounded-md border border-slate-200 bg-slate-50 p-1">
                    {(["ingreso", "salida"] as const).map((t) => (
                      <button
                        key={t}
                        type="button"
                        role="radio"
                        aria-checked={tipo === t}
                        onClick={() => setTipo(t)}
                        className={`rounded px-3 py-1.5 text-sm font-medium transition-colors ${
                          tipo === t ? "bg-white text-slate-900 shadow-boton" : "text-slate-500 hover:text-slate-800"
                        }`}
                      >
                        {t === "ingreso" ? "Ingreso" : "Salida"}
                      </button>
                    ))}
                  </div>
                </Field>
                <Field label="Cantidad" required>
                  <input
                    type="number"
                    min={0.001}
                    step="0.001"
                    required
                    value={qty}
                    onChange={(e) => setQty(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              <Field label="Motivo" required>
                <input
                  required
                  maxLength={300}
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                  placeholder={tipo === "ingreso" ? "Ej.: devolución de obra" : "Ej.: merma por daño"}
                  className={`${inputClass} w-full`}
                />
              </Field>

              {error && <Alert tone="danger">{error}</Alert>}
              {mensaje && <Alert tone="good">{mensaje}</Alert>}

              <Button type="submit" loading={enviando} className="self-start">
                Registrar {tipo}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}
    </div>
  );
}
