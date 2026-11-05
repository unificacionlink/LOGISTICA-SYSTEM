import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PackageCheck } from "lucide-react";
import { crearNotaInventario, obtenerDespacho } from "../api/almacen";
import type { DespachoRequerimiento, EstadoBien } from "../api/types";
import { EnvioBadge, NotaBadge, fmtCantidad, fmtFecha } from "../components/AlmacenBadges";
import EstadoBadge from "../components/EstadoBadge";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

const labelClass = "rotulo";

export default function AlmacenDespacho() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [datos, setDatos] = useState<DespachoRequerimiento | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const [cantidades, setCantidades] = useState<Record<string, string>>({});
  const [bultos, setBultos] = useState("");
  const [estadoBien, setEstadoBien] = useState<EstadoBien>("NUEVO");
  const [comentarios, setComentarios] = useState("");

  useEffect(() => {
    if (!id) return;
    obtenerDespacho(id)
      .then((res) => {
        if (res.ok && res.result) {
          setDatos(res.result);
          // Por defecto se propone despachar todo lo reservado.
          const iniciales: Record<string, string> = {};
          for (const it of res.result.items) {
            if (it.qtyPorDespachar > 0) iniciales[it.requestItemId] = String(Number(it.qtyPorDespachar));
          }
          setCantidades(iniciales);
        } else {
          setError(res.message);
        }
      })
      .finally(() => setCargando(false));
  }, [id]);

  async function onEmitir(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !datos) return;
    setError(null);

    const items: { requestItemId: string; qty: number }[] = [];
    for (const it of datos.items) {
      const valor = cantidades[it.requestItemId];
      if (valor === undefined || valor === "") continue;
      const qty = Number(valor);
      if (Number.isNaN(qty) || qty < 0) {
        setError(`Cantidad inválida para ${it.codigoVariante ?? it.skuBase}.`);
        return;
      }
      if (qty > it.qtyPorDespachar) {
        setError(`${it.codigoVariante ?? it.skuBase}: no puedes despachar más de lo reservado (${fmtCantidad(it.qtyPorDespachar)}).`);
        return;
      }
      if (qty > 0) items.push({ requestItemId: it.requestItemId, qty });
    }
    if (items.length === 0) {
      setError("Indica al menos una cantidad a despachar.");
      return;
    }

    setGuardando(true);
    try {
      const res = await crearNotaInventario(id, {
        items,
        packageCount: bultos ? Number(bultos) : undefined,
        itemState: estadoBien,
        comments: comentarios || undefined,
      });
      if (res.ok && res.result) navigate(`/almacen/notas/${res.result.nota.inventoryNoteId}`);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-32" />
        <Skeleton className="mb-6 h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (error && !datos) return <Alert tone="danger">{error}</Alert>;
  if (!datos) return null;

  const { requerimiento, items, notas } = datos;
  const hayReservado = items.some((i) => i.qtyPorDespachar > 0);

  return (
    <div>
      <PageHeader
        title={
          <span>
            Despacho · <span className="font-mono">{requerimiento.code}</span>
          </span>
        }
        subtitle={`${requerimiento.cecoNombre ?? "—"} · ${requerimiento.requesterNombre ?? "—"}`}
        backTo="/almacen?tab=despachar"
        backLabel="Almacén"
        actions={<EstadoBadge estado={requerimiento.status} />}
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      <form onSubmit={onEmitir}>
        <Card className="mb-4 overflow-hidden">
          <CardBody className="pb-3">
            <div className={labelClass}>Items del requerimiento</div>
          </CardBody>
          <div className="overflow-hidden">
            <table className="tabla">
              <thead>
                <tr className="border-y border-slate-200 cabecera-tabla">
                  <th className="px-5 py-2.5">SKU</th>
                  <th className="px-5 py-2.5">Producto</th>
                  <th className="px-5 py-2.5">Solicitado</th>
                  <th className="px-5 py-2.5">Despachado</th>
                  <th className="col-sec px-5 py-2.5">Entregado</th>
                  <th className="px-5 py-2.5">Reservado</th>
                  <th className="px-5 py-2.5">A despachar</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((it) => (
                  <tr key={it.requestItemId}>
                    <td className="px-5 py-2.5 font-mono text-xs text-slate-600">{it.codigoVariante ?? it.skuBase}</td>
                    <td className="col-flex px-5 py-2.5 text-slate-700" title={it.nombreProducto}>
                      {it.nombreProducto}
                      {it.atributo ? <span className="text-slate-400"> · {it.atributo}</span> : null}
                    </td>
                    <td className="px-5 py-2.5 text-slate-600">
                      {fmtCantidad(it.qtyRequested)} {it.unidadMedida}
                    </td>
                    <td className="px-5 py-2.5 text-slate-600">{fmtCantidad(it.qtyDespachado)}</td>
                    <td className="col-sec px-5 py-2.5 text-slate-600">{fmtCantidad(it.qtyEntregado)}</td>
                    <td className="px-5 py-2.5 font-medium text-slate-700">{fmtCantidad(it.qtyPorDespachar)}</td>
                    <td className="px-5 py-2.5">
                      {it.qtyPorDespachar > 0 ? (
                        <input
                          type="number"
                          min="0"
                          step="any"
                          max={it.qtyPorDespachar}
                          value={cantidades[it.requestItemId] ?? ""}
                          onChange={(e) => setCantidades({ ...cantidades, [it.requestItemId]: e.target.value })}
                          className={`${inputClass} w-24 py-1.5`}
                          aria-label={`Cantidad a despachar de ${it.codigoVariante ?? it.skuBase}`}
                        />
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        {hayReservado ? (
          <Card className="mb-4">
            <CardBody className="grid gap-4 sm:grid-cols-[140px_200px_1fr]">
              <Field label="Bultos">
                <input
                  type="number"
                  min="1"
                  value={bultos}
                  onChange={(e) => setBultos(e.target.value)}
                  className={`${inputClass} w-full`}
                />
              </Field>
              <Field label="Estado del bien">
                <select
                  value={estadoBien}
                  onChange={(e) => setEstadoBien(e.target.value as EstadoBien)}
                  className={`${inputClass} w-full`}
                >
                  <option value="NUEVO">Nuevo</option>
                  <option value="USADO">Usado</option>
                  <option value="REACONDICIONADO">Reacondicionado</option>
                  <option value="OTRO">Otro</option>
                </select>
              </Field>
              <Field label="Comentarios / condiciones">
                <input
                  value={comentarios}
                  onChange={(e) => setComentarios(e.target.value)}
                  placeholder="Opcional"
                  className={`${inputClass} w-full`}
                />
              </Field>
              <div className="sm:col-span-3">
                <Button type="submit" loading={guardando} icon={<PackageCheck className="size-4" />}>
                  {guardando ? "Emitiendo…" : "Emitir Nota de Inventario"}
                </Button>
              </div>
            </CardBody>
          </Card>
        ) : (
          <Alert tone="info" className="mb-4">
            No hay stock reservado pendiente de despacho para este requerimiento.
          </Alert>
        )}
      </form>

      {notas.length > 0 && (
        <Card className="overflow-hidden">
          <CardBody className="pb-3">
            <div className={labelClass}>Notas de inventario emitidas ({notas.length})</div>
          </CardBody>
          <div className="divide-y divide-slate-100 border-t border-slate-200">
            {notas.map((n) => (
              <Link
                key={n.inventoryNoteId}
                to={`/almacen/notas/${n.inventoryNoteId}`}
                className="flex flex-wrap items-center gap-3 px-5 py-3 text-sm hover:bg-slate-50"
              >
                <span className="font-mono text-xs text-slate-700">{n.inventoryNoteCode}</span>
                <NotaBadge estado={n.status} />
                {n.envio && <EnvioBadge estado={n.envio.status} />}
                <span className="text-xs text-slate-500">
                  {fmtCantidad(n.totalItemQty)} u. · {fmtFecha(n.issuedAt)}
                </span>
              </Link>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
