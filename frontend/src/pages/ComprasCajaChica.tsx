import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Lightbulb } from "lucide-react";
import { ETIQUETA_MOTIVO, evaluarCajaChica } from "../api/cajachica";
import { obtenerDetalleCompra } from "../api/compras";
import type { ComprasDetalle, EvaluacionCajaChica } from "../api/types";
import FormCajaChica from "../components/cajachica/FormCajaChica";
import Alert from "../components/ui/Alert";
import { Card, CardBody } from "../components/ui/Card";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

/**
 * Compra por Caja Chica de las necesidades de un requerimiento (DEC-L026,
 * DEC-L039). La compra normal ya no se arma por requerimiento: va por el
 * consolidado del SKU.
 */
export default function ComprasCajaChica() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [detalle, setDetalle] = useState<ComprasDetalle | null>(null);
  const [evaluacion, setEvaluacion] = useState<EvaluacionCajaChica | null>(null);

  useEffect(() => {
    if (!id) return;
    Promise.all([obtenerDetalleCompra(id), evaluarCajaChica(id)])
      .then(([detalleRes, evalRes]) => {
        if (detalleRes.ok && detalleRes.result) setDetalle(detalleRes.result);
        else setError(detalleRes.message);
        if (evalRes.ok && evalRes.result) setEvaluacion(evalRes.result);
        else if (!evalRes.ok) setError(evalRes.message);
      })
      .finally(() => setCargando(false));
  }, [id]);

  const pendientes = detalle?.items.filter((it) => it.cantidadPendiente > 0) ?? [];

  return (
    <div>
      <PageHeader title="Compra por Caja Chica" backTo="/compras" backLabel="Compras" />

      {cargando ? (
        <Card>
          <CardBody className="flex flex-col gap-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
          </CardBody>
        </Card>
      ) : error || !detalle || !evaluacion ? (
        <Alert tone="danger">{error ?? "No se pudo cargar el requerimiento."}</Alert>
      ) : pendientes.length === 0 ? (
        <Alert tone="info">Este requerimiento no tiene necesidades pendientes por Caja Chica.</Alert>
      ) : (
        <>
          <p className="-mt-4 mb-4 text-sm text-slate-500">
            {detalle.requerimiento.code} · {detalle.requerimiento.cecoNombre}
          </p>
          {evaluacion.motivos.length > 0 && (
            <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              <Lightbulb className="mt-0.5 size-4 shrink-0" />
              <span>{evaluacion.motivos.map((m) => `${ETIQUETA_MOTIVO[m.codigo]} (${m.detalle})`).join(" · ")}</span>
            </div>
          )}
          <FormCajaChica detalle={detalle} evaluacion={evaluacion} onRegistrada={() => navigate("/compras?tab=caja")} />
        </>
      )}
    </div>
  );
}
