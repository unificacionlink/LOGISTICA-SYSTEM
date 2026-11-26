import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FileUp, Link2, Plus, ScanText, Trash2 } from "lucide-react";
import {
  leerComprobanteCajaChica,
  registrarCompraCajaChica,
  TIPOS_REGISTRO,
  type LecturaComprobante,
  type TipoComprobanteCajaChica,
} from "../api/cajachica";
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
  /** DEC-L095: la llenó el OCR. */
  leida?: boolean;
}

type Campo = "fecha" | "tipo" | "serie" | "numero" | "ruc" | "proveedor";

const FORMATOS = "application/pdf,image/jpeg,image/png,image/webp";
/** Lo que llenó el OCR se ve en verde, para distinguirlo de lo que quedó vacío. */
const LEIDO = "border-emerald-400 bg-emerald-50/50 ring-2 ring-emerald-100";
let siguiente = 1;
const lineaVacia = (): Linea => ({ clave: siguiente++, variante: null, delCatalogo: false, descripcion: "", cantidad: "1", precio: "" });

function totalLinea(l: Linea): number {
  return (Number(l.cantidad) || 0) * (Number(l.precio) || 0);
}

const lineaEnBlanco = (l: Linea) => !l.variante && !l.descripcion.trim() && !l.precio;

/**
 * Registrar una compra con la caja chica del proyecto (DEC-L092): a la izquierda
 * el formulario y a la derecha la factura (obligatoria, foto, PDF o enlace),
 * del mismo alto que el formulario. DEC-L096: solo boleta o factura, sin nota.
 * DEC-L095: "Leer con OCR" llena lo que se pueda leer del comprobante y lo
 * marca; lo que no se leyó queda vacío para completarlo mirando la factura.
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
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [leyendo, setLeyendo] = useState(false);
  const [leidos, setLeidos] = useState<Set<Campo>>(new Set());
  const [aviso, setAviso] = useState<{ tono: "good" | "info"; texto: string } | null>(null);

  const total = useMemo(() => lineas.reduce((s, l) => s + totalLinea(l), 0), [lineas]);
  const vista = useMemo(() => (archivo ? URL.createObjectURL(archivo) : null), [archivo]);
  useEffect(() => () => {
    if (vista) URL.revokeObjectURL(vista);
  }, [vista]);

  const marca = (campo: Campo) => (leidos.has(campo) ? LEIDO : "");

  function cambiar(clave: number, cambio: Partial<Linea>) {
    setLineas((prev) => prev.map((l) => (l.clave === clave ? { ...l, ...cambio } : l)));
  }

  function elegirArchivo(f: File | null) {
    setArchivo(f);
    setAviso(null);
  }

  /** Llena solo lo que el OCR reconoció; lo demás no se toca. */
  function aplicar(r: LecturaComprobante) {
    const llenos = new Set<Campo>();
    if (r.fecha) {
      setFecha(r.fecha);
      llenos.add("fecha");
    }
    if (r.tipo === "FACTURA" || r.tipo === "BOLETA") {
      setTipo(r.tipo);
      llenos.add("tipo");
    }
    if (r.serie) {
      setSerie(r.serie.toUpperCase());
      llenos.add("serie");
    }
    if (r.numero) {
      setNumero(r.numero);
      llenos.add("numero");
    }
    if (r.ruc) {
      setRuc(r.ruc);
      llenos.add("ruc");
    }
    if (r.proveedor) {
      setProveedor(r.proveedor);
      llenos.add("proveedor");
    }
    let nLineas = 0;
    if (lineas.every(lineaEnBlanco)) {
      const leidas: Linea[] = r.lineas.map((l) => ({
        clave: siguiente++,
        variante: null,
        delCatalogo: false,
        descripcion: l.descripcion,
        cantidad: String(l.cantidad),
        precio: String(l.precioUnitario),
        leida: true,
      }));
      if (leidas.length === 0 && r.total) {
        // Sin el detalle, al menos el total: queda describir lo comprado.
        leidas.push({ ...lineaVacia(), precio: String(r.total), leida: true });
      }
      if (leidas.length > 0) setLineas(leidas);
      nLineas = leidas.length;
    }
    setLeidos(llenos);
    const n = llenos.size + nLineas;
    setAviso(
      n > 0
        ? { tono: "good", texto: `Se leyeron ${n} dato${n === 1 ? "" : "s"} (en verde). Revisa y completa lo que quedó vacío.` }
        : { tono: "info", texto: "No se pudo leer el comprobante. Completa los datos mirando la factura." },
    );
  }

  async function leer() {
    if (!archivo) return;
    setLeyendo(true);
    setAviso(null);
    try {
      const res = await leerComprobanteCajaChica(archivo);
      if (res.ok && res.result) aplicar(res.result);
      else setAviso({ tono: "info", texto: res.message || "No se pudo leer el comprobante." });
    } finally {
      setLeyendo(false);
    }
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
          nota: null,
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
    <div className="mx-auto max-w-7xl">
      <PageHeader title="Registrar compra" backTo="/caja-chica" backLabel="Caja chica" />

      {catalogo.error && <Alert tone="danger" className="mb-4">{catalogo.error}</Alert>}

      {!catalogo.datos ? (
        <div className="grid gap-5 lg:grid-cols-2">
          <Card>
            <CardBody className="grid gap-4 sm:grid-cols-2">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-16" />
              ))}
            </CardBody>
          </Card>
          <Skeleton className="h-[28rem]" />
        </div>
      ) : (
        <>
        <div className="grid items-stretch gap-5 lg:grid-cols-2">
          <div className="flex min-w-0 flex-col gap-5">
            <Card>
              <CardHeader>
                <h2 className="text-sm font-semibold text-slate-900">Comprobante</h2>
              </CardHeader>
              <CardBody className="grid gap-4 sm:grid-cols-2">
                <Field label="Centro de costo" required className="sm:col-span-2">
                  <CecoCombobox cecos={cecos} valor={cecoId} onSeleccionar={setCecoId} />
                </Field>
                <Field label="Fecha de compra" required>
                  <input
                    type="date"
                    value={fecha}
                    max={hoyIso()}
                    onChange={(e) => setFecha(e.target.value)}
                    className={`${inputClass} w-full ${marca("fecha")}`}
                  />
                </Field>
                <Field label="Tipo" required>
                  <select
                    value={tipo}
                    onChange={(e) => setTipo(e.target.value as TipoComprobanteCajaChica)}
                    className={`${inputClass} w-full ${marca("tipo")}`}
                  >
                    {TIPOS_REGISTRO.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Serie">
                  <input
                    value={serie}
                    maxLength={10}
                    onChange={(e) => setSerie(e.target.value.toUpperCase())}
                    placeholder="F001"
                    className={`${inputClass} w-full ${marca("serie")}`}
                  />
                </Field>
                <Field label="Número" required>
                  <input
                    value={numero}
                    maxLength={20}
                    onChange={(e) => setNumero(e.target.value)}
                    placeholder="00012345"
                    title="El correlativo que va después de la serie: en F001-00012345 es 00012345"
                    className={`${inputClass} w-full ${marca("numero")}`}
                  />
                </Field>
                <Field label="RUC" required={tipo === "FACTURA"}>
                  <CampoNumero value={ruc} onChange={setRuc} maxEnteros={11} className={`${inputClass} w-full ${marca("ruc")}`} />
                </Field>
                <Field label="Proveedor" required>
                  <input
                    value={proveedor}
                    maxLength={200}
                    onChange={(e) => setProveedor(e.target.value)}
                    placeholder="Razón social o tienda"
                    className={`${inputClass} w-full ${marca("proveedor")}`}
                  />
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
                  <div key={l.clave} className="grid items-end gap-3 px-5 py-3 sm:grid-cols-[minmax(0,1fr)_5rem_6.5rem_6rem_1.5rem]">
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
                          className={`${inputClass} w-full ${l.leida && l.descripcion ? LEIDO : ""}`}
                        />
                      )}
                    </Field>
                    <Field label="Cantidad">
                      <CampoNumero
                        value={l.cantidad}
                        onChange={(v) => cambiar(l.clave, { cantidad: v })}
                        className={`${inputClass} w-full ${l.leida && l.descripcion ? LEIDO : ""}`}
                      />
                    </Field>
                    <Field label="Precio unit.">
                      <CampoNumero
                        value={l.precio}
                        decimales={2}
                        maxEnteros={7}
                        onChange={(v) => cambiar(l.clave, { precio: v })}
                        className={`${inputClass} w-full ${l.leida ? LEIDO : ""}`}
                      />
                    </Field>
                    <p className="pb-2 text-right text-sm font-medium text-slate-800">{dinero(totalLinea(l), "PEN")}</p>
                    <button
                      type="button"
                      disabled={lineas.length === 1}
                      onClick={() => setLineas((prev) => prev.filter((x) => x.clave !== l.clave))}
                      className="pb-2 text-slate-400 hover:text-rose-600 disabled:invisible"
                      title="Quitar línea"
                      aria-label="Quitar línea"
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

          </div>

          {/* Mismo alto que Comprobante + Lo comprado: la factura no se desplaza con la página. */}
          <Card className="flex min-w-0 flex-col">
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-slate-900">
                  Factura <span className="text-rose-600" aria-hidden="true">*</span>
                </h2>
                {modoFactura === "archivo" && (
                  <Button size="sm" icon={<ScanText className="size-4" />} disabled={!archivo} loading={leyendo} onClick={leer}>
                    Leer con OCR
                  </Button>
                )}
              </div>
            </CardHeader>
            <CardBody className="flex flex-1 flex-col gap-4">
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
                <>
                  <input
                    type="file"
                    accept={FORMATOS}
                    onChange={(e) => elegirArchivo(e.target.files?.[0] ?? null)}
                    className="text-sm text-slate-700 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
                  />
                  {aviso && <Alert tone={aviso.tono}>{aviso.texto}</Alert>}
                  {vista && archivo ? (
                    archivo.type === "application/pdf" ? (
                      <iframe src={vista} title="Factura" className="min-h-[20rem] w-full flex-1 rounded-md border border-slate-200" />
                    ) : (
                      <div className="relative min-h-[20rem] flex-1 overflow-hidden rounded-md border border-slate-200 bg-slate-50">
                        <img src={vista} alt="Factura" className="absolute inset-0 size-full object-contain" />
                      </div>
                    )
                  ) : (
                    <div className="flex min-h-[16rem] flex-1 flex-col items-center justify-center gap-2 rounded-md border border-dashed border-slate-300 text-sm text-slate-400">
                      <FileUp className="size-6" />
                      Foto o PDF de la factura
                    </div>
                  )}
                </>
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
            </CardBody>
          </Card>
        </div>

        {error && <Alert tone="danger" className="mt-5">{error}</Alert>}

        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" href="/caja-chica">
            Cancelar
          </Button>
          <Button loading={guardando} onClick={guardar}>
            Registrar compra
          </Button>
        </div>
        </>
      )}
    </div>
  );
}
