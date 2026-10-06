import { useEffect, useState } from "react";
import { obtenerCatalogoFormulario } from "../api/requerimientos";
import { listarEmpresasStock, registrarAjusteStock } from "../api/stock";
import type { EmpresaOpcion, VarianteCatalogo } from "../api/types";
import ProductoCombobox from "../components/ProductoCombobox";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

export default function StockAjustes() {
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [variantes, setVariantes] = useState<VarianteCatalogo[]>([]);
  const [empresas, setEmpresas] = useState<EmpresaOpcion[]>([]);

  const [companyId, setCompanyId] = useState("");
  const [variante, setVariante] = useState<VarianteCatalogo | null>(null);
  const [qty, setQty] = useState("1");
  const [comment, setComment] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mensaje, setMensaje] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([obtenerCatalogoFormulario(), listarEmpresasStock()])
      .then(([catalogo, empresasRes]) => {
        if (catalogo.ok && catalogo.result) setVariantes(catalogo.result.variantes);
        else setErrorCarga(catalogo.message);
        if (empresasRes.ok) setEmpresas(empresasRes.result ?? []);
        else setErrorCarga(empresasRes.message);
      })
      .finally(() => setCargando(false));
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!companyId || !variante || Number(qty) <= 0) {
      setError("Selecciona una empresa, un producto y una cantidad mayor a 0.");
      return;
    }
    setError(null);
    setMensaje(null);
    setEnviando(true);
    try {
      const res = await registrarAjusteStock({
        companyId,
        variantId: variante.variantId,
        qty: Number(qty),
        comment: comment || undefined,
      });
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      setMensaje(`Nuevo saldo disponible de ${res.result.codigoVariante}: ${res.result.saldoDisponible}.`);
      setVariante(null);
      setQty("1");
      setComment("");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Registrar ajuste de stock"
        backTo="/stock"
        backLabel="Stock"
      />

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
        <Card>
          <CardBody>
            <form onSubmit={onSubmit} className="flex flex-col gap-5">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Empresa" required>
                  <select required value={companyId} onChange={(e) => setCompanyId(e.target.value)} className={`${inputClass} w-full`}>
                    <option value="" disabled>
                      Selecciona una empresa…
                    </option>
                    {empresas.map((e) => (
                      <option key={e.companyId} value={e.companyId}>
                        {e.code}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Cantidad" required>
                  <input
                    type="number"
                    min={0.001}
                    step="0.001"
                    value={qty}
                    onChange={(e) => setQty(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              <Field label="Producto" required>
                <ProductoCombobox variantes={variantes} valor={variante} onSeleccionar={setVariante} />
              </Field>

              <Field label="Comentario">
                <textarea
                  rows={2}
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Opcional: motivo del ajuste"
                  className={`${inputClass} w-full`}
                />
              </Field>

              {error && <Alert tone="danger">{error}</Alert>}
              {mensaje && <Alert tone="good">{mensaje}</Alert>}

              <Button type="submit" loading={enviando} className="self-start">
                {enviando ? "Guardando…" : "Registrar ajuste"}
              </Button>
            </form>
          </CardBody>
        </Card>
      )}
    </div>
  );
}
