import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AlertTriangle, Check, FileText, Link2, Loader2, ScanText, Trophy, Upload } from "lucide-react";
import {
  adjudicarComparativo,
  descartarCotizacion,
  emitirOrden,
  leerCotizacionComparativo,
  listarMonedas,
  listarPorEmitir,
  obtenerComparativo,
} from "../api/compras";
import { listarProveedores } from "../api/proveedores";
import type {
  Comparativo,
  ComparativoColumna,
  ComparativoPrecio,
  Moneda,
  OcrComparativo,
  PorEmitir,
  Proveedor,
} from "../api/types";
import { CONDICIONES } from "../components/compras/condiciones";
import EditorCotizacion from "../components/compras/EditorCotizacion";
import { ConsolidadoEstadoBadge } from "../components/compras/etiquetas";
import { cantidad, dinero } from "../components/compras/formato";
import { EMPRESAS } from "../components/empresas";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import { inputClass } from "../components/ui/Field";
import MenuAcciones from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import { Skeleton, SkeletonTable } from "../components/ui/Skeleton";
import { comprimirImagen, esImagen } from "../utils/imagenes";

import CampoNumero from "../components/ui/CampoNumero";
const MAX_COTIZACIONES = 3;

/** Una cotización subida que se está leyendo o espera revisión (aún no guardada). */
interface Borrador {
  clave: string;
  archivo: File;
  estado: "leyendo" | "listo" | "error";
  ocr: OcrComparativo | null;
  error: string | null;
  supplierFijo: string | null;
  reemplaza: ComparativoColumna | null;
}

/** Destino de la próxima subida desde el selector de archivos. */
type Destino =
  { tipo: "nuevas" } | { tipo: "proveedor"; supplierId: string } | { tipo: "reemplazo"; columna: ComparativoColumna };

const EDITABLE = new Set(["EN_COTIZACION", "ADJUDICADA", "ABIERTA"]);

function precioDe(c: ComparativoColumna, consolidationId: string): ComparativoPrecio | null {
  return c.precios.find((p) => p.consolidationId === consolidationId) ?? null;
}

/**
 * Comparativo de cotizaciones (DEC-L047): hasta tres cotizaciones por grupo de
 * productos de una empresa, leídas por OCR, lado a lado con el mejor precio
 * marcado; se adjudica todo de una vez y se emiten las OC desde aquí.
 */
export default function ComprasComparativo() {
  const confirmar = useConfirmar();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const ids = useMemo(() => (params.get("ids") ?? "").split(",").filter(Boolean), [params]);

  const [comparativo, setComparativoBase] = useState<Comparativo | null>(null);
  const [proveedores, setProveedores] = useState<Proveedor[]>([]);
  const [monedas, setMonedas] = useState<Moneda[]>([]);
  const [porEmitir, setPorEmitir] = useState<PorEmitir[]>([]);
  const [cargando, setCargando] = useState(ids.length > 0);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [borradores, setBorradores] = useState<Borrador[]>([]);
  const [abierto, setAbierto] = useState<string | null>(null);
  /** Ganadores elegidos a mano; el resto sale de lo adjudicado o del mejor precio. */
  const [elecciones, setElecciones] = useState<Record<string, string>>({});
  const [tc, setTc] = useState("");
  const [procesando, setProcesando] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const destino = useRef<Destino>({ tipo: "nuevas" });
  const contador = useRef(0);

  /** Cada comparativo nuevo vuelve a proponer lo adjudicado o el mejor precio. */
  const setComparativo = useCallback((c: Comparativo) => {
    setComparativoBase(c);
    setElecciones({});
  }, []);

  const cargarPorEmitir = useCallback(() => {
    listarPorEmitir().then((res) => res.ok && setPorEmitir(res.result ?? []));
  }, []);

  useEffect(() => {
    if (ids.length === 0) return;
    Promise.all([obtenerComparativo(ids), listarProveedores(), listarMonedas()])
      .then(([c, p, m]) => {
        if (c.ok && c.result) setComparativo(c.result);
        else setError(c.message);
        if (p.ok) setProveedores(p.result ?? []);
        if (m.ok) setMonedas(m.result ?? []);
      })
      .finally(() => setCargando(false));
    cargarPorEmitir();
  }, [ids, cargarPorEmitir, setComparativo]);

  const cotizadas = useMemo(() => comparativo?.proveedores.filter((c) => c.quotationId) ?? [], [comparativo]);
  const filasCotizables = useMemo(() => comparativo?.filas.filter((f) => EDITABLE.has(f.status)) ?? [], [comparativo]);

  // ---------------------------------------------------------- mejor precio y selección

  const monedasEnUso = useMemo(
    () => new Set(cotizadas.flatMap((c) => c.precios.map((p) => p.currencyCode))),
    [cotizadas],
  );
  const mixtas = monedasEnUso.size > 1;
  const tipoCambio = Number(tc) > 0 ? Number(tc) : null;

  /** Precio comparable: en la moneda común o, si hay soles y dólares, convertido a soles con el tipo de cambio. */
  const comparable = useCallback(
    (p: ComparativoPrecio | null, campo: "unitCost" | "lineTotal"): number | null => {
      if (!p) return null;
      if (!mixtas || p.currencyCode === "PEN") return p[campo];
      return tipoCambio && p.currencyCode === "USD" ? p[campo] * tipoCambio : null;
    },
    [mixtas, tipoCambio],
  );

  const mejores = useMemo(() => {
    const m: Record<string, string> = {};
    for (const f of comparativo?.filas ?? []) {
      let mejor: { id: string; v: number } | null = null;
      let ofertas = 0;
      for (const c of cotizadas) {
        const p = precioDe(c, f.consolidationId);
        const v = comparable(p, "unitCost");
        if (p && v != null) {
          ofertas++;
          if (!mejor || v < mejor.v) mejor = { id: p.quotationItemId, v };
        }
      }
      if (mejor && ofertas > 0) m[f.consolidationId] = mejor.id;
    }
    return m;
  }, [comparativo, cotizadas, comparable]);

  // La seleccion parte de lo adjudicado o, si no hay, del mejor precio; lo elegido a mano manda.
  const seleccion = useMemo(() => {
    const s: Record<string, string> = {};
    for (const f of comparativo?.filas ?? []) {
      const id = f.adjudicadoQuotationItemId ?? mejores[f.consolidationId];
      if (id) s[f.consolidationId] = id;
    }
    return { ...s, ...elecciones };
  }, [comparativo, mejores, elecciones]);

  const precioSeleccionado = (consolidationId: string): ComparativoPrecio | null => {
    const id = seleccion[consolidationId];
    for (const c of cotizadas) {
      const p = precioDe(c, consolidationId);
      if (p && p.quotationItemId === id) return p;
    }
    return null;
  };

  const cambios = (comparativo?.filas ?? []).filter(
    (f) =>
      (f.status === "EN_COTIZACION" || f.status === "ADJUDICADA") &&
      seleccion[f.consolidationId] &&
      seleccion[f.consolidationId] !== f.adjudicadoQuotationItemId,
  );

  let totalSeleccion = 0;
  let ahorro = 0;
  let totalComparable = true;
  for (const f of comparativo?.filas ?? []) {
    const sel = comparable(precioSeleccionado(f.consolidationId), "lineTotal");
    if (precioSeleccionado(f.consolidationId) && sel == null) totalComparable = false;
    if (sel == null) continue;
    totalSeleccion += sel;
    const valores = cotizadas
      .map((c) => comparable(precioDe(c, f.consolidationId), "lineTotal"))
      .filter((v): v is number => v != null);
    if (valores.length > 1) ahorro += Math.max(...valores) - sel;
  }
  const monedaTotal = mixtas ? "PEN" : ([...monedasEnUso][0] ?? "PEN");

  // ---------------------------------------------------------- subida y OCR

  const cupos = MAX_COTIZACIONES - cotizadas.length - borradores.filter((b) => !b.reemplaza).length;

  function elegirArchivos(d: Destino) {
    destino.current = d;
    if (inputRef.current) {
      inputRef.current.multiple = d.tipo === "nuevas";
      inputRef.current.value = "";
      inputRef.current.click();
    }
  }

  async function agregar(archivos: File[], d: Destino) {
    setAviso(null);
    const cuantos = d.tipo === "nuevas" ? Math.max(cupos, 0) : 1;
    if (d.tipo === "nuevas" && archivos.length > cuantos) {
      setAviso(
        cuantos === 0
          ? `Ya hay ${MAX_COTIZACIONES} cotizaciones: reemplaza o descarta una para subir otra.`
          : `Solo se tomaron ${cuantos} archivo(s): el comparativo admite ${MAX_COTIZACIONES} cotizaciones.`,
      );
    }
    for (const original of archivos.slice(0, cuantos)) {
      const clave = `b${++contador.current}`;
      let archivo = original;
      if (esImagen(original)) {
        try {
          archivo = await comprimirImagen(original, 2400);
        } catch (e) {
          setError(e instanceof Error ? e.message : "No se pudo leer la imagen.");
          continue;
        }
      }
      const b: Borrador = {
        clave,
        archivo,
        estado: "leyendo",
        ocr: null,
        error: null,
        supplierFijo: d.tipo === "proveedor" ? d.supplierId : d.tipo === "reemplazo" ? d.columna.supplierId : null,
        reemplaza: d.tipo === "reemplazo" ? d.columna : null,
      };
      setBorradores((prev) => [
        ...prev.filter((x) => !(b.reemplaza && x.reemplaza?.supplierId === b.reemplaza.supplierId)),
        b,
      ]);
      leerCotizacionComparativo(ids, archivo, b.supplierFijo).then((res) => {
        setBorradores((prev) =>
          prev.map((x) =>
            x.clave === clave
              ? res.ok && res.result
                ? { ...x, estado: "listo", ocr: res.result }
                : { ...x, estado: "error", error: res.message }
              : x,
          ),
        );
      });
    }
  }

  function onGuardado(clave: string, c: Comparativo, mensaje: string) {
    setComparativo(c);
    setBorradores((prev) => prev.filter((b) => b.clave !== clave));
    setAbierto(null);
    setError(null);
    setAviso(mensaje);
  }

  async function onDescartar(c: ComparativoColumna) {
    if (!c.quotationId) return;
    const suyas = new Set(c.precios.map((p) => p.quotationItemId));
    const ganados = (comparativo?.filas ?? []).filter(
      (f) => f.adjudicadoQuotationItemId && suyas.has(f.adjudicadoQuotationItemId),
    ).length;
    const r = await confirmar({
      titulo: `¿Descartar la cotización de ${c.proveedorNombre}?`,
      mensaje:
        ganados > 0
          ? `Tiene ${ganados} producto(s) adjudicado(s): quedarán sin adjudicar para elegir otra oferta.`
          : "Deja de compararse; el documento sigue en su solicitud de cotización.",
      confirmar: "Descartar",
      peligro: true,
    });
    if (!r) return;
    setProcesando(c.quotationId);
    try {
      const res = await descartarCotizacion(c.quotationId);
      if (!res.ok) {
        setError(res.message);
        return;
      }
      const nuevo = await obtenerComparativo(ids);
      if (nuevo.ok && nuevo.result) setComparativo(nuevo.result);
      setAviso("Cotización descartada.");
    } finally {
      setProcesando(null);
    }
  }

  // ---------------------------------------------------------- adjudicar y emitir

  async function onAdjudicar() {
    const porProveedor = new Map<string, number>();
    for (const f of cambios) {
      const c = cotizadas.find((x) => precioDe(x, f.consolidationId)?.quotationItemId === seleccion[f.consolidationId]);
      if (c) porProveedor.set(c.proveedorNombre, (porProveedor.get(c.proveedorNombre) ?? 0) + 1);
    }
    const reparto = [...porProveedor].map(([n, k]) => `${n} (${k})`).join(", ");
    const r = await confirmar({
      titulo: `¿Adjudicar ${cambios.length} producto(s)?`,
      mensaje:
        `${reparto}.` +
        (cotizadas.length < MAX_COTIZACIONES ? ` Hay ${cotizadas.length} de ${MAX_COTIZACIONES} cotizaciones.` : ""),
      confirmar: "Adjudicar",
    });
    if (!r) return;
    setProcesando("__adjudicar__");
    try {
      const res = await adjudicarComparativo(
        ids,
        cambios.map((f) => ({ consolidationId: f.consolidationId, quotationItemId: seleccion[f.consolidationId] })),
      );
      if (res.ok && res.result) {
        setComparativo(res.result);
        setError(null);
        setAviso("Productos adjudicados. Ya puedes emitir las órdenes de compra.");
        cargarPorEmitir();
      } else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  async function onEmitir(p: PorEmitir) {
    const r = await confirmar({
      titulo: `¿Emitir la orden de compra a ${p.proveedorNombre}?`,
      mensaje: `${p.lineas.length} producto(s) por ${dinero(p.total, p.currencyCode)}.`,
      confirmar: "Emitir OC",
    });
    if (!r) return;
    setProcesando(p.quotationId);
    try {
      const res = await emitirOrden(p.quotationId);
      if (res.ok && res.result) navigate(`/compras/ordenes/${res.result.orderId}`);
      else setError(res.message);
    } finally {
      setProcesando(null);
    }
  }

  // ---------------------------------------------------------- render

  if (cargando) {
    return (
      <div>
        <Skeleton className="mb-4 h-4 w-24" />
        <Skeleton className="mb-6 h-8 w-80" />
        <div className="mb-6 grid gap-3 md:grid-cols-3">
          {[0, 1, 2].map((k) => (
            <Skeleton key={k} className="h-36 w-full" />
          ))}
        </div>
        <Card className="overflow-hidden">
          <SkeletonTable rows={4} cols={4} />
        </Card>
      </div>
    );
  }
  if (!comparativo) {
    return (
      <div>
        <PageHeader title="Comparativo de cotizaciones" backTo="/compras" backLabel="Compras" />
        <Alert tone="danger">
          {ids.length === 0
            ? "Elige en Compras los productos que quieres comparar."
            : (error ?? "No se pudo abrir el comparativo.")}
        </Alert>
      </div>
    );
  }

  const empresa = EMPRESAS.find((e) => e.code === comparativo.empresaCode);
  const pendientes = comparativo.proveedores.filter(
    (c) => !c.quotationId && !borradores.some((b) => b.supplierFijo === c.supplierId),
  );
  const borradorAbierto = borradores.find((b) => b.clave === abierto) ?? null;
  const otrosProveedores = (excepto: string | null) =>
    [...cotizadas.map((c) => c.supplierId), ...borradores.map((b) => b.ocr?.supplierId ?? b.supplierFijo)].filter(
      (s): s is string => !!s && s !== excepto,
    );
  const emitibles = porEmitir.filter((p) => cotizadas.some((c) => c.quotationId === p.quotationId));
  const huecos = Math.max(cupos - pendientes.length, 0);

  return (
    <div>
      <PageHeader
        title="Comparativo de cotizaciones"
        backTo="/compras"
        backLabel="Compras"
        actions={
          <span className="flex items-center gap-2 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-sm font-medium text-slate-700 shadow-boton">
            <span className={`size-2 rounded-full ${empresa?.marca ?? "bg-slate-400"}`} aria-hidden="true" />
            {empresa?.label ?? comparativo.empresaCode}
            <span className="text-slate-400">·</span>
            <span className="text-slate-500">{comparativo.filas.length} producto(s)</span>
          </span>
        }
      />

      <input
        ref={inputRef}
        type="file"
        accept="application/pdf,image/jpeg,image/png,image/webp,image/heic"
        className="sr-only"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          if (files.length) agregar(files, destino.current);
        }}
      />

      {error && (
        <Alert tone="danger" className="mb-4">
          {error}
        </Alert>
      )}
      {aviso && (
        <Alert tone="good" className="mb-4">
          {aviso}
        </Alert>
      )}

      {/* ------------------------------------------------ tarjetas de cotización */}
      <div className="mb-6 grid gap-3 md:grid-cols-3">
        {cotizadas.map((c, i) => {
          const reemplazo = borradores.find((b) => b.reemplaza?.supplierId === c.supplierId);
          const total = c.precios.reduce((s, p) => s + p.lineTotal, 0);
          return (
            <Card key={c.supplierId} className="flex min-w-0 flex-col">
              <CardBody className="flex flex-1 flex-col gap-3">
                <div className="flex items-start gap-2">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-emerald-50 text-xs font-semibold text-emerald-700">
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-slate-900" title={c.proveedorNombre}>
                      {c.proveedorNombre}
                    </p>
                    <p className="font-mono text-xs text-slate-500">{c.ruc ?? c.rfqCode}</p>
                  </div>
                  <MenuAcciones
                    acciones={[
                      {
                        label: "Reemplazar por otra",
                        onClick: () => elegirArchivos({ tipo: "reemplazo", columna: c }),
                      },
                      {
                        label: "Descartar",
                        onClick: () => onDescartar(c),
                        peligro: true,
                        disabled: procesando === c.quotationId,
                      },
                    ]}
                  />
                </div>
                <div>
                  <p className="text-xl font-semibold tabular-nums tracking-tight text-slate-900">
                    {dinero(total, c.currencyCode)}
                  </p>
                  <p className="text-xs text-slate-500">
                    {c.precios.length} de {comparativo.filas.length} productos
                    {c.leadDays != null && ` · ${c.leadDays} días`}
                    {c.paymentCondition &&
                      ` · ${CONDICIONES.find((x) => x.id === c.paymentCondition)?.label ?? c.paymentCondition}`}
                  </p>
                </div>
                <div className="mt-auto flex flex-wrap items-center gap-2 text-xs">
                  {c.documentoUrl && (
                    <a
                      href={c.documentoUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 font-medium text-brand-600 hover:text-brand-700"
                    >
                      <FileText className="size-3.5" /> Ver documento
                    </a>
                  )}
                  {c.source === "OCR" && (
                    <Badge tone="azul">
                      <ScanText className="size-3" /> Leída por OCR
                    </Badge>
                  )}
                  {c.source === "ENLACE" && (
                    <Badge tone="azul">
                      <Link2 className="size-3" /> Por enlace
                    </Badge>
                  )}
                  {reemplazo && <EstadoBorrador b={reemplazo} onRevisar={() => setAbierto(reemplazo.clave)} />}
                </div>
              </CardBody>
            </Card>
          );
        })}

        {borradores
          .filter((b) => !b.reemplaza)
          .map((b, i) => {
            const nombre =
              proveedores.find((p) => p.supplierId === (b.supplierFijo ?? b.ocr?.supplierId))?.legalName ??
              b.ocr?.proveedorDetectado ??
              b.archivo.name;
            return (
              <Card key={b.clave} className="flex min-w-0 flex-col border-brand-200">
                <CardBody className="flex flex-1 flex-col gap-3">
                  <div className="flex items-start gap-2">
                    <span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-brand-50 text-xs font-semibold text-brand-700">
                      {cotizadas.length + i + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-slate-900" title={nombre}>
                        {nombre}
                      </p>
                      <p className="truncate text-xs text-slate-500">{b.archivo.name}</p>
                    </div>
                  </div>
                  <div className="mt-auto">
                    <EstadoBorrador b={b} onRevisar={() => setAbierto(b.clave)} grande />
                  </div>
                </CardBody>
              </Card>
            );
          })}

        {pendientes.map((c) => (
          <Card key={c.supplierId} className="flex min-w-0 flex-col">
            <CardBody className="flex flex-1 flex-col gap-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-slate-900" title={c.proveedorNombre}>
                  {c.proveedorNombre}
                </p>
                <p className="text-xs text-slate-500">
                  <span className="font-mono">{c.rfqCode}</span> · sin respuesta
                </p>
              </div>
              <Button
                variant="secondary"
                size="sm"
                className="mt-auto self-start"
                disabled={cupos <= 0}
                icon={<Upload className="size-3.5" />}
                onClick={() => elegirArchivos({ tipo: "proveedor", supplierId: c.supplierId })}
              >
                Subir su cotización
              </Button>
            </CardBody>
          </Card>
        ))}

        {Array.from({ length: huecos }).map((_, k) => (
          <button
            key={`hueco-${k}`}
            type="button"
            onClick={() => elegirArchivos({ tipo: "nuevas" })}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              agregar(Array.from(e.dataTransfer.files), { tipo: "nuevas" });
            }}
            className="flex min-h-36 flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 bg-white px-4 py-6 text-center text-sm text-slate-500 transition-colors hover:border-brand-400 hover:bg-brand-50/30 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          >
            <Upload className="size-5 text-slate-400" />
            <span className="font-medium text-slate-700">Cotización {MAX_COTIZACIONES - huecos + k + 1}</span>
            <span className="text-xs">PDF o foto · se lee sola</span>
          </button>
        ))}
      </div>

      {/* ------------------------------------------------ comparativo */}
      <Card className="overflow-hidden">
        <CardBody className="flex flex-wrap items-center gap-3 pb-3">
          <div className="rotulo mr-auto">Comparativo</div>
          {mixtas && (
            <label className="flex items-center gap-2 text-xs text-slate-600">
              Tipo de cambio S/ por US$
              <CampoNumero
                decimales={3} maxEnteros={3}
                value={tc}
                onChange={(v) => setTc(v)}
                className={`${inputClass} w-24 py-1 text-right`}
              />
            </label>
          )}
        </CardBody>

        {cotizadas.length === 0 ? (
          <p className="border-t border-slate-200 px-5 py-10 text-center text-sm text-slate-500">
            Sube las cotizaciones para ver aquí los precios lado a lado.
          </p>
        ) : (
          <>
            {mixtas && !tipoCambio && (
              <p className="flex items-center gap-1.5 border-t border-amber-100 bg-amber-50 px-5 py-2 text-xs text-amber-800">
                <AlertTriangle className="size-3.5" /> Hay cotizaciones en soles y en dólares: ingresa el tipo de cambio
                para compararlas.
              </p>
            )}
            <div className="overflow-x-auto border-t border-slate-200">
              <table className="tabla min-w-[40rem]">
                <thead>
                  <tr className="cabecera-tabla border-b border-slate-200">
                    <th className="px-4 py-2.5">Producto</th>
                    {cotizadas.map((c) => (
                      <th key={c.supplierId} className="w-[20%] px-4 py-2.5 text-right">
                        <span className="block truncate" title={c.proveedorNombre}>
                          {c.proveedorNombre}
                        </span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {comparativo.filas.map((f) => {
                    const elegible = f.status === "EN_COTIZACION" || f.status === "ADJUDICADA";
                    return (
                      <tr key={f.consolidationId}>
                        <td className="min-w-52 max-w-0 px-4 py-2.5">
                          <p className="truncate text-slate-800" title={f.nombreProducto}>
                            {f.nombreProducto}
                            {f.atributo && <span className="text-slate-400"> · {f.atributo}</span>}
                          </p>
                          <p className="flex items-center gap-2 whitespace-nowrap text-xs text-slate-500">
                            <span className="font-mono">{f.codigo}</span>
                            <span>
                              {cantidad(f.totalQty)} {f.unidadMedida}
                            </span>
                            {!elegible && <ConsolidadoEstadoBadge estado={f.status} />}
                          </p>
                        </td>
                        {cotizadas.map((c) => {
                          const p = precioDe(c, f.consolidationId);
                          if (!p) {
                            return (
                              <td key={c.supplierId} className="px-4 py-2.5 text-right text-xs text-slate-400">
                                No cotiza
                              </td>
                            );
                          }
                          const mejor = mejores[f.consolidationId] === p.quotationItemId;
                          const elegido = seleccion[f.consolidationId] === p.quotationItemId;
                          const adjudicado = f.adjudicadoQuotationItemId === p.quotationItemId;
                          return (
                            <td key={c.supplierId} className="p-1.5 text-right">
                              <button
                                type="button"
                                disabled={!elegible}
                                aria-pressed={elegido}
                                onClick={() => setElecciones((s) => ({ ...s, [f.consolidationId]: p.quotationItemId }))}
                                className={`flex w-full flex-col items-end rounded-md px-2.5 py-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 ${
                                  elegido
                                    ? "bg-brand-50 ring-1 ring-inset ring-brand-300"
                                    : elegible
                                      ? "hover:bg-slate-50"
                                      : ""
                                }`}
                              >
                                <span
                                  className={`flex items-center gap-1 font-medium ${mejor ? "text-emerald-700" : "text-slate-800"}`}
                                >
                                  {adjudicado ? (
                                    <Check className="size-3.5 text-brand-700" aria-label="Adjudicado" />
                                  ) : (
                                    mejor && <Trophy className="size-3.5" aria-label="Mejor precio" />
                                  )}
                                  {dinero(p.unitCost, p.currencyCode)}
                                </span>
                                <span className="text-xs text-slate-400">{dinero(p.lineTotal, p.currencyCode)}</span>
                              </button>
                            </td>
                          );
                        })}
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t border-slate-200 bg-slate-50/70">
                    <td className="px-4 py-2.5 text-xs font-medium text-slate-500">Total por proveedor</td>
                    {cotizadas.map((c) => (
                      <td key={c.supplierId} className="px-4 py-2.5 text-right">
                        <p className="font-semibold text-slate-900">
                          {dinero(
                            c.precios.reduce((s, p) => s + p.lineTotal, 0),
                            c.currencyCode,
                          )}
                        </p>
                        {c.precios.length < comparativo.filas.length && (
                          <p className="text-[0.6875rem] text-slate-400">
                            {c.precios.length} de {comparativo.filas.length} productos
                          </p>
                        )}
                      </td>
                    ))}
                  </tr>
                </tfoot>
              </table>
            </div>

            <div className="flex flex-col gap-3 border-t border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="text-sm">
                <p className="text-slate-600">
                  Selección:{" "}
                  <b className="tabular-nums text-slate-900">
                    {totalComparable ? dinero(totalSeleccion, monedaTotal) : "—"}
                  </b>
                </p>
                {totalComparable && ahorro > 0.005 && (
                  <p className="text-xs text-emerald-700">
                    Ahorro de {dinero(ahorro, monedaTotal)} frente a la oferta más alta de cada producto
                  </p>
                )}
              </div>
              <Button
                icon={<Trophy className="size-4" />}
                disabled={cambios.length === 0 || procesando !== null}
                loading={procesando === "__adjudicar__"}
                onClick={onAdjudicar}
              >
                {cambios.length > 0 ? `Adjudicar ${cambios.length} producto(s)` : "Adjudicado"}
              </Button>
            </div>
          </>
        )}
      </Card>

      {emitibles.length > 0 && (
        <Card className="mt-6">
          <CardBody className="flex flex-col gap-3">
            <div className="rotulo">Listos para orden de compra</div>
            {emitibles.map((p) => (
              <div
                key={p.quotationId}
                className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-medium text-slate-900">{p.proveedorNombre}</p>
                  <p className="text-xs text-slate-500">
                    {p.lineas.length} producto(s)
                    {p.supplierQuoteNumber ? ` · Cotización N° ${p.supplierQuoteNumber}` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <span className="text-sm font-medium tabular-nums text-slate-800">
                    {dinero(p.total, p.currencyCode)}
                  </span>
                  <Button size="sm" loading={procesando === p.quotationId} onClick={() => onEmitir(p)}>
                    Emitir OC
                  </Button>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      )}

      {borradorAbierto && borradorAbierto.estado !== "leyendo" && (
        <EditorCotizacion
          ids={ids}
          filas={filasCotizables}
          archivo={borradorAbierto.archivo}
          ocr={borradorAbierto.ocr}
          proveedores={proveedores}
          monedas={monedas}
          supplierFijo={borradorAbierto.supplierFijo}
          reemplaza={borradorAbierto.reemplaza}
          excluir={otrosProveedores(borradorAbierto.supplierFijo ?? borradorAbierto.ocr?.supplierId ?? null)}
          onGuardado={(c, m) => onGuardado(borradorAbierto.clave, c, m)}
          onCerrar={() => setAbierto(null)}
        />
      )}
    </div>
  );
}

function EstadoBorrador({ b, onRevisar, grande = false }: { b: Borrador; onRevisar: () => void; grande?: boolean }) {
  if (b.estado === "leyendo") {
    return (
      <span className="inline-flex items-center gap-1.5 text-xs text-slate-500">
        <Loader2 className="size-3.5 animate-spin motion-reduce:animate-none" /> Leyendo la cotización…
      </span>
    );
  }
  const leidos = b.ocr ? Object.keys(b.ocr.precios).length : 0;
  return (
    <div className="flex flex-col items-start gap-2">
      {grande && (
        <p className={`text-xs ${b.estado === "error" ? "text-amber-700" : "text-slate-500"}`}>
          {b.estado === "error" ? "No se pudo leer: ingresa los precios a mano." : `${leidos} precio(s) leído(s)`}
        </p>
      )}
      <Button size="sm" variant={grande ? "primary" : "secondary"} onClick={onRevisar}>
        {b.reemplaza ? "Revisar reemplazo" : "Revisar y guardar"}
      </Button>
    </div>
  );
}
