import { useEffect, useState } from "react";
import { etiquetaRequerimientos } from "../components/compras/etiquetas";
import { useParams } from "react-router-dom";
import { PackageOpen } from "lucide-react";
import { anularOrden, obtenerOrden } from "../api/compras";
import type { OrdenDetalle } from "../api/types";
import OrdenEstadoBadge from "../components/OrdenEstadoBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import BotonDescarga from "../components/BotonDescarga";
import { descargarPdfOrden } from "../api/documentos";
import { useConfirmar } from "../components/ui/Confirmacion";

export default function ComprasOrdenDetalle() {
  const confirmar = useConfirmar();
  const { id } = useParams<{ id: string }>();

  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detalle, setDetalle] = useState<OrdenDetalle | null>(null);
  const [anulando, setAnulando] = useState(false);

  function cargar() {
    if (!id) return;
    return obtenerOrden(id).then((res) => {
      if (res.ok && res.result) setDetalle(res.result);
      else setError(res.message);
    });
  }

  useEffect(() => {
    cargar()?.finally(() => setCargando(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function onAnular() {
    if (!id || !detalle) return;
    const r = await confirmar({
      titulo: `¿Anular ${detalle.orden.orderCode}?`,
      mensaje: "La anulación no se puede deshacer.",
      confirmar: "Anular orden",
      peligro: true,
      motivo: "Motivo de la anulación",
    });
    if (!r) return;
    setAnulando(true);
    try {
      const res = await anularOrden(id, r.motivo);
      if (res.ok) await cargar();
      else setError(res.message);
    } finally {
      setAnulando(false);
    }
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
          </CardBody>
        </Card>
      </div>
    );
  }
  if (error && !detalle) return <Alert tone="danger">{error}</Alert>;
  if (!detalle) return null;

  const { orden, items } = detalle;
  // Con recepciones en almacen la OC ya no se puede anular (el backend lo rechaza).
  const puedeAnular = orden.status === "EMITIDA" || orden.status === "EN_ATENCION";

  return (
    <div>
      <PageHeader
        title={<span className="font-mono">{orden.orderCode}</span>}
        subtitle={`${orden.proveedorNombre} · ${etiquetaRequerimientos(orden.requestCode)}`}
        backTo="/compras/ordenes"
        backLabel="Órdenes de compra"
        actions={
          <>
            <BotonDescarga descargar={() => descargarPdfOrden(orden.orderId, orden.orderCode)} />
            <OrdenEstadoBadge estado={orden.status} />
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <Card className="mb-4">
        <CardBody>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <div className="rotulo">Condición de pago</div>
              <div className="mt-1 text-sm text-slate-700">{orden.paymentCondition}</div>
            </div>
            <div>
              <div className="rotulo">Modalidad de entrega</div>
              <div className="mt-1 text-sm text-slate-700">{orden.deliveryMode}</div>
            </div>
            <div>
              <div className="rotulo">Lugar de entrega</div>
              <div className="mt-1 text-sm text-slate-700">{orden.deliveryLocation ?? "—"}</div>
            </div>
            <div>
              <div className="rotulo">Total</div>
              <div className="mt-1 text-sm text-slate-700">
                {orden.total} {orden.currencyCode}
              </div>
            </div>
          </div>
        </CardBody>
      </Card>

      <Card className="mb-4 overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Items ({items.length})</div>
        </CardBody>
        <div className="overflow-hidden">
          <table className="tabla">
            <thead>
              <tr className="border-y border-slate-200 bg-slate-50/70 text-[11px] font-medium uppercase tracking-[0.06em] text-slate-500">
                <th className="px-5 py-2.5">SKU</th>
                <th className="px-5 py-2.5">Producto</th>
                <th className="px-5 py-2.5">Cantidad</th>
                <th className="px-5 py-2.5">Costo unitario</th>
                <th className="px-5 py-2.5">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((it) => (
                <tr key={it.orderItemId}>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante}</td>
                  <td className="col-flex px-5 py-2.5 text-slate-700" title={String(it.description)}>{it.description}</td>
                  <td className="px-5 py-2.5 text-slate-600">
                    {it.qtyOrdered} {it.unidadMedida ?? ""}
                  </td>
                  <td className="px-5 py-2.5 text-slate-600">{it.unitCost}</td>
                  <td className="px-5 py-2.5 text-slate-600">{it.lineTotal}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="flex flex-wrap gap-2">
        {orden.status !== "ANULADA" && (
          <Button href={`/almacen/ordenes/${orden.orderId}/recepcion`} icon={<PackageOpen className="size-4" />}>
            {orden.status === "RECEPCION_COMPLETA" ? "Ver recepciones" : "Registrar recepción"}
          </Button>
        )}
        {puedeAnular && (
          <Button variant="secondary" loading={anulando} onClick={onAnular}>
            {anulando ? "Anulando…" : "Anular orden"}
          </Button>
        )}
      </div>
    </div>
  );
}
