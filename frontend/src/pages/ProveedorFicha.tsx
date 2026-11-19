import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { BarChart3, ClipboardCheck, FileText, Info, ShoppingCart, Star } from "lucide-react";
import {
  actualizarProveedor,
  cambiarActivoProveedor,
  enviarProveedorARevision,
  listarEvaluacionesProveedor,
  obtenerFichaProveedor,
  reabrirProveedor,
} from "../api/proveedores";
import type { EvaluacionProveedor, FichaProveedor, TipoEvaluacion } from "../api/types";
import { fmtDia } from "../components/formato";
import { aInput, datosDe, type DatosProveedor } from "../components/proveedores/datosProveedor";
import { fmtNota } from "../components/proveedores/estados";
import FormularioProveedor from "../components/proveedores/FormularioProveedor";
import { EstadoProveedorBadge, ResultadoBadge, VigenciaBadge } from "../components/proveedores/Insignias";
import PanelDecision from "../components/proveedores/PanelDecision";
import PanelEvaluacion from "../components/proveedores/PanelEvaluacion";
import { PanelCategorias, PanelProductos } from "../components/proveedores/PanelesClasificacion";
import PestanaCompras from "../components/proveedores/PestanaCompras";
import PestanaDesempeno from "../components/proveedores/PestanaDesempeno";
import PestanaDocumentos from "../components/proveedores/PestanaDocumentos";
import PestanaEvaluaciones from "../components/proveedores/PestanaEvaluaciones";
import PestanaGeneral from "../components/proveedores/PestanaGeneral";
import Alert from "../components/ui/Alert";
import Button from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { useConfirmar } from "../components/ui/Confirmacion";
import MenuAcciones, { type AccionMenu } from "../components/ui/MenuAcciones";
import PageHeader from "../components/ui/PageHeader";
import PanelLateral from "../components/ui/PanelLateral";
import Pestanas, { type Pestana } from "../components/ui/Pestanas";
import { Skeleton } from "../components/ui/Skeleton";

type Tab = "general" | "documentos" | "seleccion" | "evaluaciones" | "compras" | "desempeno";
type Panel = "editar" | "categorias" | "productos" | "decidir" | TipoEvaluacion | null;

function Resumen({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="min-w-0 px-4 py-3">
      <p className="text-xs font-medium text-slate-500">{titulo}</p>
      <div className="mt-1 flex min-w-0 items-center gap-2 text-sm text-slate-800">{children}</div>
    </div>
  );
}

function PanelEditar({
  ficha,
  onCerrar,
  onGuardado,
}: {
  ficha: FichaProveedor;
  onCerrar: () => void;
  onGuardado: (mensaje: string) => void;
}) {
  const [datos, setDatos] = useState<DatosProveedor>(() => datosDe(ficha.proveedor));
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      const res = await actualizarProveedor(ficha.proveedor.supplierId, aInput(datos));
      if (res.ok) onGuardado(res.message);
      else setError(res.message);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <PanelLateral
      titulo="Editar proveedor"
      subtitulo={ficha.proveedor.legalName}
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button loading={guardando} onClick={guardar} disabled={datos.ruc.length !== 11 || !datos.legalName.trim()}>
            Guardar
          </Button>
        </>
      }
    >
      {error && <Alert tone="danger" className="mx-5 mt-4">{error}</Alert>}
      <FormularioProveedor datos={datos} onCambio={setDatos} />
    </PanelLateral>
  );
}

/**
 * Ficha unica del proveedor (DEC-L069..L071): estado, calificacion y vigencia
 * a la vista, una accion principal segun el estado y el detalle por pestanas.
 */
export default function ProveedorFicha() {
  const { id = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const confirmar = useConfirmar();
  const [ficha, setFicha] = useState<FichaProveedor | null>(null);
  const [evaluaciones, setEvaluaciones] = useState<EvaluacionProveedor[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [panel, setPanel] = useState<Panel>(params.get("editar") ? "editar" : null);
  const [ocupado, setOcupado] = useState(false);

  const tabs: Tab[] = ["general", "documentos", "seleccion", "evaluaciones", "compras", "desempeno"];
  const tab = (tabs.includes(params.get("tab") as Tab) ? params.get("tab") : "general") as Tab;

  const cargar = useCallback(() => {
    obtenerFichaProveedor(id).then((res) => {
      if (res.ok && res.result) setFicha(res.result);
      else setError(res.message);
    });
    listarEvaluacionesProveedor(id).then((res) => setEvaluaciones(res.ok ? (res.result ?? []) : []));
  }, [id]);

  useEffect(cargar, [cargar]);

  function cambiarTab(t: Tab) {
    setParams(t === "general" ? {} : { tab: t }, { replace: true });
  }

  function cerrarPanel() {
    setPanel(null);
    if (params.get("editar")) setParams(tab === "general" ? {} : { tab }, { replace: true });
  }

  function listo(mensaje: string, f?: FichaProveedor) {
    setAviso(mensaje);
    setError(null);
    cerrarPanel();
    if (f) setFicha(f);
    else cargar();
  }

  async function ejecutar(accion: () => Promise<{ ok: boolean; result: FichaProveedor | null; message: string }>) {
    setOcupado(true);
    try {
      const res = await accion();
      if (res.ok && res.result) listo(res.message, res.result);
      else {
        setAviso(null);
        setError(res.message);
      }
    } finally {
      setOcupado(false);
    }
  }

  if (error && !ficha) {
    return (
      <div>
        <PageHeader title="Proveedor" backTo="/proveedores" backLabel="Proveedores" />
        <Alert tone="danger">{error}</Alert>
      </div>
    );
  }

  if (!ficha) {
    return (
      <div>
        <Skeleton className="mb-3 h-4 w-24" />
        <Skeleton className="mb-6 h-9 w-80" />
        <Skeleton className="mb-5 h-[4.5rem] rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  const p = ficha.proveedor;
  const s = p.status;

  async function desactivar() {
    if (p.active) {
      const r = await confirmar({
        titulo: "¿Desactivar este proveedor?",
        mensaje: "Deja de aparecer en Compras hasta que lo actives.",
        confirmar: "Desactivar",
        peligro: true,
        motivo: "Motivo",
      });
      if (r) ejecutar(() => cambiarActivoProveedor(id, false, r.motivo));
    } else {
      ejecutar(() => cambiarActivoProveedor(id, true));
    }
  }

  async function reabrir() {
    const r = await confirmar({
      titulo: "¿Reabrir la solicitud?",
      mensaje: "Vuelve a borrador para corregirla y enviarla de nuevo.",
      confirmar: "Reabrir",
      motivo: "Motivo",
    });
    if (r) ejecutar(() => reabrirProveedor(id, r.motivo));
  }

  const principal =
    s === "BORRADOR" || s === "OBSERVADO" ? (
      <Button loading={ocupado} onClick={() => ejecutar(() => enviarProveedorARevision(id))}>
        Enviar a revisión
      </Button>
    ) : s === "PENDIENTE_REVISION" ? (
      <Button onClick={() => setPanel("decidir")}>Decidir</Button>
    ) : s === "HABILITADO" ? (
      <Button icon={<ClipboardCheck className="size-4" />} onClick={() => setPanel("DESEMPENO")}>
        Nueva evaluación
      </Button>
    ) : (
      <Button loading={ocupado} onClick={reabrir}>
        Reabrir
      </Button>
    );

  const acciones: AccionMenu[] = [];
  if (s !== "RECHAZADO") {
    acciones.push({ label: "Editar datos", onClick: () => setPanel("editar") });
    acciones.push({ label: "Registrar selección", onClick: () => setPanel("SELECCION") });
  }
  if (s === "HABILITADO") {
    acciones.push({ label: p.active ? "Desactivar" : "Activar", onClick: desactivar, peligro: p.active });
  }

  const faltan = ficha.advertencias.filter((a) => a.startsWith("Falta") || a.startsWith("Está vencido")).length;
  const pestanas: Pestana<Tab>[] = [
    { id: "general", label: "General", icon: Info },
    { id: "documentos", label: "Documentos", icon: FileText, marca: faltan || null },
    { id: "seleccion", label: "Selección", icon: Star },
    { id: "evaluaciones", label: "Evaluaciones", icon: ClipboardCheck },
    { id: "compras", label: "Compras", icon: ShoppingCart },
    { id: "desempeno", label: "Desempeño", icon: BarChart3 },
  ];
  const ultima = ficha.ultimaEvaluacion;
  const seleccion = ficha.ultimaSeleccion;

  return (
    <div>
      <PageHeader
        title={p.legalName}
        subtitle={
          <span className="inline-flex items-center gap-2">
            <span className="font-mono">{p.ruc}</span>
            {p.tradeName && <span>· {p.tradeName}</span>}
            {p.supplierType === "CRITICO" && <span className="font-medium text-amber-700">· Crítico</span>}
          </span>
        }
        backTo="/proveedores"
        backLabel="Proveedores"
        actions={
          <>
            <MenuAcciones acciones={acciones} />
            {principal}
          </>
        }
      />

      {aviso && <Alert tone="good" className="mb-4">{aviso}</Alert>}
      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}
      {(s === "OBSERVADO" || s === "RECHAZADO") && p.statusReason && (
        <Alert tone="danger" className="mb-4">
          {s === "OBSERVADO" ? "Observado" : "Rechazado"}: {p.statusReason}
        </Alert>
      )}

      <Card className="mb-5 grid grid-cols-2 divide-slate-100 sm:grid-cols-4 sm:divide-x">
        <Resumen titulo="Estado">
          <EstadoProveedorBadge status={s} activo={p.active} />
        </Resumen>
        <Resumen titulo="Selección">
          {seleccion ? (
            <>
              <span className="font-semibold tabular-nums">{fmtNota(seleccion.finalScore)}</span>
              <ResultadoBadge resultado={seleccion.result} />
            </>
          ) : (
            <span className="text-slate-500">Sin registrar</span>
          )}
        </Resumen>
        <Resumen titulo="Última evaluación">
          {ultima ? (
            <>
              <span className="font-semibold tabular-nums">{fmtNota(ultima.finalScore)}</span>
              <span className="truncate text-slate-500">{fmtDia(ultima.evaluatedAt)}</span>
            </>
          ) : (
            <span className="text-slate-500">Sin evaluaciones</span>
          )}
        </Resumen>
        <Resumen titulo="Vigencia">
          <VigenciaBadge vigencia={ficha.vigencia} />
          {ficha.proximaEvaluacion && <span className="truncate text-slate-500">{fmtDia(ficha.proximaEvaluacion)}</span>}
        </Resumen>
      </Card>

      <Pestanas pestanas={pestanas} activa={tab} onCambiar={cambiarTab} />

      {tab === "general" && (
        <PestanaGeneral
          ficha={ficha}
          onEditar={() => setPanel("editar")}
          onCategorias={() => setPanel("categorias")}
          onProductos={() => setPanel("productos")}
        />
      )}
      {tab === "documentos" && <PestanaDocumentos supplierId={id} onCambio={cargar} />}
      {tab === "seleccion" && (
        <PestanaEvaluaciones
          kind="SELECCION"
          evaluaciones={evaluaciones}
          puedeRegistrar={s !== "RECHAZADO"}
          motivoNoRegistrar="Reabre la solicitud para registrar la selección."
          onRegistrar={() => setPanel("SELECCION")}
          onCambio={(m) => listo(m)}
        />
      )}
      {tab === "evaluaciones" && (
        <PestanaEvaluaciones
          kind="DESEMPENO"
          evaluaciones={evaluaciones}
          puedeRegistrar={s === "HABILITADO"}
          motivoNoRegistrar="La evaluación de desempeño es para proveedores habilitados."
          onRegistrar={() => setPanel("DESEMPENO")}
          onCambio={(m) => listo(m)}
        />
      )}
      {tab === "compras" && <PestanaCompras supplierId={id} />}
      {tab === "desempeno" && <PestanaDesempeno supplierId={id} />}

      {panel === "editar" && <PanelEditar ficha={ficha} onCerrar={cerrarPanel} onGuardado={(m) => listo(m)} />}
      {panel === "categorias" && <PanelCategorias ficha={ficha} onCerrar={() => setPanel(null)} onGuardado={(f, m) => listo(m, f)} />}
      {panel === "productos" && <PanelProductos ficha={ficha} onCerrar={() => setPanel(null)} onGuardado={(f, m) => listo(m, f)} />}
      {panel === "decidir" && <PanelDecision ficha={ficha} onCerrar={() => setPanel(null)} onDecidido={(f, m) => listo(m, f)} />}
      {(panel === "SELECCION" || panel === "DESEMPENO") && (
        <PanelEvaluacion
          supplierId={id}
          proveedor={p.legalName}
          kind={panel}
          onCerrar={() => setPanel(null)}
          onRegistrada={(m) => {
            cambiarTab(panel === "SELECCION" ? "seleccion" : "evaluaciones");
            listo(m);
          }}
        />
      )}
    </div>
  );
}
