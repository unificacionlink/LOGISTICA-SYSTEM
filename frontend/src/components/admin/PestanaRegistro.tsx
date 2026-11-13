import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { ExternalLink, RefreshCw, ScrollText, Search, X } from "lucide-react";
import {
  listarActividad,
  listarCambios,
  listarErrores,
  listarMovimientos,
  obtenerResumenRegistro,
  type ActividadRegistro,
  type CambioRegistro,
  type ErrorRegistro,
  type FiltrosRegistro,
  type MovimientoRegistro,
  type PaginaRegistro,
  type ResumenRegistro,
} from "../../api/registro";
import type { ApiResponse } from "../../api/http";
import { hoyIso } from "../formato";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import { inputClass } from "../ui/Field";
import { Skeleton, SkeletonTable } from "../ui/Skeleton";
import {
  ETIQUETA_METODO,
  TIPOS_MOVIMIENTO,
  enlaceEntidad,
  enlaceRuta,
  etiquetaAccion,
  etiquetaAccionCambio,
  etiquetaEntidad,
  etiquetaModulo,
  fechaHora,
  jsonLegible,
  navegadorCorto,
  resultado,
} from "./registro/formato";

/**
 * Registro de la plataforma para ADMIN (DEC-L060): operaciones, ingresos,
 * cambios criticos, errores y movimientos de inventario. Una vista a la vez,
 * con periodo, busqueda y un filtro propio; el detalle completo de cada fila
 * se abre al tocarla.
 */

type Vista = "actividad" | "ingresos" | "cambios" | "errores" | "movimientos";
type Fila = ActividadRegistro | CambioRegistro | ErrorRegistro | MovimientoRegistro;

const VISTAS: { id: Vista; label: string }[] = [
  { id: "actividad", label: "Operaciones" },
  { id: "ingresos", label: "Ingresos" },
  { id: "cambios", label: "Cambios" },
  { id: "errores", label: "Errores" },
  { id: "movimientos", label: "Movimientos" },
];

const TIPOS: Record<Vista, { valor: string; label: string }[]> = {
  actividad: [
    { valor: "", label: "Todas" },
    { valor: "RECHAZADAS", label: "Rechazadas o con error" },
  ],
  ingresos: [
    { valor: "INGRESOS", label: "Todos" },
    { valor: "INGRESOS_FALLIDOS", label: "Fallidos" },
  ],
  cambios: [],
  errores: [
    { valor: "", label: "Todos" },
    { valor: "SERVIDOR", label: "Del servidor" },
    { valor: "NAVEGADOR", label: "De la pantalla" },
  ],
  movimientos: [
    { valor: "", label: "Todos" },
    ...Object.entries(TIPOS_MOVIMIENTO).map(([valor, label]) => ({ valor, label })),
  ],
};

const PERIODOS = [
  { valor: "0", label: "Hoy" },
  { valor: "6", label: "Últimos 7 días" },
  { valor: "29", label: "Últimos 30 días" },
  { valor: "89", label: "Últimos 90 días" },
  { valor: "rango", label: "Elegir fechas" },
];

function restarDias(iso: string, dias: number): string {
  const [a, m, d] = iso.split("-").map(Number);
  const f = new Date(a, m - 1, d - dias);
  return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, "0")}-${String(f.getDate()).padStart(2, "0")}`;
}

function cargar(vista: Vista, f: FiltrosRegistro): Promise<ApiResponse<PaginaRegistro<Fila>>> {
  switch (vista) {
    case "cambios":
      return listarCambios(f);
    case "errores":
      return listarErrores(f);
    case "movimientos":
      return listarMovimientos(f);
    case "ingresos":
      return listarActividad({ ...f, tipo: f.tipo || "INGRESOS" });
    default:
      return listarActividad(f);
  }
}

interface Resultado {
  clave: string;
  filas: Fila[];
  hayMas: boolean;
  pagina: number;
  error: string | null;
}

export default function PestanaRegistro() {
  const [vista, setVista] = useState<Vista>("actividad");
  const [periodo, setPeriodo] = useState("6");
  const [rango, setRango] = useState({ desde: restarDias(hoyIso(), 6), hasta: hoyIso() });
  const [tipo, setTipo] = useState("");
  const [escrito, setEscrito] = useState("");
  const [q, setQ] = useState("");
  const [usuario, setUsuario] = useState<{ id: string; nombre: string } | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [resultadoActual, setResultado] = useState<Resultado | null>(null);
  const [masCargando, setMasCargando] = useState(false);
  const [resumen, setResumen] = useState<ResumenRegistro | null>(null);
  const [abierta, setAbierta] = useState<Fila | null>(null);

  const hoy = hoyIso();
  const desde = periodo === "rango" ? rango.desde : restarDias(hoy, Number(periodo));
  const hasta = periodo === "rango" ? rango.hasta : hoy;
  const filtros: FiltrosRegistro = { desde, hasta, usuario: usuario?.id ?? "", q, tipo, pagina: 0 };
  const clave = JSON.stringify({ vista, ...filtros, recarga });
  const cargando = resultadoActual?.clave !== clave;

  // Busqueda: espera a que se deje de escribir.
  useEffect(() => {
    const t = window.setTimeout(() => setQ(escrito), 350);
    return () => window.clearTimeout(t);
  }, [escrito]);

  useEffect(() => {
    let vigente = true;
    const f: FiltrosRegistro = JSON.parse(clave);
    cargar(vista, { ...f, pagina: 0 }).then((res) => {
      if (!vigente) return;
      setResultado({
        clave,
        filas: res.ok && res.result ? res.result.filas : [],
        hayMas: !!res.result?.hayMas,
        pagina: 0,
        error: res.ok ? null : res.message,
      });
    });
    return () => {
      vigente = false;
    };
  }, [clave, vista]);

  useEffect(() => {
    obtenerResumenRegistro().then((res) => {
      if (res.ok && res.result) setResumen(res.result);
    });
  }, [recarga]);

  async function cargarMas() {
    if (!resultadoActual) return;
    setMasCargando(true);
    try {
      const pagina = resultadoActual.pagina + 1;
      const res = await cargar(vista, { ...filtros, pagina });
      if (res.ok && res.result) {
        const extra = res.result;
        setResultado((prev) =>
          prev && prev.clave === clave
            ? { ...prev, filas: [...prev.filas, ...extra.filas], hayMas: extra.hayMas, pagina }
            : prev,
        );
      }
    } finally {
      setMasCargando(false);
    }
  }

  function irA(v: Vista, t = "") {
    setVista(v);
    setTipo(t);
  }

  function filtrarUsuario(id: string | null, nombre: string | null) {
    if (!id) return;
    setUsuario({ id, nombre: nombre ?? "Usuario" });
    setAbierta(null);
  }

  const tipos = TIPOS[vista];

  return (
    <div className="flex flex-col gap-4">
      <Indicadores resumen={resumen} irA={irA} />

      <Card className="overflow-hidden">
        <div role="tablist" aria-label="Vista del registro" className="flex gap-1 overflow-x-auto border-b border-slate-200 px-3 pt-2">
          {VISTAS.map((v) => (
            <button
              key={v.id}
              type="button"
              role="tab"
              aria-selected={vista === v.id}
              onClick={() => irA(v.id, v.id === "ingresos" ? "INGRESOS" : "")}
              className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                vista === v.id ? "border-brand-600 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              {v.label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2 px-4 py-3">
          <div className="relative min-w-[14rem] flex-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <input
              value={escrito}
              onChange={(e) => setEscrito(e.target.value)}
              placeholder="Buscar usuario, ruta, mensaje, producto…"
              aria-label="Buscar en el registro"
              className={`${inputClass} w-full pl-8`}
            />
          </div>
          <select aria-label="Periodo" value={periodo} onChange={(e) => setPeriodo(e.target.value)} className={`${inputClass} w-auto`}>
            {PERIODOS.map((p) => (
              <option key={p.valor} value={p.valor}>
                {p.label}
              </option>
            ))}
          </select>
          {periodo === "rango" && (
            <>
              <input
                type="date"
                aria-label="Desde"
                value={rango.desde}
                max={rango.hasta}
                onChange={(e) => e.target.value && setRango((r) => ({ ...r, desde: e.target.value }))}
                className={`${inputClass} w-auto`}
              />
              <input
                type="date"
                aria-label="Hasta"
                value={rango.hasta}
                min={rango.desde}
                max={hoy}
                onChange={(e) => e.target.value && setRango((r) => ({ ...r, hasta: e.target.value }))}
                className={`${inputClass} w-auto`}
              />
            </>
          )}
          {tipos.length > 0 && (
            <select aria-label="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} className={`${inputClass} w-auto`}>
              {tipos.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.label}
                </option>
              ))}
            </select>
          )}
          {usuario && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 py-1 pl-3 pr-1.5 text-xs font-medium text-brand-700">
              {usuario.nombre}
              <button type="button" aria-label="Quitar filtro de usuario" onClick={() => setUsuario(null)} className="rounded-full p-0.5 hover:bg-brand-100">
                <X className="size-3.5" />
              </button>
            </span>
          )}
          <Button size="sm" variant="secondary" icon={<RefreshCw className="size-3.5" />} onClick={() => setRecarga((n) => n + 1)}>
            Actualizar
          </Button>
        </div>

        {resultadoActual?.error && !cargando && (
          <div className="px-4 pb-3">
            <Alert tone="danger">{resultadoActual.error}</Alert>
          </div>
        )}

        {cargando ? (
          <SkeletonTable cols={5} />
        ) : !resultadoActual || resultadoActual.filas.length === 0 ? (
          <div className="border-t border-slate-200">
            <EmptyState icon={ScrollText} title="Sin registros en este periodo" />
          </div>
        ) : (
          <>
            <Tabla vista={vista} filas={resultadoActual.filas} onAbrir={setAbierta} />
            {resultadoActual.hayMas && (
              <div className="flex justify-center border-t border-slate-100 py-3">
                <Button size="sm" variant="secondary" loading={masCargando} onClick={() => void cargarMas()}>
                  Cargar más
                </Button>
              </div>
            )}
          </>
        )}
      </Card>

      {abierta && <Detalle fila={abierta} vista={vista} onCerrar={() => setAbierta(null)} onUsuario={filtrarUsuario} />}
    </div>
  );
}

// ---------------------------------------------------------------- indicadores (24 h)

function Indicadores({ resumen, irA }: { resumen: ResumenRegistro | null; irA: (v: Vista, tipo?: string) => void }) {
  const items: { etiqueta: string; valor: number | undefined; tono: string; ir: () => void }[] = [
    { etiqueta: "Operaciones", valor: resumen?.operaciones, tono: "text-slate-900", ir: () => irA("actividad") },
    {
      etiqueta: "Rechazadas",
      valor: resumen?.rechazadas,
      tono: resumen && resumen.rechazadas > 0 ? "text-amber-700" : "text-slate-900",
      ir: () => irA("actividad", "RECHAZADAS"),
    },
    {
      etiqueta: "Ingresos fallidos",
      valor: resumen?.ingresosFallidos,
      tono: resumen && resumen.ingresosFallidos > 0 ? "text-amber-700" : "text-slate-900",
      ir: () => irA("ingresos", "INGRESOS_FALLIDOS"),
    },
    {
      etiqueta: "Errores",
      valor: resumen ? resumen.erroresServidor + resumen.erroresNavegador : undefined,
      tono: resumen && resumen.erroresServidor + resumen.erroresNavegador > 0 ? "text-rose-700" : "text-slate-900",
      ir: () => irA("errores"),
    },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {items.map((it) => (
        <button
          key={it.etiqueta}
          type="button"
          onClick={it.ir}
          className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-left shadow-superficie transition-colors hover:border-brand-200 hover:bg-brand-50/30"
        >
          <span className="block text-xs text-slate-500">{it.etiqueta} · 24 h</span>
          {it.valor === undefined ? (
            <Skeleton className="mt-1.5 h-6 w-12" />
          ) : (
            <span className={`mt-0.5 block text-xl font-semibold tabular-nums ${it.tono}`}>{it.valor.toLocaleString("es-PE")}</span>
          )}
        </button>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- tablas

const th = "px-4 py-2.5";
const td = "px-4 py-2.5";

function Tabla({ vista, filas, onAbrir }: { vista: Vista; filas: Fila[]; onAbrir: (f: Fila) => void }) {
  const fila = (f: Fila, celdas: ReactNode) => (
    <tr
      key={f.id}
      tabIndex={0}
      onClick={() => onAbrir(f)}
      onKeyDown={(e) => e.key === "Enter" && onAbrir(f)}
      className="cursor-pointer hover:bg-slate-50 focus-visible:bg-slate-50 focus-visible:outline-none"
    >
      {celdas}
    </tr>
  );
  const fecha = (iso: string) => <td className={`${td} text-xs tabular-nums text-slate-500`}>{fechaHora(iso)}</td>;

  if (vista === "cambios") {
    return (
      <table className="tabla">
        <thead>
          <tr className="cabecera-tabla border-y border-slate-200">
            <th className={th}>Fecha</th>
            <th className={th}>Usuario</th>
            <th className={th}>Acción</th>
            <th className={th}>Sobre</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {(filas as CambioRegistro[]).map((c) =>
            fila(
              c,
              <>
                {fecha(c.en)}
                <td className={`${td} text-slate-700`}>{c.usuario ?? "Sistema"}</td>
                <td className={`${td} font-medium text-slate-800`}>{etiquetaAccionCambio(c.accion)}</td>
                <td className={`col-flex ${td} text-slate-600`} title={etiquetaEntidad(c.entidad)}>
                  {etiquetaEntidad(c.entidad)}
                  <span className="ml-2 font-mono text-xs text-slate-400">{c.entidadId.slice(0, 8)}</span>
                </td>
              </>,
            ),
          )}
        </tbody>
      </table>
    );
  }

  if (vista === "errores") {
    return (
      <table className="tabla">
        <thead>
          <tr className="cabecera-tabla border-y border-slate-200">
            <th className={th}>Fecha</th>
            <th className={th}>Origen</th>
            <th className={th}>Mensaje</th>
            <th className={`${th} col-sec`}>Usuario</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {(filas as ErrorRegistro[]).map((e) =>
            fila(
              e,
              <>
                {fecha(e.en)}
                <td className={td}>
                  <Badge tone={e.origen === "SERVIDOR" ? "rojo" : "amarillo"}>{e.origen === "SERVIDOR" ? "Servidor" : "Pantalla"}</Badge>
                </td>
                <td className={`col-flex ${td} text-slate-700`} title={e.mensaje ?? ""}>
                  {e.mensaje || e.tipo || "Sin mensaje"}
                  {e.ruta && <span className="ml-2 font-mono text-xs text-slate-400">{e.ruta}</span>}
                </td>
                <td className={`${td} col-sec text-slate-600`}>{e.usuario ?? "—"}</td>
              </>,
            ),
          )}
        </tbody>
      </table>
    );
  }

  if (vista === "movimientos") {
    return (
      <table className="tabla">
        <thead>
          <tr className="cabecera-tabla border-y border-slate-200">
            <th className={th}>Fecha</th>
            <th className={th}>Producto</th>
            <th className={th}>Tipo</th>
            <th className={`${th} text-right`}>Cantidad</th>
            <th className={`${th} col-sec`}>Usuario</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {(filas as MovimientoRegistro[]).map((m) =>
            fila(
              m,
              <>
                {fecha(m.en)}
                <td className={`col-flex ${td} text-slate-700`} title={`${m.codigo} · ${m.producto}`}>
                  <span className="font-mono text-xs text-slate-500">{m.codigo}</span>
                  <span className="ml-2">{m.producto}</span>
                </td>
                <td className={`${td} text-slate-600`}>{TIPOS_MOVIMIENTO[m.tipo] ?? m.tipo}</td>
                <td className={`${td} text-right font-medium tabular-nums ${m.cantidad < 0 ? "text-rose-700" : "text-emerald-700"}`}>
                  {m.cantidad > 0 ? "+" : ""}
                  {m.cantidad.toLocaleString("es-PE", { maximumFractionDigits: 3 })}
                </td>
                <td className={`${td} col-sec text-slate-600`}>{m.usuario ?? "—"}</td>
              </>,
            ),
          )}
        </tbody>
      </table>
    );
  }

  const ingresos = vista === "ingresos";
  return (
    <table className="tabla">
      <thead>
        <tr className="cabecera-tabla border-y border-slate-200">
          <th className={th}>Fecha</th>
          <th className={th}>Usuario</th>
          <th className={th}>{ingresos ? "Acción" : "Operación"}</th>
          <th className={th}>Resultado</th>
          <th className={`${th} col-sec`}>{ingresos ? "Navegador" : "Duración"}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {(filas as ActividadRegistro[]).map((a) => {
          const r = resultado(a.status);
          return fila(
            a,
            <>
              {fecha(a.en)}
              <td className={`${td} text-slate-700`}>{a.usuario ?? (ingresos ? a.detalle : null) ?? "Sin sesión"}</td>
              <td className={`col-flex ${td}`} title={`${a.metodo} ${a.ruta}`}>
                {!ingresos && <span className="mr-2 text-xs text-slate-400">{etiquetaModulo(a.modulo)}</span>}
                <span className="text-slate-800">{etiquetaAccion(a.metodo, a.ruta)}</span>
              </td>
              <td className={td}>
                <Badge tone={r.tono}>{ingresos && a.status === 401 ? "Fallido" : r.label}</Badge>
              </td>
              <td className={`${td} col-sec text-xs text-slate-500`}>
                {ingresos ? navegadorCorto(a.navegador) : `${a.ms.toLocaleString("es-PE")} ms`}
              </td>
            </>,
          );
        })}
      </tbody>
    </table>
  );
}

// ---------------------------------------------------------------- detalle

function Detalle({
  fila,
  vista,
  onCerrar,
  onUsuario,
}: {
  fila: Fila;
  vista: Vista;
  onCerrar: () => void;
  onUsuario: (id: string | null, nombre: string | null) => void;
}) {
  const cerrarRef = useRef(onCerrar);
  useEffect(() => {
    cerrarRef.current = onCerrar;
  });
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && cerrarRef.current();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  let titulo = "";
  let datos: [string, ReactNode][] = [];
  let bloques: { titulo: string; texto: string | null }[] = [];
  let enlace: string | null = null;

  if (vista === "cambios") {
    const c = fila as CambioRegistro;
    titulo = `${etiquetaAccionCambio(c.accion)} · ${etiquetaEntidad(c.entidad)}`;
    enlace = enlaceEntidad(c.entidad, c.entidadId);
    datos = [
      ["Fecha", new Date(c.en).toLocaleString("es-PE")],
      ["Usuario", c.usuario ?? "Sistema"],
      ["Entidad", etiquetaEntidad(c.entidad)],
      ["Identificador", <Codigo key="id" texto={c.entidadId} />],
    ];
    bloques = [
      { titulo: "Antes", texto: jsonLegible(c.antes) },
      { titulo: "Después", texto: jsonLegible(c.despues) },
    ];
  } else if (vista === "errores") {
    const e = fila as ErrorRegistro;
    titulo = e.origen === "SERVIDOR" ? "Error del servidor" : "Error en la pantalla";
    datos = [
      ["Fecha", new Date(e.en).toLocaleString("es-PE")],
      ["Usuario", e.usuario ?? "Sin sesión"],
      ["Ruta", e.ruta ? <Codigo key="r" texto={`${e.metodo ? `${e.metodo} ` : ""}${e.ruta}`} /> : "—"],
      ["Código HTTP", e.status ?? "—"],
      ["Tipo", e.tipo ?? "—"],
      ["Navegador", navegadorCorto(e.navegador)],
    ];
    bloques = [
      { titulo: "Mensaje", texto: e.mensaje },
      { titulo: "Detalle técnico", texto: e.pila },
    ];
  } else if (vista === "movimientos") {
    const m = fila as MovimientoRegistro;
    titulo = `${TIPOS_MOVIMIENTO[m.tipo] ?? m.tipo} · ${m.codigo}`;
    datos = [
      ["Fecha", new Date(m.en).toLocaleString("es-PE")],
      ["Usuario", m.usuario ?? "—"],
      ["Producto", m.producto],
      ["Código", m.codigo],
      ["Cantidad", m.cantidad.toLocaleString("es-PE", { maximumFractionDigits: 3 })],
      ["Costo unitario", m.costoUnitario == null ? "—" : m.costoUnitario.toLocaleString("es-PE", { maximumFractionDigits: 4 })],
      ["Almacén", `${m.almacen} (${m.empresa})`],
      ["Referencia", m.referenciaTipo],
      ["Id. de referencia", <Codigo key="ref" texto={m.referenciaId} />],
    ];
  } else {
    const a = fila as ActividadRegistro;
    const r = resultado(a.status);
    titulo = `${etiquetaModulo(a.modulo)} · ${etiquetaAccion(a.metodo, a.ruta)}`;
    enlace = enlaceRuta(a.ruta, a.entidadId);
    datos = [
      ["Fecha", new Date(a.en).toLocaleString("es-PE")],
      ["Usuario", a.usuario ? `${a.usuario}${a.correo ? ` · ${a.correo}` : ""}` : "Sin sesión"],
      ["Resultado", <Badge key="r" tone={r.tono}>{`${r.label} · ${a.status}`}</Badge>],
      ["Operación", ETIQUETA_METODO[a.metodo] ?? a.metodo],
      ["Ruta", <Codigo key="ruta" texto={`${a.metodo} ${a.ruta}`} />],
      ["Duración", `${a.ms.toLocaleString("es-PE")} ms`],
      ["IP", a.ip ?? "—"],
      ["Navegador", navegadorCorto(a.navegador)],
    ];
    if (a.entidadId) datos.push(["Identificador", <Codigo key="ent" texto={a.entidadId} />]);
    bloques = [
      { titulo: a.status >= 400 ? "Motivo" : "Detalle", texto: a.detalle },
      { titulo: "Navegador completo", texto: a.navegador },
    ];
  }

  const usuarioId = (fila as { usuarioId: string | null }).usuarioId;
  const usuarioNombre = (fila as { usuario: string | null }).usuario;

  return createPortal(
    <div className="animate-capa-in fixed inset-0 z-50 flex justify-end bg-slate-900/20" onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}>
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className="flex h-full w-full max-w-xl flex-col overflow-hidden border-l border-slate-200 bg-white shadow-flotante"
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <h2 className="min-w-0 truncate text-[1.0625rem] font-semibold text-slate-900" title={titulo}>
            {titulo}
          </h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="-mr-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
            <X className="size-4.5" />
          </button>
        </div>
        <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto px-5 py-4">
          <dl className="grid grid-cols-[8.5rem_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
            {datos.map(([k, v]) => (
              <div key={k} className="contents">
                <dt className="text-slate-500">{k}</dt>
                <dd className="min-w-0 truncate text-slate-800">{v}</dd>
              </div>
            ))}
          </dl>
          <div className="flex flex-wrap gap-2">
            {enlace && (
              <Button size="sm" variant="secondary" href={enlace} icon={<ExternalLink className="size-3.5" />}>
                Abrir
              </Button>
            )}
            {usuarioId && (
              <Button size="sm" variant="secondary" onClick={() => onUsuario(usuarioId, usuarioNombre)}>
                Ver todo de este usuario
              </Button>
            )}
          </div>
          {bloques
            .filter((b) => b.texto)
            .map((b) => (
              <div key={b.titulo}>
                <p className="rotulo mb-1.5">{b.titulo}</p>
                <pre className="max-h-[22rem] overflow-auto whitespace-pre-wrap break-words rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs text-slate-700">
                  {b.texto}
                </pre>
              </div>
            ))}
        </div>
      </aside>
    </div>,
    document.body,
  );
}

function Codigo({ texto }: { texto: string }) {
  return (
    <span className="font-mono text-xs text-slate-600" title={texto}>
      {texto}
    </span>
  );
}

