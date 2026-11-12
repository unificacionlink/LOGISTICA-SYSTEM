import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { FileText } from "lucide-react";
import { obtenerFormularioCajaChica } from "../api/cajachica";
import type { CompraCajaChica, FormularioCajaChica } from "../api/types";
import EstadoCompra from "../components/cajachica/EstadoCompra";
import FormCajaChica from "../components/cajachica/FormCajaChica";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";
import { useRealtime } from "../realtime/useRealtime";

function fmtDia(iso: string): string {
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

/**
 * Compra con la caja chica del proyecto (DEC-L054): Logistica autoriza la ruta
 * en Necesidades y el solicitante registra aqui lo que compro con su
 * comprobante; si Logistica la observa, la corrige aqui mismo.
 */
export default function ComprasCajaChica() {
  const { id } = useParams<{ id: string }>();
  const { onCajaChica } = useRealtime();
  const [formulario, setFormulario] = useState<FormularioCajaChica | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [corrigiendo, setCorrigiendo] = useState<CompraCajaChica | null>(null);

  const cargar = useCallback(() => {
    if (!id) return;
    obtenerFormularioCajaChica(id).then((res) => {
      if (res.ok && res.result) setFormulario(res.result);
      else setError(res.message);
    });
  }, [id]);

  useEffect(cargar, [cargar]);
  useEffect(() => onCajaChica((a) => a.requestId === id && cargar()), [id, onCajaChica, cargar]);

  function onGuardada(_c: CompraCajaChica, mensaje: string) {
    setCorrigiendo(null);
    setAviso(mensaje);
    cargar();
  }

  const observadas = formulario?.compras.filter((c) => c.estado === "OBSERVADO") ?? [];

  return (
    <div>
      <PageHeader title="Compra con caja chica" backTo={`/requerimientos/${id}`} backLabel="Requerimiento" />

      {!formulario && !error ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-4 w-48" />
          <Card>
            <CardBody className="flex flex-col gap-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-24 w-full" />
            </CardBody>
          </Card>
        </div>
      ) : error || !formulario ? (
        <Alert tone="danger">{error ?? "No se pudo cargar el requerimiento."}</Alert>
      ) : (
        <div className="flex flex-col gap-6">
          <p className="-mt-4 text-sm text-slate-500">
            {formulario.requestCode}
            {formulario.cecoNombre ? ` · ${formulario.cecoNombre}` : ""}
          </p>
          {aviso && (
            <Alert tone="good">{aviso}</Alert>
          )}

          {corrigiendo ? (
            <>
              <Alert tone="danger">Observada: {corrigiendo.observacion}</Alert>
              <FormCajaChica
                formulario={formulario}
                corrige={corrigiendo}
                onGuardada={onGuardada}
                onCancelar={() => setCorrigiendo(null)}
              />
            </>
          ) : (
            <>
              {observadas.map((c) => (
                <Alert key={c.id} tone="danger">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span>
                      {c.codigo} observada: {c.observacion}
                    </span>
                    <Button size="sm" onClick={() => setCorrigiendo(c)}>
                      Corregir
                    </Button>
                  </div>
                </Alert>
              ))}
              {formulario.puedeRegistrar ? (
                <FormCajaChica key={formulario.compras.length} formulario={formulario} onGuardada={onGuardada} />
              ) : (
                formulario.compras.length === 0 && (
                  <Alert tone="info">Logística aún no autoriza comprar con caja chica para este requerimiento.</Alert>
                )
              )}
            </>
          )}

          {formulario.compras.length > 0 && !corrigiendo && (
            <Card className="overflow-hidden">
              <CardBody className="pb-3">
                <div className="rotulo">Compras registradas</div>
              </CardBody>
              <ul className="divide-y divide-slate-100 border-t border-slate-200">
                {formulario.compras.map((c) => (
                  <li key={c.id} className="flex flex-col gap-1.5 px-5 py-3">
                    <div className="flex flex-wrap items-center gap-3">
                      <span className="font-mono text-xs text-slate-700">{c.codigo}</span>
                      <span className="min-w-0 flex-1 truncate text-sm text-slate-700">{c.proveedor}</span>
                      <span className="tabular-nums text-sm text-slate-800">
                        {c.moneda} {c.monto.toFixed(2)}
                      </span>
                      <EstadoCompra estado={c.estado} />
                    </div>
                    <p className="text-xs text-slate-500">
                      {c.comprobante} · {fmtDia(c.fechaCompra)} ·{" "}
                      {c.items.map((i) => `${i.cantidad} × ${i.producto}`).join(", ")}
                    </p>
                    {c.documentoUrl && (
                      <a
                        href={c.documentoUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 self-start text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        <FileText className="size-3.5" />
                        Comprobante
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
