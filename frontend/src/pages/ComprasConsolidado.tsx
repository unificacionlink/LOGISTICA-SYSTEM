import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FileText, Trophy } from "lucide-react";
import { adjudicar, anularConsolidado, obtenerConsolidado } from "../api/compras";
import type { ConsolidadoDetalle, Oferta } from "../api/types";
import { cantidad, ConsolidadoEstadoBadge, dinero } from "../components/compras/etiquetas";
import PrioridadBadge from "../components/PrioridadBadge";
import RfqEstadoBadge from "../components/RfqEstadoBadge";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";

const CONDICION: Record<string, string> = {
  CONTADO: "Contado",
  ANTICIPO: "Anticipo",
  CREDITO_30: "Crédito 30 días",
};

const cabecera =
  "border-y border-slate-200 cabecera-tabla";

/** Consolidado de un SKU (DEC-L039): de donde viene, que ofrecio cada proveedor y a quien se adjudica. */
export default function ComprasConsolidado() {
  const confirmar = useConfirmar();
  const { id } = useParams<{ id: string }>();
  const [detalle, setDetalle] = useState<ConsolidadoDetalle | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [procesando, setProcesando] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    obtenerConsolidado(id)
      .then((res) => {
        if (res.ok && res.result) setDetalle(res.result);
        else setError(res.message);
      })
      .finally(() => setCargando(false));
  }, [id]);

  async function onAdjudicar(o: Oferta) {
    if (!id || !o.quotationItemId) return;
    setProcesando(o.quotationItemId);
    try {
      const res = await adjudicar(id, o.quotationItemId);
      if (res.ok && res.result) {
        setDetalle(res.result);
        setError(null);
      } else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  async function onAnular() {
    if (!id) return;
    const r = await confirmar({
      titulo: "¿Anular el consolidado?",
      mensaje: "Sus faltantes vuelven a Necesidades para decidir la ruta (Caja Chica o una nueva compra).",
      confirmar: "Anular",
      peligro: true,
      motivo: "Motivo de la anulación",
    });
    if (!r) return;
    setProcesando("__anular__");
    try {
      const res = await anularConsolidado(id, r.motivo);
      if (res.ok && res.result) setDetalle(res.result);
      else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-72" />
        <Card className="mb-6 overflow-hidden">
          <SkeletonTable rows={3} cols={4} />
        </Card>
        <Card className="overflow-hidden">
          <SkeletonTable rows={3} cols={6} />
        </Card>
      </div>
    );
  }
  if (!detalle) return <Alert tone="danger">{error ?? "Consolidado no encontrado."}</Alert>;

  const { consolidado: c, ofertas, ultimoCosto } = detalle;
  const adjudicable = c.status === "EN_COTIZACION" || c.status === "ADJUDICADA";
  const anulable = adjudicable;

  return (
    <div>
      <PageHeader
        title={c.nombreProducto}
        subtitle={`${c.codigoVariante ?? c.skuBase} · ${c.empresaCode} · ${c.consolidationCode} · ${cantidad(c.totalQty)} ${c.unidadMedida}`}
        backTo="/compras"
        backLabel="Compras"
        actions={
          <>
            <ConsolidadoEstadoBadge estado={c.status} />
            {detalle.orderId && (
              <Button href={`/compras/ordenes/${detalle.orderId}`} size="sm" variant="secondary">
                {detalle.orderCode}
              </Button>
            )}
            {anulable && (
              <MenuAcciones
                acciones={[
                  { label: "Anular consolidado", onClick: onAnular, peligro: true, disabled: procesando === "__anular__" },
                ]}
              />
            )}
          </>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <Card className="mb-6 overflow-hidden">
        <CardBody className="pb-3">
          <div className="rotulo">Origen ({c.origenes.length})</div>
        </CardBody>
        <table className="tabla">
          <thead>
            <tr className={cabecera}>
              <th className="px-5 py-2.5">CECO</th>
              <th className="px-5 py-2.5">Requerimiento</th>
              <th className="px-5 py-2.5">Prioridad</th>
              <th className="px-5 py-2.5 text-right">Cantidad</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {c.origenes.map((o) => (
              <tr key={o.needId}>
                <td className="col-flex px-5 py-2.5 text-sm" title={`${o.cecoCode} ${o.cecoNombre}`}>
                  <span className="font-mono font-medium text-slate-700">{o.cecoCode}</span>{" "}
                  <span className="text-slate-500">{o.cecoNombre}</span>
                </td>
                <td className="px-5 py-2.5">
                  <Link to={`/requerimientos/${o.requestId}`} className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700">
                    {o.requestCode}
                  </Link>
                </td>
                <td className="px-5 py-2.5">
                  <PrioridadBadge prioridad={o.priority} />
                </td>
                <td className="px-5 py-2.5 text-right text-slate-700">
                  {cantidad(o.qty)} {c.unidadMedida}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card className="overflow-hidden">
        <CardBody className="flex flex-wrap items-baseline justify-between gap-2 pb-3">
          <div className="rotulo">Ofertas ({ofertas.filter((o) => o.unitCost != null).length})</div>
          {ultimoCosto != null && (
            <span className="text-xs text-slate-500">Última compra: {dinero(ultimoCosto)} por unidad</span>
          )}
        </CardBody>
        {ofertas.length === 0 ? (
          <p className="px-5 pb-5 text-sm text-slate-500">Todavía no se preparó la solicitud de cotización.</p>
        ) : (
          <table className="tabla">
            <thead>
              <tr className={cabecera}>
                <th className="px-5 py-2.5">Proveedor</th>
                <th className="px-5 py-2.5">Costo unit.</th>
                <th className="px-5 py-2.5 col-sec">Total</th>
                <th className="px-5 py-2.5 col-sec">Condiciones</th>
                <th className="px-5 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ofertas.map((o, i) => (
                <tr key={`${o.rfqId}-${o.quotationItemId ?? i}`} className={o.adjudicada ? "bg-emerald-50/50" : ""}>
                  <td className="col-flex px-5 py-2.5" title={o.proveedorNombre}>
                    <p className="text-sm text-slate-700">{o.proveedorNombre}</p>
                    <p className="flex items-center gap-1.5 text-xs text-slate-400">
                      <Link to={`/compras/rfq/${o.rfqId}`} className="font-mono hover:text-brand-600">
                        {o.rfqCode}
                      </Link>
                      {o.supplierQuoteNumber && <span>· N° {o.supplierQuoteNumber}</span>}
                      {o.documentoUrl && (
                        <a href={o.documentoUrl} target="_blank" rel="noreferrer" title="Ver cotización" className="hover:text-brand-600">
                          <FileText className="size-3.5" />
                        </a>
                      )}
                    </p>
                  </td>
                  {o.unitCost == null ? (
                    <td colSpan={3} className="px-5 py-2.5">
                      <RfqEstadoBadge estado={o.rfqStatus} />
                      <span className="ml-2 text-xs text-slate-400">sin respuesta</span>
                    </td>
                  ) : (
                    <>
                      <td className="whitespace-nowrap px-5 py-2.5 font-medium text-slate-800">
                        {dinero(o.unitCost, o.currencyCode)}
                        {o.cotizacionStatus === "DESCARTADA" && (
                          <Badge tone="rojo" className="ml-2">
                            Descartada
                          </Badge>
                        )}
                      </td>
                      <td className="col-sec whitespace-nowrap px-5 py-2.5 text-slate-600">
                        {dinero(o.lineTotal, o.currencyCode)}
                        <span className="block text-xs text-slate-400">
                          {cantidad(o.qty)} {c.unidadMedida}
                        </span>
                      </td>
                      <td className="col-sec px-5 py-2.5 text-xs text-slate-500">
                        {[
                          o.paymentCondition ? (CONDICION[o.paymentCondition] ?? o.paymentCondition) : null,
                          o.leadDays != null ? `${o.leadDays} días` : null,
                          [o.offeredBrand, o.offeredModel].filter(Boolean).join(" ") || null,
                        ]
                          .filter(Boolean)
                          .join(" · ") || "—"}
                      </td>
                    </>
                  )}
                  <td className="whitespace-nowrap px-5 py-2.5 text-right">
                    {o.adjudicada ? (
                      <Badge tone="verde">
                        <Trophy className="size-3" />
                        Adjudicada
                      </Badge>
                    ) : (
                      adjudicable &&
                      o.quotationItemId &&
                      o.cotizacionStatus !== "DESCARTADA" && (
                        <Button
                          size="sm"
                          variant={c.status === "ADJUDICADA" ? "secondary" : "primary"}
                          loading={procesando === o.quotationItemId}
                          disabled={procesando !== null}
                          onClick={() => onAdjudicar(o)}
                        >
                          Adjudicar
                        </Button>
                      )
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}
