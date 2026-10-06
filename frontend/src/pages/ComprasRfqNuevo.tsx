import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { FileQuestion, Lightbulb, Wallet } from "lucide-react";
import { ETIQUETA_MOTIVO, evaluarCajaChica } from "../api/cajachica";
import { crearRfq, listarMonedas, obtenerDetalleCompra } from "../api/compras";
import { listarProveedores } from "../api/proveedores";
import type { ComprasDetalle, EvaluacionCajaChica, Moneda, Proveedor } from "../api/types";
import FormCajaChica from "../components/cajachica/FormCajaChica";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

export default function ComprasRfqNuevo() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [detalle, setDetalle] = useState<ComprasDetalle | null>(null);
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);
  const [monedas, setMonedas] = useState<Moneda[]>([]);

  const [supplierId, setSupplierId] = useState("");
  const [paymentCondition, setPaymentCondition] = useState("CONTADO");
  const [deliveryMode, setDeliveryMode] = useState("AGENCIA");
  const [deliveryLocation, setDeliveryLocation] = useState("");
  const [requestedLeadDays, setRequestedLeadDays] = useState("");
  const [currencyRequested, setCurrencyRequested] = useState("");
  const [cantidades, setCantidades] = useState<Record<string, string>>({});

  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  // Dos rutas de compra del mismo REQ (DEC-L003): cotizar a proveedor o Caja Chica.
  const [evaluacion, setEvaluacion] = useState<EvaluacionCajaChica | null>(null);
  const [ruta, setRuta] = useState<"rfq" | "caja">("rfq");

  useEffect(() => {
    if (!id) return;
    Promise.all([obtenerDetalleCompra(id), listarProveedores(), listarMonedas(), evaluarCajaChica(id)])
      .then(([detalleRes, proveedoresRes, monedasRes, evalRes]) => {
        if (evalRes.ok && evalRes.result) {
          setEvaluacion(evalRes.result);
          if (evalRes.result.motivos.length > 0) setRuta("caja");
        }
        if (detalleRes.ok && detalleRes.result) {
          setDetalle(detalleRes.result);
          const iniciales: Record<string, string> = {};
          for (const it of detalleRes.result.items) {
            if (it.cantidadPendiente > 0) iniciales[it.requestItemId] = String(it.cantidadPendiente);
          }
          setCantidades(iniciales);
        } else {
          setErrorCarga(detalleRes.message);
        }
        if (proveedoresRes.ok) setProveedores(proveedoresRes.result ?? []);
        if (monedasRes.ok) setMonedas(monedasRes.result ?? []);
      })
      .finally(() => setCargando(false));
  }, [id]);

  const itemsPendientes = useMemo(
    () => detalle?.items.filter((it) => it.cantidadPendiente > 0) ?? [],
    [detalle],
  );

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!id) return;
    const items = Object.entries(cantidades)
      .filter(([, qty]) => Number(qty) > 0)
      .map(([requestItemId, qty]) => ({ requestItemId, qty: Number(qty) }));
    if (!supplierId) {
      setError("Selecciona un proveedor.");
      return;
    }
    if (items.length === 0) {
      setError("Selecciona al menos un item con cantidad mayor a 0.");
      return;
    }
    setError(null);
    setEnviando(true);
    try {
      const res = await crearRfq(id, {
        supplierId,
        paymentCondition,
        deliveryMode,
        deliveryLocation: deliveryLocation || undefined,
        requestedLeadDays: requestedLeadDays ? Number(requestedLeadDays) : undefined,
        currencyRequested: currencyRequested || undefined,
        items,
      });
      if (!res.ok || !res.result) {
        setError(res.message);
        return;
      }
      navigate(`/compras/rfq/${res.result.rfqId}`);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div>
      <PageHeader title="Gestionar compra" backTo="/compras" backLabel="Compras" />

      {cargando ? (
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
          </CardBody>
        </Card>
      ) : errorCarga || !detalle ? (
        <Alert tone="danger">{errorCarga ?? "No se pudo cargar el requerimiento."}</Alert>
      ) : itemsPendientes.length === 0 ? (
        <Alert tone="info">Este requerimiento no tiene cantidad pendiente de compra.</Alert>
      ) : (
        <>
        <p className="-mt-4 mb-4 text-sm text-slate-500">
          {detalle.requerimiento.code} · {detalle.requerimiento.cecoNombre}
        </p>
        {evaluacion && evaluacion.motivos.length > 0 && (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <Lightbulb className="mt-0.5 size-4 shrink-0" />
            <span>
              Sugerido por Caja Chica:{" "}
              {evaluacion.motivos.map((m) => `${ETIQUETA_MOTIVO[m.codigo]} (${m.detalle})`).join(" · ")}
            </span>
          </div>
        )}
        <div role="tablist" className="mb-4 inline-flex rounded-lg border border-slate-200 bg-white p-1">
          {(
            [
              ["rfq", "Cotizar a proveedor", FileQuestion],
              ["caja", "Caja chica", Wallet],
            ] as const
          ).map(([id, label, Icon]) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={ruta === id}
              onClick={() => setRuta(id)}
              className={`flex items-center gap-2 rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                ruta === id ? "bg-brand-700 text-white" : "text-slate-600 hover:text-slate-900"
              }`}
            >
              <Icon className="size-4" />
              {label}
            </button>
          ))}
        </div>
        {ruta === "caja" && evaluacion ? (
          <FormCajaChica
            detalle={detalle}
            evaluacion={evaluacion}
            onRegistrada={() => navigate("/compras?tab=caja")}
          />
        ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-6">
          <Card>
            <CardBody>
              <div className="overflow-hidden rounded-lg border border-slate-200">
                <table className="tabla">
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                      <th className="px-4 py-2.5">SKU</th>
                      <th className="px-4 py-2.5">Producto</th>
                      <th className="px-4 py-2.5">Pendiente</th>
                      <th className="px-4 py-2.5">A cotizar</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {itemsPendientes.map((it) => (
                      <tr key={it.requestItemId}>
                        <td className="px-4 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante}</td>
                        <td className="col-flex px-4 py-2.5 text-slate-700" title={String(it.nombreProducto)}>{it.nombreProducto}</td>
                        <td className="px-4 py-2.5 text-slate-600">
                          {it.cantidadPendiente} {it.unidadMedida}
                        </td>
                        <td className="px-4 py-2.5">
                          <input
                            type="number"
                            min={0}
                            max={it.cantidadPendiente}
                            step="0.001"
                            value={cantidades[it.requestItemId] ?? "0"}
                            onChange={(e) =>
                              setCantidades((prev) => ({ ...prev, [it.requestItemId]: e.target.value }))
                            }
                            className={`${inputClass} w-28 py-1.5`}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardBody className="flex flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Proveedor" required>
                  <select
                    required
                    value={supplierId}
                    onChange={(e) => setSupplierId(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="" disabled>
                      Selecciona un proveedor…
                    </option>
                    {proveedores.map((p) => (
                      <option key={p.supplierId} value={p.supplierId}>
                        {p.legalName} — {p.ruc}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Moneda solicitada">
                  <select
                    value={currencyRequested}
                    onChange={(e) => setCurrencyRequested(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="">Sin definir</option>
                    {monedas.map((m) => (
                      <option key={m.currencyCode} value={m.currencyCode}>
                        {m.currencyCode} — {m.name}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Condición de pago">
                  <select
                    value={paymentCondition}
                    onChange={(e) => setPaymentCondition(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="CONTADO">Contado</option>
                    <option value="ANTICIPO">Anticipo</option>
                    <option value="CREDITO_30">Crédito 30 días</option>
                  </select>
                </Field>
                <Field label="Modalidad de entrega">
                  <select
                    value={deliveryMode}
                    onChange={(e) => setDeliveryMode(e.target.value)}
                    className={`${inputClass} w-full`}
                  >
                    <option value="AGENCIA">Agencia</option>
                    <option value="ALMACEN_PROVEEDOR">Almacén del proveedor</option>
                    <option value="ALMACEN_CLIENTE">Almacén del cliente</option>
                  </select>
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Lugar de entrega">
                  <input
                    value={deliveryLocation}
                    onChange={(e) => setDeliveryLocation(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
                <Field label="Días de entrega solicitados">
                  <input
                    type="number"
                    min={0}
                    value={requestedLeadDays}
                    onChange={(e) => setRequestedLeadDays(e.target.value)}
                    className={`${inputClass} w-full`}
                  />
                </Field>
              </div>

              {error && <Alert tone="danger">{error}</Alert>}

              <Button type="submit" loading={enviando} className="self-start">
                {enviando ? "Creando…" : "Crear RFQ"}
              </Button>
            </CardBody>
          </Card>
        </form>
        )}
        </>
      )}
    </div>
  );
}
