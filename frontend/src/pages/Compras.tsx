import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ClipboardList, FileQuestion, Layers, ListTodo, Scale, Send, Wallet } from "lucide-react";
import { listarCajaChica, obtenerReglaCajaChica } from "../api/cajachica";
import {
  decidirRuta,
  dividirNecesidad,
  emitirOrden,
  listarConsolidados,
  listarNecesidades,
  listarPorEmitir,
  listarRfqsAbiertas,
  prepararSolicitudes,
} from "../api/compras";
import type { CompraCajaChica, Consolidado, Necesidad, PorEmitir, ReglaCajaChica, Rfq } from "../api/types";
import { fmtDia } from "../components/formato";
import PestanaCajaChica from "../components/cajachica/PestanaCajaChica";
import { ConsolidadoEstadoBadge, OrigenesConsolidado } from "../components/compras/etiquetas";
import { cantidad, dinero, ETIQUETA_SUGERENCIA } from "../components/compras/formato";
import { EMPRESAS } from "../components/empresas";
import PrioridadBadge from "../components/PrioridadBadge";
import RfqEstadoBadge from "../components/RfqEstadoBadge";
import Alert from "../components/ui/Alert";
import Badge from "../components/ui/Badge";
import Button from "../components/ui/Button";
import { Card, CardBody } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import EmptyState from "../components/ui/EmptyState";
import MenuAcciones from "../components/ui/MenuAcciones";
import CampoNumero from "../components/ui/CampoNumero";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

type Pestana = "necesidades" | "consolidados" | "solicitudes" | "caja";
const PESTANAS: Pestana[] = ["necesidades", "consolidados", "solicitudes", "caja"];

type Parte = "necesidades" | "consolidados" | "porEmitir" | "rfqs" | "caja" | "regla";
const TODAS: readonly Parte[] = ["necesidades", "consolidados", "porEmitir", "rfqs", "caja", "regla"];

const th = "px-5 py-3";
const cabecera =
  "border-b border-slate-200 cabecera-tabla";
const linkClass = "text-sm font-medium text-brand-600 hover:text-brand-700";

/**
 * Compras (Lote 16, DEC-L039): necesidades por decidir ruta, consolidados entre
 * requerimientos (preparar RFQ, adjudicar, emitir OC), solicitudes de
 * cotizacion y Caja Chica.
 */
export default function Compras() {
  const confirmar = useConfirmar();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = (PESTANAS.includes(params.get("tab") as Pestana) ? params.get("tab") : "consolidados") as Pestana;

  const [necesidades, setNecesidades] = useState<Necesidad[]>([]);
  const [consolidados, setConsolidados] = useState<Consolidado[]>([]);
  const [porEmitir, setPorEmitir] = useState<PorEmitir[]>([]);
  const [rfqs, setRfqs] = useState<Rfq[]>([]);
  const [compras, setCompras] = useState<CompraCajaChica[]>([]);
  const [regla, setRegla] = useState<ReglaCajaChica | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set());
  const [procesando, setProcesando] = useState<string | null>(null);

  // Abrir trae todo (los conteos de las pestanas lo necesitan); cada accion
  // recarga solo las listas que cambia.
  const cargar = useCallback((partes: readonly Parte[] = TODAS) => {
    const pide = new Set(partes);
    const nada = Promise.resolve(null);
    return Promise.all([
      pide.has("necesidades") ? listarNecesidades() : nada,
      pide.has("consolidados") ? listarConsolidados() : nada,
      pide.has("porEmitir") ? listarPorEmitir() : nada,
      pide.has("rfqs") ? listarRfqsAbiertas() : nada,
      pide.has("caja") ? listarCajaChica() : nada,
      pide.has("regla") ? obtenerReglaCajaChica() : nada,
    ]).then(([nec, con, emi, rfq, caja, reg]) => {
      const fallo = [nec, con, emi, rfq].find((r) => r && !r.ok);
      setError(fallo ? fallo.message : null);
      if (nec?.ok) setNecesidades(nec.result ?? []);
      if (con?.ok) setConsolidados(con.result ?? []);
      if (emi?.ok) setPorEmitir(emi.result ?? []);
      if (rfq?.ok) setRfqs(rfq.result ?? []);
      if (caja?.ok) setCompras(caja.result ?? []);
      if (reg?.ok) setRegla(reg.result);
    });
  }, []);

  useEffect(() => {
    cargar().finally(() => setCargando(false));
  }, [cargar]);

  function irA(id: Pestana) {
    setParams(id === "consolidados" ? {} : { tab: id }, { replace: true });
    setAviso(null);
  }

  async function ejecutar(
    clave: string,
    accion: () => Promise<{ ok: boolean; message: string }>,
    recargar: readonly Parte[],
  ) {
    setProcesando(clave);
    setAviso(null);
    try {
      const res = await accion();
      if (!res.ok) {
        setError(res.message);
        return false;
      }
      setError(null);
      setAviso(res.message);
      await cargar(recargar);
      return true;
    } finally {
      setProcesando(null);
    }
  }

  async function onRuta(n: Necesidad, ruta: "COMPRA" | "CAJA_CHICA" | "STOCK") {
    await ejecutar(n.needId, () => decidirRuta(n.needId, ruta), ["necesidades", "consolidados"]);
  }

  async function onDividir(n: Necesidad, unidades: number) {
    return ejecutar(n.needId, () => dividirNecesidad(n.needId, unidades), ["necesidades"]);
  }

  async function onPreparar() {
    const ids = Array.from(seleccion);
    const ok = await ejecutar("__preparar__", () => prepararSolicitudes(ids), ["necesidades", "consolidados", "rfqs"]);
    if (ok) {
      setSeleccion(new Set());
      irA("solicitudes");
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

  const abiertos = useMemo(() => consolidados.filter((c) => c.status === "ABIERTA"), [consolidados]);
  const porDecidir = necesidades.filter((n) => n.status === "PENDIENTE_RUTA").length;
  const porRegularizar = compras.filter((c) => c.estado === "PENDIENTE_REGULARIZACION" || c.estado === "OBSERVADO").length;
  const porEnviar = rfqs.filter((r) => r.status === "BORRADOR").length;

  const pestanas: { id: Pestana; label: string; icon: typeof Layers; conteo: number; tono: "gris" | "amarillo" }[] = [
    { id: "necesidades", label: "Necesidades", icon: ListTodo, conteo: porDecidir, tono: "amarillo" },
    { id: "consolidados", label: "Consolidados", icon: Layers, conteo: abiertos.length + porEmitir.length, tono: "gris" },
    { id: "solicitudes", label: "Solicitudes", icon: FileQuestion, conteo: porEnviar, tono: "amarillo" },
    { id: "caja", label: "Caja chica", icon: Wallet, conteo: porRegularizar, tono: "amarillo" },
  ];

  return (
    <div>
      <PageHeader
        title="Compras"
        actions={
          <Button href="/compras/ordenes" variant="secondary" icon={<ClipboardList className="size-4" />}>
            Órdenes emitidas
          </Button>
        }
      />

      <div role="tablist" className="mb-4 flex flex-wrap gap-1 border-b border-slate-200">
        {pestanas.map(({ id, label, icon: Icon, conteo, tono }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => irA(id)}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              tab === id ? "border-brand-600 text-brand-700" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="size-4" />
            {label}
            {!cargando && conteo > 0 && <Badge tone={tono}>{conteo}</Badge>}
          </button>
        ))}
      </div>

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {aviso && <Alert tone="good" className="mb-4">{aviso}</Alert>}

      {cargando ? (
        <Card className="overflow-hidden">
          <SkeletonTable cols={6} />
        </Card>
      ) : tab === "caja" ? (
        <PestanaCajaChica
          compras={compras}
          regla={regla}
          onCompra={(c) => setCompras((prev) => prev.map((x) => (x.id === c.id ? c : x)))}
          onRegla={setRegla}
        />
      ) : tab === "necesidades" ? (
        <TablaNecesidades necesidades={necesidades} procesando={procesando} onRuta={onRuta} onDividir={onDividir} />
      ) : tab === "solicitudes" ? (
        <TablaSolicitudes rfqs={rfqs} />
      ) : (
        <div className="flex flex-col gap-6">
          {porEmitir.length > 0 && <ListosParaOc porEmitir={porEmitir} procesando={procesando} onEmitir={onEmitir} />}
          <TablaConsolidados
            consolidados={consolidados}
            seleccion={seleccion}
            onSeleccion={setSeleccion}
            onPreparar={onPreparar}
            preparando={procesando === "__preparar__"}
          />
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ Necesidades

function TablaNecesidades({
  necesidades,
  procesando,
  onRuta,
  onDividir,
}: {
  necesidades: Necesidad[];
  procesando: string | null;
  onRuta: (n: Necesidad, ruta: "COMPRA" | "CAJA_CHICA" | "STOCK") => void;
  onDividir: (n: Necesidad, unidades: number) => Promise<boolean | undefined>;
}) {
  // DEC-L054: separar unidades de una necesidad para darle otra ruta.
  const [dividiendo, setDividiendo] = useState<string | null>(null);
  const [unidades, setUnidades] = useState("");
  const dividir = (n: Necesidad) =>
    n.qty > 1 ? [{ label: "Dividir…", onClick: () => (setDividiendo(n.needId), setUnidades("")) }] : [];
  if (necesidades.length === 0) {
    return (
      <Card>
        <EmptyState icon={ListTodo} title="No hay necesidades por atender" />
      </Card>
    );
  }
  return (
    <Card className="overflow-hidden">
      <table className="tabla">
        <thead>
          <tr className={cabecera}>
            <th className={th}>Requerimiento</th>
            <th className={th}>CECO</th>
            <th className={th}>Producto</th>
            <th className={th}>Cantidad</th>
            <th className={th}>Ruta</th>
            <th className={th} />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {necesidades.map((n) => (
            <tr key={n.needId} className="hover:bg-slate-50">
              <td className="whitespace-nowrap px-5 py-3">
                <Link to={`/requerimientos/${n.requestId}`} className="font-mono text-xs font-medium text-brand-600 hover:text-brand-700">
                  {n.requestCode}
                </Link>
                <div className="mt-1">
                  <PrioridadBadge prioridad={n.priority} />
                </div>
              </td>
              <td className="col-flex px-5 py-3 text-xs" title={`${n.cecoCode} ${n.cecoNombre}`}>
                <span className="font-mono font-medium text-slate-700">{n.cecoCode}</span>{" "}
                <span className="text-slate-500">{n.cecoNombre}</span>
              </td>
              <td className="col-flex px-5 py-3" title={n.nombreProducto}>
                <p className="text-sm text-slate-700">{n.nombreProducto}</p>
                <p className="font-mono text-xs text-slate-500">{n.codigoVariante ?? n.skuBase}</p>
              </td>
              <td className="whitespace-nowrap px-5 py-3 text-slate-700">
                {cantidad(n.qty)} {n.unidadMedida}
                {(n.stockDisponible ?? 0) > 0 && (
                  <p className="mt-1 text-xs text-slate-500">
                    Almacén: {cantidad(n.stockDisponible ?? 0)} disp. ·{" "}
                    <button
                      type="button"
                      disabled={procesando === n.needId}
                      onClick={() => onRuta(n, "STOCK")}
                      className="font-medium text-brand-600 hover:text-brand-700 disabled:opacity-50"
                    >
                      Usar
                    </button>
                  </p>
                )}
              </td>
              <td className="px-5 py-3">
                {n.status === "CAJA_CHICA" ? (
                  <Badge tone="amarillo">Compra del proyecto</Badge>
                ) : (
                  <Badge tone="gris">Por decidir</Badge>
                )}
                {n.suggestedReason && (
                  <p className="mt-1 text-xs text-amber-700">
                    {n.suggestedReason === "SOLICITANTE" ? "" : "Sugerido: "}
                    {ETIQUETA_SUGERENCIA[n.suggestedReason]}
                  </p>
                )}
                {n.notaSolicitante && (
                  <p className="mt-0.5 max-w-56 text-xs text-slate-500" title={n.notaSolicitante}>
                    «{n.notaSolicitante}»
                  </p>
                )}
              </td>
              <td className="whitespace-nowrap px-5 py-3 text-right">
                {dividiendo === n.needId ? (
                  <div className="flex items-center justify-end gap-2">
                    <span className="text-xs text-slate-500">Separar</span>
                    <CampoNumero
                      decimales={0}
                      max={n.qty - 1}
                      value={unidades}
                      onChange={setUnidades}
                      autoFocus
                      aria-label={`Unidades a separar de ${n.codigoVariante ?? n.skuBase}`}
                      className={`${inputClass} w-16 py-1 text-sm`}
                    />
                    <span className="text-xs text-slate-500">de {cantidad(n.qty)}</span>
                    <Button
                      size="sm"
                      loading={procesando === n.needId}
                      disabled={!(Number(unidades) > 0 && Number(unidades) < n.qty)}
                      onClick={async () => {
                        if (await onDividir(n, Number(unidades))) setDividiendo(null);
                      }}
                    >
                      Dividir
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setDividiendo(null)}>
                      Cancelar
                    </Button>
                  </div>
                ) : n.status === "CAJA_CHICA" ? (
                  <div className="flex items-center justify-end gap-1">
                    <Link to={`/requerimientos/${n.requestId}/caja-chica`} className={linkClass}>
                      Ver compra
                    </Link>
                    <MenuAcciones
                      acciones={[
                        { label: "Pasar a compra normal", onClick: () => onRuta(n, "COMPRA"), disabled: procesando === n.needId },
                        ...dividir(n),
                      ]}
                    />
                  </div>
                ) : (
                  <div className="flex justify-end gap-2">
                    <Button size="sm" loading={procesando === n.needId} onClick={() => onRuta(n, "CAJA_CHICA")}>
                      Caja chica del proyecto
                    </Button>
                    <Button size="sm" variant="secondary" disabled={procesando === n.needId} onClick={() => onRuta(n, "COMPRA")}>
                      Compra normal
                    </Button>
                    {n.qty > 1 && <MenuAcciones acciones={dividir(n)} />}
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

// ------------------------------------------------------------------ Consolidados

function ListosParaOc({
  porEmitir,
  procesando,
  onEmitir,
}: {
  porEmitir: PorEmitir[];
  procesando: string | null;
  onEmitir: (p: PorEmitir) => void;
}) {
  return (
    <Card>
      <CardBody className="flex flex-col gap-3">
        <div className="rotulo">Listos para orden de compra</div>
        {porEmitir.map((p) => (
          <div
            key={p.quotationId}
            className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div className="min-w-0">
              <p className="font-medium text-slate-900">{p.proveedorNombre}</p>
              <p className="text-xs text-slate-500">
                {p.empresaCode}
                {p.supplierQuoteNumber ? ` · Cotización N° ${p.supplierQuoteNumber}` : ""} ·{" "}
                {p.lineas.map((l) => `${l.codigoVariante ?? l.skuBase} × ${cantidad(l.qty)}`).join(", ")}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span className="text-sm font-medium text-slate-800">{dinero(p.total, p.currencyCode)}</span>
              <Button size="sm" loading={procesando === p.quotationId} onClick={() => onEmitir(p)}>
                Emitir OC
              </Button>
            </div>
          </div>
        ))}
      </CardBody>
    </Card>
  );
}

/** Se pueden comparar los que aun no tienen OC (DEC-L047). */
const COMPARABLES = new Set(["ABIERTA", "EN_COTIZACION", "ADJUDICADA"]);

function TablaConsolidados({
  consolidados,
  seleccion,
  onSeleccion,
  onPreparar,
  preparando,
}: {
  consolidados: Consolidado[];
  seleccion: Set<string>;
  onSeleccion: (s: Set<string>) => void;
  onPreparar: () => void;
  preparando: boolean;
}) {
  const navigate = useNavigate();
  const [empresa, setEmpresa] = useState("");
  const empresas = useMemo(
    () =>
      EMPRESAS.map((e) => ({
        ...e,
        n: consolidados.filter((c) => c.empresaCode === e.code && COMPARABLES.has(c.status)).length,
      })).filter((e) => e.n > 0),
    [consolidados],
  );
  // Comparar y pedir cotizacion es por empresa (cada una compra con su RFQ): se trabaja una a la vez.
  const empresaActiva = empresas.some((e) => e.code === empresa) ? empresa : (empresas[0]?.code ?? "");

  if (consolidados.length === 0) {
    return (
      <Card>
        <EmptyState icon={Layers} title="No hay faltantes consolidados" />
      </Card>
    );
  }
  const visibles = consolidados.filter((c) => !empresaActiva || c.empresaCode === empresaActiva);
  const seleccionables = visibles.filter((c) => COMPARABLES.has(c.status));
  const elegidos = seleccionables.filter((c) => seleccion.has(c.consolidationId));
  const todos = seleccionables.length > 0 && seleccionables.every((c) => seleccion.has(c.consolidationId));
  const soloAbiertos = elegidos.length > 0 && elegidos.every((c) => c.status === "ABIERTA");

  function alternar(id: string) {
    const s = new Set(seleccion);
    if (s.has(id)) s.delete(id);
    else s.add(id);
    onSeleccion(s);
  }

  function cambiarEmpresa(code: string) {
    setEmpresa(code);
    onSeleccion(new Set());
  }

  return (
    <Card className="overflow-hidden">
      {empresas.length > 1 && (
        <div role="tablist" aria-label="Empresa" className="flex gap-1 overflow-x-auto border-b border-slate-200 px-3 pt-2">
          {empresas.map((e) => (
            <button
              key={e.code}
              type="button"
              role="tab"
              aria-selected={empresaActiva === e.code}
              onClick={() => cambiarEmpresa(e.code)}
              className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                empresaActiva === e.code
                  ? "border-brand-600 text-slate-900"
                  : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              <span className={`size-2 rounded-full ${e.marca}`} aria-hidden="true" />
              {e.label}
              <span className="font-mono text-xs tabular-nums text-slate-400">{e.n}</span>
            </button>
          ))}
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
        <span className="text-sm text-slate-500">
          {elegidos.length > 0 ? `${elegidos.length} seleccionado(s)` : "Selecciona los productos a cotizar"}
        </span>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="secondary"
            icon={<Send className="size-3.5" />}
            disabled={!soloAbiertos}
            loading={preparando}
            onClick={onPreparar}
            title="Crea una solicitud por cada proveedor habilitado para enviarla por correo o WhatsApp"
          >
            Pedir cotización
          </Button>
          <Button
            size="sm"
            icon={<Scale className="size-3.5" />}
            disabled={elegidos.length === 0}
            onClick={() => navigate(`/compras/comparativo?ids=${elegidos.map((c) => c.consolidationId).join(",")}`)}
          >
            Comparar cotizaciones
          </Button>
        </div>
      </div>
      <table className="tabla">
        <thead>
          <tr className={`${cabecera} border-t`}>
            <th className="w-10 px-5 py-3">
              <input
                type="checkbox"
                aria-label="Seleccionar todos"
                checked={todos}
                disabled={seleccionables.length === 0}
                onChange={() => onSeleccion(todos ? new Set() : new Set(seleccionables.map((c) => c.consolidationId)))}
              />
            </th>
            <th className={th}>Producto</th>
            <th className={th}>Origen (CECO · REQ)</th>
            <th className={th}>Total</th>
            <th className={th}>Cotizado</th>
            <th className={`${th} col-sec`}>Proveedores</th>
            <th className={th}>Estado</th>
            <th className={th} />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {visibles.map((c) => {
            const abierto = COMPARABLES.has(c.status);
            return (
              <tr key={c.consolidationId} className="align-top hover:bg-slate-50">
                <td className="px-5 py-3">
                  {abierto && (
                    <input
                      type="checkbox"
                      aria-label={`Seleccionar ${c.codigoVariante ?? c.skuBase}`}
                      checked={seleccion.has(c.consolidationId)}
                      onChange={() => alternar(c.consolidationId)}
                    />
                  )}
                </td>
                <td className="col-flex px-5 py-3" title={c.nombreProducto}>
                  <p className="text-sm text-slate-700">{c.nombreProducto}</p>
                  <p className="font-mono text-xs text-slate-500">
                    {c.codigoVariante ?? c.skuBase}
                  </p>
                </td>
                <td className="px-5 py-3">
                  <OrigenesConsolidado origenes={c.origenes} />
                </td>
                <td className="whitespace-nowrap px-5 py-3 font-medium text-slate-800">
                  {cantidad(c.totalQty)} {c.unidadMedida}
                </td>
                <td className="whitespace-nowrap px-5 py-3">
                  {c.cantidadCotizada == null ? (
                    <span className="text-slate-400">—</span>
                  ) : (
                    <>
                      <p
                        className={`font-medium ${
                          c.cantidadCotizada >= c.totalQty ? "text-emerald-700" : "text-amber-700"
                        }`}
                      >
                        {cantidad(c.cantidadCotizada)} {c.unidadMedida}
                      </p>
                      <p className="text-xs text-slate-500">
                        {c.cotizaciones} {c.cotizaciones === 1 ? "oferta" : "ofertas"}
                      </p>
                    </>
                  )}
                </td>
                <td className="col-sec px-5 py-3 text-xs" title={c.proveedores.join(", ")}>
                  {c.proveedores.length === 0 ? (
                    <span className="text-rose-600">Sin proveedor</span>
                  ) : (
                    <span className="text-slate-600">{c.proveedores.length} habilitado(s)</span>
                  )}
                </td>
                <td className="px-5 py-3">
                  <ConsolidadoEstadoBadge estado={c.status} />
                  {c.adjudicado && (
                    <p className="mt-1 max-w-40 truncate text-xs text-slate-500" title={c.adjudicado.proveedorNombre}>
                      {c.adjudicado.proveedorNombre}
                    </p>
                  )}
                </td>
                <td className="whitespace-nowrap px-5 py-3 text-right">
                  <Link to={`/compras/consolidados/${c.consolidationId}`} className={linkClass}>
                    Ver
                  </Link>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

// ------------------------------------------------------------------ Solicitudes

function TablaSolicitudes({ rfqs }: { rfqs: Rfq[] }) {
  if (rfqs.length === 0) {
    return (
      <Card>
        <EmptyState icon={FileQuestion} title="No hay solicitudes de cotización en curso" />
      </Card>
    );
  }
  return (
    <Card className="overflow-hidden">
      <table className="tabla">
        <thead>
          <tr className={cabecera}>
            <th className={th}>Solicitud</th>
            <th className={th}>Proveedor</th>
            <th className={`${th} col-sec`}>Requerimientos</th>
            <th className={`${th} col-sec`}>Enviada</th>
            <th className={th}>Estado</th>
            <th className={th} />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rfqs.map((r) => (
            <tr key={r.rfqId} className="hover:bg-slate-50">
              <td className="whitespace-nowrap px-5 py-3">
                <p className="font-mono text-xs text-slate-700">{r.rfqCode}</p>
                <p className="text-xs text-slate-400">{r.empresaCode}</p>
              </td>
              <td className="col-flex px-5 py-3 text-slate-600" title={r.proveedorNombre}>
                {r.proveedorNombre}
              </td>
              <td className="col-sec col-flex px-5 py-3 font-mono text-xs text-slate-500" title={r.requestCode ?? ""}>
                {r.requestCode ?? "—"}
              </td>
              <td className="col-sec whitespace-nowrap px-5 py-3 text-xs text-slate-400">
                {r.sentAt ? fmtDia(r.sentAt.slice(0, 10)) : "—"}
              </td>
              <td className="px-5 py-3">
                <RfqEstadoBadge estado={r.status} />
              </td>
              <td className="whitespace-nowrap px-5 py-3 text-right">
                <Link to={`/compras/rfq/${r.rfqId}`} className={linkClass}>
                  {r.status === "BORRADOR" ? "Enviar" : "Cotizaciones"}
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}
