import { Link } from "react-router-dom";
import { ExternalLink, FileText } from "lucide-react";
import { documentosOrden } from "../../api/compras";
import type { DocumentoOrden } from "../../api/types";
import { useDatos } from "../../api/useDatos";
import { Card, CardBody, CardHeader } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";

const GRUPO: Record<DocumentoOrden["tipo"], string> = {
  PAGO: "Pago",
  GUIA: "Guía",
  RECEPCION: "Recepción",
  NOTA_INVENTARIO: "Nota de inventario",
};

function fecha(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("es-PE", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—";
}

/**
 * Documentos relacionados de la OC (DEC-L089): constancias y factura del pago,
 * guías y recepciones del proveedor y notas de inventario de sus requerimientos.
 */
export default function DocumentosOrden({ orderId, version }: { orderId: string; version: string }) {
  // version: cambia al pagar, recibir o liberar, para volver a pedir la lista.
  const docs = useDatos(`oc-docs:${orderId}:${version}`, () => documentosOrden(orderId), { conservar: true });

  return (
    <Card className="mb-4 overflow-hidden">
      <CardHeader className="flex items-center gap-2">
        <FileText className="size-4.5 text-slate-500" />
        <h2 className="font-semibold text-slate-900">Documentos relacionados</h2>
      </CardHeader>
      {!docs.datos ? (
        <CardBody className="flex flex-col gap-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </CardBody>
      ) : docs.datos.length === 0 ? (
        <CardBody>
          <p className="text-sm text-slate-500">Todavía no hay pagos, guías, recepciones ni notas de inventario.</p>
        </CardBody>
      ) : (
        <table className="tabla">
          <thead>
            <tr className="border-y border-slate-200 cabecera-tabla">
              <th className="px-5 py-2.5">Tipo</th>
              <th className="px-5 py-2.5">Documento</th>
              <th className="px-5 py-2.5">Detalle</th>
              <th className="px-5 py-2.5">Fecha</th>
              <th className="px-5 py-2.5" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {docs.datos.map((d, i) => (
              <tr key={`${d.tipo}-${d.titulo}-${i}`}>
                <td className="px-5 py-2.5 text-xs text-slate-500">{GRUPO[d.tipo]}</td>
                <td className="px-5 py-2.5 text-sm font-medium text-slate-800">{d.titulo}</td>
                <td className="col-flex px-5 py-2.5 text-sm text-slate-600" title={d.detalle ?? ""}>
                  {d.detalle ?? "—"}
                </td>
                <td className="px-5 py-2.5 text-sm text-slate-600">{fecha(d.fecha)}</td>
                <td className="px-5 py-2.5 text-right text-sm">
                  <span className="inline-flex gap-3">
                    {d.url && (
                      <a href={d.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-600 hover:text-brand-700">
                        <ExternalLink className="size-3.5" />
                        Archivo
                      </a>
                    )}
                    {d.ruta && (
                      <Link to={d.ruta} className="font-medium text-brand-600 hover:text-brand-700">
                        Ver
                      </Link>
                    )}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
