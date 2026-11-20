import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronDown, PackagePlus, Plus, Trash2, Wrench } from "lucide-react";
import { invalidar } from "../api/cache";
import { crearOrdenDirecta, listarMonedas } from "../api/compras";
import { listarProveedores } from "../api/proveedores";
import { obtenerCatalogoFormulario } from "../api/requerimientos";
import type { ModoIgv, VarianteCatalogo } from "../api/types";
import { useDatos } from "../api/useDatos";
import ProductoCombobox from "../components/ProductoCombobox";
import ProveedorCombobox from "../components/compras/ProveedorCombobox";
import { CONDICIONES, ENTREGAS } from "../components/compras/condiciones";
import { dinero } from "../components/compras/formato";
import { EMPRESAS } from "../components/empresas";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import CampoNumero from "../components/ui/CampoNumero";
import { Card, CardBody, CardHeader } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

const IGV: { id: ModoIgv; label: string }[] = [
  { id: "MAS_IGV", label: "Precios sin IGV (+18%)" },
  { id: "IGV_INCLUIDO", label: "Precios con IGV incluido" },
  { id: "NO_AFECTO", label: "No afecto a IGV" },
];

interface Linea {
  clave: number;
  variante: VarianteCatalogo | null;
  /** null = linea de producto; texto = servicio. */
  servicio: string | null;
  cantidad: string;
  precio: string;
}

const redondear = (n: number) => Math.round(n * 100) / 100;

/** Valor, IGV y precio total como los calcula el servidor (ImpuestoOrden). */
function totales(suma: number, modo: ModoIgv) {
  const s = redondear(suma);
  if (modo === "MAS_IGV") {
    const igv = redondear(s * 0.18);
    return { valor: s, igv, total: redondear(s + igv) };
  }
  if (modo === "IGV_INCLUIDO") {
    const valor = redondear(s / 1.18);
    return { valor, igv: redondear(s - valor), total: s };
  }
  return { valor: s, igv: 0, total: s };
}

/**
 * OC directa (DEC-L080): Logistica compra sin solicitud de cotizacion, con
 * productos del catalogo y/o servicios. Sale aprobada.
 */
export default function ComprasOrdenNueva() {
  const navigate = useNavigate();
  const confirmar = useConfirmar();
  // Datos de referencia: comparten la memoria de enCache (misma clave, mismo dato).
  const catalogo = useDatos("catalogo", obtenerCatalogoFormulario, { fresco: 10 * 60_000 });
  const proveedores = useDatos("proveedores", () => listarProveedores(), { fresco: 5 * 60_000 });
  const monedas = useDatos("monedas", listarMonedas, { fresco: 60 * 60_000 });

  const [empresa, setEmpresa] = useState(EMPRESAS[0].code);
  const [proveedor, setProveedor] = useState<string | null>(null);
  const [moneda, setMoneda] = useState("PEN");
  const [formaPago, setFormaPago] = useState("CREDITO_30");
  const [entrega, setEntrega] = useState("ALMACEN_CLIENTE");
  const [modoIgv, setModoIgv] = useState<ModoIgv>("MAS_IGV");
  const [plazo, setPlazo] = useState("");
  const [lugar, setLugar] = useState("");
  const [especificaciones, setEspecificaciones] = useState("");
  const [cecoId, setCecoId] = useState("");
  const [codigoReq, setCodigoReq] = useState("");
  const [lineas, setLineas] = useState<Linea[]>([]);
  const [siguiente, setSiguiente] = useState(1);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cecos = useMemo(
    () => (catalogo.datos?.cecos ?? []).filter((c) => c.empresaCode === empresa),
    [catalogo.datos, empresa],
  );
  const suma = lineas.reduce((acc, l) => acc + (Number(l.cantidad) || 0) * (Number(l.precio) || 0), 0);
  const t = totales(suma, modoIgv);

  function agregar(servicio: boolean) {
    setLineas((ls) => [...ls, { clave: siguiente, variante: null, servicio: servicio ? "" : null, cantidad: "1", precio: "" }]);
    setSiguiente((n) => n + 1);
  }

  function cambiar(clave: number, cambio: Partial<Linea>) {
    setLineas((ls) => ls.map((l) => (l.clave === clave ? { ...l, ...cambio } : l)));
  }

  function faltante(): string | null {
    if (!proveedor) return "Selecciona el proveedor.";
    if (lineas.length === 0) return "Agrega al menos un producto o servicio.";
    for (const l of lineas) {
      if (l.servicio === null && !l.variante) return "Elige el producto de cada línea de producto.";
      if (l.servicio !== null && !l.servicio.trim()) return "Describe cada servicio.";
      if (!(Number(l.cantidad) > 0)) return "Indica la cantidad de cada línea.";
      if (!(Number(l.precio) > 0)) return "Indica el precio unitario de cada línea.";
    }
    return null;
  }

  async function emitir() {
    const falta = faltante();
    if (falta) {
      setError(falta);
      return;
    }
    const ok = await confirmar({
      titulo: "¿Emitir la orden de compra?",
      mensaje: `Sale aprobada por ${dinero(t.total, moneda)}. Después no se pueden cambiar sus líneas.`,
      confirmar: "Emitir orden",
    });
    if (!ok) return;
    setGuardando(true);
    try {
      const res = await crearOrdenDirecta({
        empresaCode: empresa,
        supplierId: proveedor!,
        currencyCode: moneda,
        paymentCondition: formaPago,
        deliveryMode: entrega,
        deliveryLocation: lugar.trim() || null,
        deliveryTerm: plazo.trim() || null,
        taxMode: modoIgv,
        additionalSpecs: especificaciones.trim() || null,
        cecoId: cecoId || null,
        requestCode: codigoReq.trim() || null,
        lineas: lineas.map((l) => ({
          variantId: l.servicio === null ? l.variante!.variantId : null,
          descripcion: l.servicio,
          cantidad: Number(l.cantidad),
          precioUnitario: Number(l.precio),
        })),
      });
      if (res.ok && res.result) {
        invalidar("compras-ordenes");
        navigate(`/compras/ordenes/${res.result.orderId}`, { replace: true });
      } else {
        setError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  const cargando = !catalogo.datos || !proveedores.datos || !monedas.datos;

  return (
    <div>
      <PageHeader
        title="Nueva orden directa"
        backTo="/compras/ordenes"
        backLabel="Órdenes de compra"
        actions={
          <Button icon={<PackagePlus className="size-4" />} loading={guardando} disabled={cargando} onClick={emitir}>
            Emitir orden
          </Button>
        }
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {cargando ? (
        <Card className="mb-4">
          <CardBody className="grid gap-4 sm:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="flex flex-col gap-2">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-9 w-full" />
              </div>
            ))}
          </CardBody>
        </Card>
      ) : (
        <>
          <Card className="mb-4">
            <CardBody className="flex flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="Empresa" required>
                  <select
                    className={`${inputClass} w-full`}
                    value={empresa}
                    onChange={(e) => {
                      setEmpresa(e.target.value);
                      setCecoId("");
                    }}
                  >
                    {EMPRESAS.map((e) => (
                      <option key={e.code} value={e.code}>
                        {e.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Proveedor" required className="sm:col-span-2">
                  <ProveedorCombobox proveedores={proveedores.datos ?? []} valor={proveedor} onSeleccionar={setProveedor} />
                </Field>
                <Field label="Moneda">
                  <select className={`${inputClass} w-full`} value={moneda} onChange={(e) => setMoneda(e.target.value)}>
                    {(monedas.datos ?? []).map((m) => (
                      <option key={m.currencyCode} value={m.currencyCode}>
                        {m.currencyCode} · {m.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Forma de pago">
                  <select className={`${inputClass} w-full`} value={formaPago} onChange={(e) => setFormaPago(e.target.value)}>
                    {CONDICIONES.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="IGV">
                  <select className={`${inputClass} w-full`} value={modoIgv} onChange={(e) => setModoIgv(e.target.value as ModoIgv)}>
                    {IGV.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Plazo de entrega">
                  <input className={`${inputClass} w-full`} value={plazo} maxLength={120} placeholder="Ej. 5 días hábiles" onChange={(e) => setPlazo(e.target.value)} />
                </Field>
                <Field label="Entrega">
                  <select className={`${inputClass} w-full`} value={entrega} onChange={(e) => setEntrega(e.target.value)}>
                    {ENTREGAS.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Lugar de entrega">
                  <input className={`${inputClass} w-full`} value={lugar} maxLength={220} onChange={(e) => setLugar(e.target.value)} />
                </Field>
              </div>

              <details className="group">
                <summary className="flex cursor-pointer list-none items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900">
                  <ChevronDown className="size-4 transition-transform group-open:rotate-180" />
                  Referencias y especificaciones
                </summary>
                <div className="mt-4 grid gap-4 sm:grid-cols-3">
                  <Field label="CECO">
                    <select className={`${inputClass} w-full`} value={cecoId} onChange={(e) => setCecoId(e.target.value)}>
                      <option value="">Sin CECO</option>
                      {cecos.map((c) => (
                        <option key={c.cecoId} value={c.cecoId}>
                          {c.code} · {c.name}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Requerimiento">
                    <input
                      className={`${inputClass} w-full font-mono`}
                      value={codigoReq}
                      maxLength={50}
                      placeholder="REQ-…"
                      onChange={(e) => setCodigoReq(e.target.value)}
                    />
                  </Field>
                  <Field label="Especificaciones adicionales" className="sm:col-span-3">
                    <textarea
                      className={`${inputClass} w-full`}
                      rows={3}
                      maxLength={2000}
                      value={especificaciones}
                      onChange={(e) => setEspecificaciones(e.target.value)}
                    />
                  </Field>
                </div>
              </details>
            </CardBody>
          </Card>

          <Card className="mb-4 overflow-hidden">
            <CardHeader className="flex items-center justify-between gap-3">
              <div className="rotulo">Productos y servicios ({lineas.length})</div>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" icon={<Plus className="size-3.5" />} onClick={() => agregar(false)}>
                  Producto
                </Button>
                <Button variant="secondary" size="sm" icon={<Wrench className="size-3.5" />} onClick={() => agregar(true)}>
                  Servicio
                </Button>
              </div>
            </CardHeader>
            {lineas.length === 0 ? (
              <CardBody className="text-sm text-slate-500">Agrega productos del catálogo o servicios.</CardBody>
            ) : (
              <div className="overflow-hidden">
                <table className="tabla">
                  <thead>
                    <tr className="border-b border-slate-200 cabecera-tabla">
                      <th className="px-5 py-2.5">Descripción</th>
                      <th className="w-28 px-3 py-2.5">Cantidad</th>
                      <th className="w-36 px-3 py-2.5">Precio unitario</th>
                      <th className="w-32 px-3 py-2.5 text-right">Subtotal</th>
                      <th className="w-12 px-3 py-2.5" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {lineas.map((l) => (
                      <tr key={l.clave}>
                        <td className="col-flex px-5 py-2">
                          {l.servicio === null ? (
                            <ProductoCombobox
                              variantes={catalogo.datos?.variantes ?? []}
                              valor={l.variante}
                              onSeleccionar={(v) => cambiar(l.clave, { variante: v })}
                            />
                          ) : (
                            <input
                              className={`${inputClass} w-full`}
                              placeholder="Describe el servicio"
                              maxLength={250}
                              value={l.servicio}
                              onChange={(e) => cambiar(l.clave, { servicio: e.target.value })}
                            />
                          )}
                        </td>
                        <td className="px-3 py-2">
                          <CampoNumero
                            className={`${inputClass} w-full`}
                            value={l.cantidad}
                            maxEnteros={9}
                            onChange={(v) => cambiar(l.clave, { cantidad: v })}
                          />
                        </td>
                        <td className="px-3 py-2">
                          <CampoNumero
                            className={`${inputClass} w-full`}
                            value={l.precio}
                            decimales={4}
                            maxEnteros={10}
                            onChange={(v) => cambiar(l.clave, { precio: v })}
                          />
                        </td>
                        <td className="px-3 py-2 text-right text-sm text-slate-700">
                          {dinero(redondear((Number(l.cantidad) || 0) * (Number(l.precio) || 0)))}
                        </td>
                        <td className="px-3 py-2">
                          <button
                            type="button"
                            className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-rose-600"
                            title="Quitar línea"
                            aria-label="Quitar línea"
                            onClick={() => setLineas((ls) => ls.filter((x) => x.clave !== l.clave))}
                          >
                            <Trash2 className="size-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <CardBody className="flex justify-end border-t border-slate-200">
              <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm">
                <dt className="text-slate-500">Valor total</dt>
                <dd className="text-right text-slate-700">{dinero(t.valor, moneda)}</dd>
                <dt className="text-slate-500">IGV</dt>
                <dd className="text-right text-slate-700">{dinero(t.igv, moneda)}</dd>
                <dt className="font-medium text-slate-700">Precio total</dt>
                <dd className="text-right font-semibold text-slate-900">{dinero(t.total, moneda)}</dd>
              </dl>
            </CardBody>
          </Card>
        </>
      )}
    </div>
  );
}
