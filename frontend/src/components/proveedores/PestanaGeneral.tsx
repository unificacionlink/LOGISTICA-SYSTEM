import { useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { obtenerHistorialProveedor } from "../../api/proveedores";
import type { FichaProveedor, HistorialProveedor } from "../../api/types";
import { fmtFecha } from "../formato";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import { Skeleton } from "../ui/Skeleton";
import { ENTREGA } from "./datosProveedor";
import EquivalenciasProveedor from "./EquivalenciasProveedor";
import { ACCION_HISTORIAL } from "./estados";

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{etiqueta}</dt>
      <dd className="truncate text-sm text-slate-800">{children || "—"}</dd>
    </div>
  );
}

function Bloque({ titulo, accion, children }: { titulo: string; accion?: ReactNode; children: ReactNode }) {
  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3">
        <p className="text-sm font-semibold text-slate-800">{titulo}</p>
        {accion}
      </div>
      <div className="px-5 py-4">{children}</div>
    </Card>
  );
}

function Historial({ supplierId }: { supplierId: string }) {
  const [abierto, setAbierto] = useState(false);
  const [items, setItems] = useState<HistorialProveedor[] | null>(null);

  function alternar() {
    setAbierto((v) => !v);
    if (!items) {
      obtenerHistorialProveedor(supplierId).then((res) => setItems(res.ok ? (res.result ?? []) : []));
    }
  }

  return (
    <Card className="overflow-hidden">
      <button
        type="button"
        aria-expanded={abierto}
        onClick={alternar}
        className="flex w-full items-center justify-between px-5 py-3 text-left text-sm font-semibold text-slate-800 hover:bg-slate-50"
      >
        Historial de decisiones
        <ChevronDown className={`size-4 text-slate-400 transition-transform ${abierto ? "rotate-180" : ""}`} />
      </button>
      {abierto && (
        <div className="border-t border-slate-100 px-5 py-3">
          {!items ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-5" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <p className="text-sm text-slate-500">Sin movimientos.</p>
          ) : (
            <ol className="space-y-2">
              {items.map((h, i) => (
                <li key={i} className="grid grid-cols-[8.5rem_1fr] gap-3 text-sm">
                  <span className="tabular-nums text-slate-500">{fmtFecha(h.fecha)}</span>
                  <span className="whitespace-normal text-slate-700">
                    <span className="font-medium text-slate-900">{ACCION_HISTORIAL[h.accion] ?? h.accion}</span>
                    {h.usuario && <span className="text-slate-500"> · {h.usuario}</span>}
                    {h.motivo && <span className="block text-slate-600">{h.motivo}</span>}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </Card>
  );
}

/** Datos, condiciones, clasificacion y productos del proveedor (DEC-L069). */
export default function PestanaGeneral({
  ficha,
  onEditar,
  onCategorias,
  onProductos,
}: {
  ficha: FichaProveedor;
  onEditar: () => void;
  onCategorias: () => void;
  onProductos: () => void;
}) {
  const p = ficha.proveedor;
  const editable = p.status !== "RECHAZADO";
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="space-y-4">
        <Bloque
          titulo="Datos y condiciones"
          accion={
            editable && (
              <Button size="sm" variant="ghost" onClick={onEditar}>
                Editar
              </Button>
            )
          }
        >
          <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3">
            <Dato etiqueta="Nombre comercial">{p.tradeName}</Dato>
            <Dato etiqueta="Tipo">{p.supplierType === "CRITICO" ? "Crítico" : "No crítico"}</Dato>
            <Dato etiqueta="Ciudad">{p.city}</Dato>
            <Dato etiqueta="Responsable">
              {p.contactName}
              {p.contactRole ? ` · ${p.contactRole}` : ""}
            </Dato>
            <Dato etiqueta="Correo">{p.email}</Dato>
            <Dato etiqueta="Teléfono">{p.phone ?? p.whatsappPhone}</Dato>
            <Dato etiqueta="Condición de pago">{p.paymentCondition}</Dato>
            <Dato etiqueta="Crédito">{p.creditDays != null ? `${p.creditDays} días` : null}</Dato>
            <Dato etiqueta="Plazo de entrega">{p.leadDays != null ? `${p.leadDays} días` : null}</Dato>
            <Dato etiqueta="Garantía">{p.warrantyTerms}</Dato>
            <Dato etiqueta="Entrega">{p.preferredDeliveryMode ? ENTREGA[p.preferredDeliveryMode] : null}</Dato>
            <Dato etiqueta="Dirección">{p.address}</Dato>
          </dl>
          {p.offeringDescription && (
            <p className="mt-4 whitespace-normal border-t border-slate-100 pt-3 text-sm text-slate-700">{p.offeringDescription}</p>
          )}
        </Bloque>

        <Bloque
          titulo={`Productos que ofrece (${ficha.productos.length})`}
          accion={
            <Button size="sm" variant="ghost" onClick={onProductos}>
              Editar
            </Button>
          }
        >
          {ficha.productos.length === 0 ? (
            <p className="text-sm text-slate-500">Sin productos asignados.</p>
          ) : (
            <ul className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
              {ficha.productos.slice(0, 20).map((x) => (
                <li key={x.productId} className="truncate text-sm text-slate-700" title={x.nombre}>
                  <span className="font-mono text-xs text-slate-500">{x.skuBase}</span> {x.nombre}
                </li>
              ))}
              {ficha.productos.length > 20 && (
                <li className="text-sm text-slate-500">y {ficha.productos.length - 20} más</li>
              )}
            </ul>
          )}
        </Bloque>
      </div>

      <div className="space-y-4">
        <Bloque
          titulo="Categorías"
          accion={
            <Button size="sm" variant="ghost" onClick={onCategorias}>
              Editar
            </Button>
          }
        >
          {ficha.categorias.length === 0 ? (
            <p className="text-sm text-slate-500">Sin categorías.</p>
          ) : (
            <ul className="flex flex-wrap gap-1.5">
              {ficha.categorias.map((c) => (
                <li
                  key={`${c.productTypeId}|${c.familyId}`}
                  className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700"
                >
                  {c.familia ? `${c.tipo} / ${c.familia}` : c.tipo}
                </li>
              ))}
            </ul>
          )}
        </Bloque>
        <EquivalenciasProveedor supplierId={p.supplierId} />
        <Historial supplierId={p.supplierId} />
      </div>
    </div>
  );
}
