import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FileUp, Link2, Plus, Trash2 } from "lucide-react";
import { registrarCompraCajaChica, TIPOS_COMPROBANTE, type TipoComprobanteCajaChica } from "../api/cajachica";
import { invalidar } from "../api/cache";
import { obtenerCatalogoFormulario } from "../api/requerimientos";
import type { VarianteCatalogo } from "../api/types";
import { useDatos } from "../api/useDatos";
import CecoCombobox from "../components/CecoCombobox";
import { dinero } from "../components/compras/formato";
import { hoyIso } from "../components/formato";
import ProductoCombobox from "../components/ProductoCombobox";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import CampoNumero from "../components/ui/CampoNumero";
import { Card, CardBody, CardHeader } from "../components/ui/Card";
import Field, { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton } from "../components/ui/Skeleton";

interface Linea {
  clave: number;
  variante: VarianteCatalogo | null;
  /** true: se elige del catalogo; false: se describe a mano. */
  delCatalogo: boolean;
  descripcion: string;
  cantidad: string;
  precio: string;
}

const FORMATOS = "application/pdf,image/jpeg,image/png,image/webp";
let siguiente = 1;
const lineaVacia = (): Linea => ({ clave: siguiente++, variante: null, delCatalogo: false, descripcion: "", cantidad: "1", precio: "" });

function totalLinea(l: Linea): number {
  return (Number(l.cantidad) || 0) * (Number(l.precio) || 0);
}

/**
 * Registrar una compra con la caja chica del proyecto (DEC-L092): CECO,
 * comprobante, lo comprado (del catalogo o libre) y la factura obligatoria
 * como foto, PDF o enlace. No pasa por requerimiento ni revision.
 */
export default function CajaChicaNueva() {
  const navigate = useNavigate();
  const catalogo = useDatos("catalogo-formulario", obtenerCatalogoFormulario);
  const cecos = catalogo.datos?.cecos ?? [];
  const variantes = catalogo.datos?.variantes ?? [];

  const [cecoId, setCecoId] = useState("");
  const [fecha, setFecha] = useState(hoyIso());
  const [tipo, setTipo] = useState<TipoComprobanteCajaChica>("FACTURA");
  const [serie, setSerie] = useState("");
  const [numero, setNumero] = useState("");
  const [ruc, setRuc] = useState("");
  const [proveedor, setProveedor] = useState("");
  const [lineas, setLineas] = useState<Linea[]>(() => [lineaVacia()]);
  const [modoFactura, setModoFactura] = useState<"archivo" | "enlace">("archivo");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enlace, setEnlace] = useState("");
  const [nota, setNota] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const total = useMemo(() => lineas.reduce((s, l) => s + totalLinea(l), 0), [lineas]);

  function cambiar(clave: number, cambio: Partial<Linea>) {
    setLineas((prev) => prev.map((l) => (l.clave === clave ? { ...l, ...cambio } : l)));
  }

  function validar(): string | null {
    if (!cecoId) return "Elige el centro de costo (CECO).";
    if (!fecha || fecha > hoyIso()) return "La fecha de compra no puede ser futura.";
    if (tipo === "FACTURA" && !/^\d{11}$/.test(ruc)) return "Una factura lleva el RUC del proveedor (11 dígitos).";
    if (ruc && !/^\d{11}$/.test(ruc)) return "El RUC tiene 11 dígitos.";
    if (!proveedor.trim()) return "Indica dónde se compró.";
    if (!numero.trim()) return "Indica el número del comprobante.";
    for (const l of lineas) {
      if (l.delCatalogo ? !l.variante : !l.descripcion.trim()) return "Cada línea lleva un producto o una descripción.";
      if (!(Number(l.cantidad) > 0)) return "La cantidad de cada línea debe ser mayor que cero.";
      if (l.precio === "") return "Indica el precio unitario de cada línea.";
    }
    if (!(total > 0)) return "El total de la compra debe ser mayor que cero.";
    if (modoFactura === "archivo" && !archivo) return "Adjunta la factura (foto o PDF).";
    if (modoFactura === "enlace" && !/^https?:\/\/\S+$/i.test(enlace.trim())) return "Pega el enlace de la factura (http o https).";
    return null;
  }

  async function guardar() {
    const problema = validar();
    if (problema) {
      setError(problema);
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const res = await registrarCompraCajaChica(
        {
          cecoId,
          fecha,
          proveedorRuc: ruc || null,
          proveedorNombre: proveedor.trim(),
          tipoComprobante: tipo,
          serie: serie.trim() || null,
          numero: numero.trim(),
          facturaUrl: modoFactura === "enlace" ? enlace.trim() : null,
          nota: nota.trim() || null,
          lineas: lineas.map((l) => ({
            variantId: l.delCatalogo ? (l.variante?.variantId ?? null) : null,
            descripcion: l.delCatalogo ? null : l.descripcion.trim(),
            cantidad: Number(l.cantidad),
            precioUnitario: Number(l.precio),
          })),
        },
        modoFactura === "archivo" ? archivo : null,
      );
      if (!res.ok) {
        setError(res.message);
        return;
      }
      invalidar("cajachica");
      navigate("/caja-chica", { replace: true });
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Registrar compra" backTo="/caja-chica" backLabel="Caja chica" />

      {catalogo.error && <Alert tone="danger" className="mb-4">{catalogo.error}</Alert>}

      {!catalogo.datos ? (
        <Card>
          <CardBody className="grid gap-4 sm:grid-cols-2">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-16" />
            ))}
          </CardBody>
        </Card>
      ) : (
        <div className="flex flex-col gap-5">
          <Card>
            <CardHeader>
              <h2 className="text-sm font-semibold text-slate-900">Comprobante</h2>
            </CardHeader>
            <CardBody className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Field label="Centro de costo" required className="sm:col-span-2">
                <CecoCombobox cecos={cecos} valor={cecoId} onSeleccionar={setCecoId} />
              </Field>
              <Field label="Fecha de compra" required>
                <input type="date" value={fecha} max={hoyIso()} onChange={(e) => setFecha(e.target.value)} className={`${inputClass} w-full`} />
              </Field>
              <Field label="Tipo" required>
                <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoComprobanteCajaChica)} className={`${inputClass} w-full`}>
                  {TIPOS_COMPROBANTE.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Serie">
                <input value={serie} maxLength={10} onChange={(e) => setSerie(e.target.value.toUpperCase())} placeholder="F001" className={`${inputClass} w-full`} />
              </Field>
              <Field label="Número" required>
                <input value={numero} maxLength={20} onChange={(e) => setNumero(e.target.value)} className={`${inputClass} w-full`} />
              </Field>
              <Field label="RUC" required={tipo === "FACTURA"}>
                <CampoNumero value={ruc} onChange={setRuc} maxEnteros={11} className={`${inputClass} w-full`} />
              </Field>
              <Field label="Proveedor" required className="sm:col-span-2">
                <input value={proveedor} maxLength={200} onChange={(e) => setProveedor(e.target.value)} placeholder="Razón social o tienda" className={`${inputClass} w-full`} />
              </Field>
            </CardBody>
          </Card>

          <Card className="overflow-hidden">
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-slate-900">Lo comprado</h2>
                <span className="text-sm font-semibold text-slate-900">{dinero(total, "PEN")}</span>
              </div>
            </CardHeader>
            <div className="divide-y divide-slate-100">
              {lineas.map((l) => (
                <div key={l.clave} className="grid items-end gap-3 px-5 py-3 sm:grid-cols-[minmax(0,1fr)_5.5rem_7.5rem_6.5rem_2rem]">
                  <Field
                    label={
                      <span className="flex items-center gap-2">
                        {l.delCatalogo ? "Producto" : "Descripción"}
                        <button
                          type="button"
                          onClick={() => cambiar(l.clave, { delCatalogo: !l.delCatalogo, variante: null })}
                          className="text-xs font-medium text-brand-600 hover:text-brand-700"
                        >
                          {l.delCatalogo ? "Escribir" : "Del catálogo"}
                        </button>
                      </span>
                    }
                  >
                    {l.delCatalogo ? (
                      <ProductoCombobox variantes={variantes} valor={l.variante} onSeleccionar={(v) => cambiar(l.clave, { variante: v })} />
                    ) : (
                      <input
                        value={l.descripcion}
                        maxLength={300}
                        onChange={(e) => cambiar(l.clave, { descripcion: e.target.value })}
                        className={`${inputClass} w-full`}
                      />
                    )}
                  </Field>
                  <Field label="Cantidad">
                    <CampoNumero value={l.cantidad} onChange={(v) => cambiar(l.clave, { cantidad: v })} className={`${inputClass} w-full`} />
                  </Field>
                  <Field label="Precio unit.">
                    <CampoNumero value={l.precio} decimales={2} maxEnteros={7} onChange={(v) => cambiar(l.clave, { precio: v })} className={`${inputClass} w-full`} />
                  </Field>
                  <p className="pb-2 text-right text-sm font-medium text-slate-800">{dinero(totalLinea(l), "PEN")}</p>
                  <button
                    type="button"
                    disabled={lineas.length === 1}
                    onClick={() => setLineas((prev) => prev.filter((x) => x.clave !== l.clave))}
                    className="pb-2 text-slate-400 hover:text-rose-600 disabled:invisible"
                    title="Quitar línea"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </div>
              ))}
            </div>
            <div className="border-t border-slate-100 px-5 py-3">
              <Button size="sm" variant="ghost" icon={<Plus className="size-4" />} disabled={lineas.length >= 50} onClick={() => setLineas((prev) => [...prev, lineaVacia()])}>
                Agregar línea
              </Button>
            </div>
          </Card>

          <Card>
            <CardHeader>
              <h2 className="text-sm font-semibold text-slate-900">
                Factura <span className="text-rose-600" aria-hidden="true">*</span>
              </h2>
            </CardHeader>
            <CardBody className="flex flex-col gap-4">
              <div role="radiogroup" className="inline-flex w-fit rounded-md border border-slate-200 p-0.5">
                {(
                  [
                    { id: "archivo", label: "Foto o PDF", icon: FileUp },
                    { id: "enlace", label: "Enlace", icon: Link2 },
                  ] as const
                ).map(({ id, label, icon: Icon }) => (
                  <button
                    key={id}
                    type="button"
                    role="radio"
                    aria-checked={modoFactura === id}
                    onClick={() => setModoFactura(id)}
                    className={`flex items-center gap-1.5 rounded px-3 py-1.5 text-sm font-medium ${
                      modoFactura === id ? "bg-brand-50 text-brand-700" : "text-slate-500 hover:text-slate-700"
                    }`}
                  >
                    <Icon className="size-4" />
                    {label}
                  </button>
                ))}
              </div>
              {modoFactura === "archivo" ? (
                <input
                  type="file"
                  accept={FORMATOS}
                  onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
                  className="text-sm text-slate-700 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
                />
              ) : (
                <input
                  type="url"
                  value={enlace}
                  maxLength={500}
                  onChange={(e) => setEnlace(e.target.value)}
                  placeholder="https://"
                  className={`${inputClass} w-full`}
                />
              )}
              <Field label="Nota">
                <textarea value={nota} maxLength={500} rows={2} onChange={(e) => setNota(e.target.value)} className={`${inputClass} w-full`} />
              </Field>
            </CardBody>
          </Card>

          {error && <Alert tone="danger">{error}</Alert>}

          <div className="flex justify-end gap-2">
            <Button variant="secondary" href="/caja-chica">
              Cancelar
            </Button>
            <Button loading={guardando} onClick={guardar}>
              Registrar compra
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
