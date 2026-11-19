import { useId, useState } from "react";
import { useDatos } from "../../api/useDatos";
import { Link } from "react-router-dom";
import { AlertTriangle } from "lucide-react";
import { listarDiferencias, recibirDevolucion } from "../../api/almacen";
import type { DiferenciasAlmacen as Datos } from "../../api/types";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import { inputClass } from "../ui/Field";
import { SkeletonTable } from "../ui/Skeleton";
import { fmtCantidad, fmtFecha } from "../formato";

const th = "px-5 py-3";
const ENLACE = /^https?:\/\/\S+$/i;

/**
 * Pestana Diferencias del almacen (V38.7 aiRender 'diferencias', ALM-03): lo
 * observado en destino por resolver (se resuelve en la nota) y las devoluciones,
 * que el almacen central recibe en cuarentena con enlace de evidencia.
 */
export default function DiferenciasAlmacen() {
  const diferencias = useDatos("almacen-diferencias", listarDiferencias);
  const datos = diferencias.datos;
  const setDatos = diferencias.mutar;
  const [errorAccion, setError] = useState<string | null>(null);
  const error = errorAccion ?? diferencias.error;
  const [exito, setExito] = useState<string | null>(null);

  if (!datos && !error) {
    return (
      <Card className="overflow-hidden">
        <SkeletonTable cols={6} />
      </Card>
    );
  }
  const observados = datos?.observados ?? [];
  const devoluciones = datos?.devoluciones ?? [];

  return (
    <>
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {exito && <Alert tone="good" className="mb-4">{exito}</Alert>}
      <Card className="mb-4 overflow-hidden">
        {observados.length === 0 && devoluciones.length === 0 ? (
          <EmptyState icon={AlertTriangle} title="No hay diferencias de recepción" />
        ) : (
          <table className="tabla">
            <thead>
              <tr className="cabecera-tabla border-b border-slate-200">
                <th className={th}>Empresa</th>
                <th className={th}>Nota / retorno</th>
                <th className={th}>Producto</th>
                <th className={th}>Cantidad</th>
                <th className={th}>Estado</th>
                <th className={th} />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {observados.map((o) => (
                <tr key={`${o.shipmentId}-${o.issueItemId}`}>
                  <td className="px-5 py-3 text-xs text-slate-500">{o.empresaCode}</td>
                  <td className="px-5 py-3 font-mono text-xs text-slate-700">{o.notaCodigo}</td>
                  <td className="col-flex px-5 py-3 text-slate-700" title={o.nombreProducto}>
                    <span className="font-mono text-xs text-slate-500">{o.sku}</span> {o.nombreProducto}
                  </td>
                  <td className="px-5 py-3 text-slate-700">{fmtCantidad(o.observado)}</td>
                  <td className="px-5 py-3">
                    <Badge tone="rojo">Observado</Badge>
                  </td>
                  <td className="px-5 py-3 text-right">
                    <Link
                      to={`/almacen/notas/${o.inventoryNoteId}`}
                      className="text-sm font-medium text-brand-600 hover:text-brand-700"
                    >
                      Resolver
                    </Link>
                  </td>
                </tr>
              ))}
              {devoluciones.map((d) => (
                <FilaDevolucion
                  key={d.resolutionId}
                  devolucion={d}
                  onRecibida={(nuevos) => {
                    setDatos(nuevos);
                    setError(null);
                    setExito("Retorno recibido: queda en cuarentena en el almacén central.");
                  }}
                  onError={(m) => {
                    setExito(null);
                    setError(m);
                  }}
                />
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

function FilaDevolucion({
  devolucion: d,
  onRecibida,
  onError,
}: {
  devolucion: Datos["devoluciones"][number];
  onRecibida: (d: Datos) => void;
  onError: (m: string) => void;
}) {
  const [abierto, setAbierto] = useState(false);
  const [evidencia, setEvidencia] = useState("");
  const [errorCampo, setErrorCampo] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const id = useId();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!ENLACE.test(evidencia.trim())) {
      setErrorCampo("Registra evidencia válida (enlace http o https).");
      return;
    }
    setErrorCampo(null);
    setOcupado(true);
    try {
      const res = await recibirDevolucion(d.resolutionId, evidencia.trim());
      if (res.ok && res.result) onRecibida(res.result);
      else onError(res.message);
    } finally {
      setOcupado(false);
    }
  }

  return (
    <>
      <tr>
        <td className="px-5 py-3 text-xs text-slate-500">{d.empresaCode}</td>
        <td className="px-5 py-3 font-mono text-xs text-slate-700">{d.notaCodigo}</td>
        <td className="col-flex px-5 py-3 text-slate-700" title={d.nombreProducto}>
          <span className="font-mono text-xs text-slate-500">{d.sku}</span> {d.nombreProducto}
        </td>
        <td className="px-5 py-3 text-slate-700">{fmtCantidad(d.qty)}</td>
        <td className="px-5 py-3">
          {d.estado === "EN_TRANSITO" ? (
            <Badge tone="amarillo">Retorno en camino</Badge>
          ) : (
            <span title={d.recibidaEn ? fmtFecha(d.recibidaEn) : undefined}>
              <Badge tone="gris">Retorno en cuarentena</Badge>
            </span>
          )}
        </td>
        <td className="px-5 py-3 text-right">
          {d.estado === "EN_TRANSITO" && !abierto && (
            <Button size="sm" variant="secondary" onClick={() => setAbierto(true)}>
              Recibir retorno
            </Button>
          )}
        </td>
      </tr>
      {abierto && (
        <tr>
          <td colSpan={6} className="px-5 pb-4">
            <Card>
              <CardBody>
                <form onSubmit={onSubmit} aria-label={`Recibir retorno de ${d.sku}`} className="flex flex-wrap items-end gap-3" noValidate>
                  <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                    <label htmlFor={id} className="text-[0.8125rem] font-medium text-slate-700">
                      Evidencia de recepción
                    </label>
                    <input
                      id={id}
                      type="url"
                      inputMode="url"
                      required
                      maxLength={500}
                      autoComplete="off"
                      autoCorrect="off"
                      autoCapitalize="none"
                      spellCheck={false}
                      placeholder="https://"
                      value={evidencia}
                      onChange={(e) => {
                        setEvidencia(e.target.value);
                        setErrorCampo(null);
                      }}
                      className={`${inputClass} w-full`}
                    />
                  </div>
                  <Button type="submit" size="sm" loading={ocupado}>
                    Confirmar retorno físico
                  </Button>
                  <Button type="button" size="sm" variant="secondary" onClick={() => setAbierto(false)}>
                    Cancelar
                  </Button>
                  {errorCampo && (
                    <p role="alert" className="w-full text-sm text-rose-700">
                      {errorCampo}
                    </p>
                  )}
                </form>
              </CardBody>
            </Card>
          </td>
        </tr>
      )}
    </>
  );
}
