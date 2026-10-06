import { useState } from "react";
import { Link } from "react-router-dom";
import { FileText, Wallet } from "lucide-react";
import { ETIQUETA_MOTIVO, guardarReglaCajaChica, observarCajaChica, regularizarCajaChica } from "../../api/cajachica";
import type { CompraCajaChica, ReglaCajaChica } from "../../api/types";
import { useAuth } from "../../auth/AuthContext";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card, CardBody } from "../ui/Card";
import EmptyState from "../ui/EmptyState";
import Field, { inputClass } from "../ui/Field";

function EstadoCompra({ estado }: { estado: CompraCajaChica["estado"] }) {
  if (estado === "PENDIENTE_REGULARIZACION") return <Badge tone="amarillo">Por regularizar</Badge>;
  if (estado === "OBSERVADO") return <Badge tone="rojo">Observada</Badge>;
  if (estado === "REGULARIZADO") return <Badge tone="verde">Regularizada</Badge>;
  return <Badge tone="gris">Anulada</Badge>;
}

function fmtDia(iso: string): string {
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

/** Compras por Caja Chica y su regularizacion (DEC-L026). */
export default function PestanaCajaChica({
  compras,
  regla,
  onCompra,
  onRegla,
}: {
  compras: CompraCajaChica[];
  regla: ReglaCajaChica | null;
  onCompra: (c: CompraCajaChica) => void;
  onRegla: (r: ReglaCajaChica) => void;
}) {
  const [abierta, setAbierta] = useState<string | null>(null);

  return (
    <div className="flex flex-col gap-4">
      {regla && <Regla regla={regla} onRegla={onRegla} />}
      <Card className="overflow-hidden">
        {compras.length === 0 ? (
          <EmptyState icon={Wallet} title="Aún no hay compras por Caja Chica" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {compras.map((c) => (
              <Compra
                key={c.id}
                c={c}
                abierta={abierta === c.id}
                alternar={() => setAbierta(abierta === c.id ? null : c.id)}
                onCompra={onCompra}
              />
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Compra({
  c,
  abierta,
  alternar,
  onCompra,
}: {
  c: CompraCajaChica;
  abierta: boolean;
  alternar: () => void;
  onCompra: (c: CompraCajaChica) => void;
}) {
  const [modo, setModo] = useState<"ver" | "regularizar" | "observar">("ver");
  const [texto, setTexto] = useState(c.codigoExterno ?? "");
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const abierto = c.estado === "PENDIENTE_REGULARIZACION" || c.estado === "OBSERVADO";

  async function ejecutar() {
    setGuardando(true);
    setError(null);
    try {
      const res = modo === "regularizar" ? await regularizarCajaChica(c.id, texto) : await observarCajaChica(c.id, texto);
      if (res.ok && res.result) {
        onCompra(res.result);
        setModo("ver");
      } else {
        setError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <li>
      <button type="button" onClick={alternar} aria-expanded={abierta} className="flex w-full flex-wrap items-center gap-3 px-5 py-3 text-left hover:bg-slate-50">
        <span className="font-mono text-xs text-slate-700">{c.codigo}</span>
        <span className="min-w-0 flex-1 truncate text-sm text-slate-700">{c.proveedor}</span>
        <span className="font-mono text-xs text-slate-500">{c.requestCode}</span>
        <span className="tabular-nums text-sm text-slate-800">
          {c.moneda} {c.monto.toFixed(2)}
        </span>
        <EstadoCompra estado={c.estado} />
      </button>
      {abierta && (
        <div className="flex flex-col gap-3 border-t border-slate-100 bg-slate-50/60 px-5 py-4 text-sm">
          <dl className="grid gap-x-4 gap-y-1.5 text-xs sm:grid-cols-4">
            <div>
              <dt className="rotulo">Comprobante</dt>
              <dd className="text-slate-700">{c.comprobante}</dd>
            </div>
            <div>
              <dt className="rotulo">Fecha de compra</dt>
              <dd className="text-slate-700">{fmtDia(c.fechaCompra)}</dd>
            </div>
            <div>
              <dt className="rotulo">Motivo</dt>
              <dd className="text-slate-700">
                {ETIQUETA_MOTIVO[c.motivo]}
                {c.motivoDetalle ? ` — ${c.motivoDetalle}` : ""}
              </dd>
            </div>
            <div>
              <dt className="rotulo">Registró</dt>
              <dd className="text-slate-700">{c.registradoPor}</dd>
            </div>
          </dl>
          <ul className="flex flex-col gap-0.5 text-xs text-slate-600">
            {c.items.map((i) => (
              <li key={i.sku}>
                {i.cantidad} × {i.sku} {i.producto} · {c.moneda} {i.costoUnitario} = {i.total.toFixed(2)}
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center gap-3 text-xs">
            <Link to={`/requerimientos/${c.requestId}`} className="font-medium text-brand-600 hover:text-brand-700">
              Ver requerimiento
            </Link>
            {c.documentoUrl && (
              <a href={c.documentoUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-600 hover:text-brand-700">
                <FileText className="size-3.5" />
                Comprobante
              </a>
            )}
            {c.codigoExterno && <span className="text-slate-500">Caja Chica: {c.codigoExterno}</span>}
          </div>
          {c.observacion && <Alert tone="danger">Observada: {c.observacion}</Alert>}

          {abierto && modo === "ver" && (
            <div className="flex gap-2">
              <Button size="sm" onClick={() => setModo("regularizar")}>
                Regularizar
              </Button>
              {c.estado === "PENDIENTE_REGULARIZACION" && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    setTexto("");
                    setModo("observar");
                  }}
                >
                  Observar
                </Button>
              )}
            </div>
          )}
          {modo !== "ver" && (
            <div className="flex flex-wrap items-end gap-2">
              <Field label={modo === "regularizar" ? "Código en el aplicativo de Caja Chica" : "Motivo de la observación"} required={modo === "observar"}>
                <input autoFocus value={texto} onChange={(e) => setTexto(e.target.value)} className={`${inputClass} w-72`} />
              </Field>
              <Button size="sm" loading={guardando} disabled={modo === "observar" && !texto.trim()} onClick={ejecutar}>
                {modo === "regularizar" ? "Confirmar" : "Observar"}
              </Button>
              <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setModo("ver")}>
                Cancelar
              </Button>
            </div>
          )}
          {error && <Alert tone="danger">{error}</Alert>}
        </div>
      )}
    </li>
  );
}

/** Lineamiento vigente (RF-026): lo ve Logistica, lo edita ADMIN. */
function Regla({ regla, onRegla }: { regla: ReglaCajaChica; onRegla: (r: ReglaCajaChica) => void }) {
  const { tieneAlgunRol } = useAuth();
  const esAdmin = tieneAlgunRol("ADMIN");
  const [editando, setEditando] = useState(false);
  const [umbral, setUmbral] = useState(String(regla.umbral));
  const [flags, setFlags] = useState({
    bajoMonto: regla.bajoMonto,
    mismoDia: regla.mismoDia,
    fallaProveedor: regla.fallaProveedor,
    excepcion: regla.excepcion,
  });
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const activos = [
    regla.bajoMonto && `bajo monto hasta ${regla.moneda} ${regla.umbral.toFixed(2)}`,
    regla.mismoDia && "mismo día",
    regla.fallaProveedor && "falla de proveedor",
    regla.excepcion && "excepción autorizada",
  ].filter(Boolean);

  async function onGuardar() {
    setGuardando(true);
    setError(null);
    try {
      const res = await guardarReglaCajaChica({ umbral: Number(umbral), ...flags });
      if (res.ok && res.result) {
        onRegla(res.result);
        setEditando(false);
      } else {
        setError(res.message);
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Card>
      <CardBody className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <span className="text-slate-600">
            <span className="rotulo mr-2">Regla vigente</span>
            {activos.join(" · ") || "Caja Chica deshabilitada"}
          </span>
          {esAdmin && !editando && (
            <Button size="sm" variant="ghost" onClick={() => setEditando(true)}>
              Editar
            </Button>
          )}
        </div>
        {editando && (
          <div className="flex flex-wrap items-end gap-4">
            <Field label={`Umbral de bajo monto (${regla.moneda})`}>
              <input type="number" min={0} step="0.01" value={umbral} onChange={(e) => setUmbral(e.target.value)} className={`${inputClass} w-32`} />
            </Field>
            {(
              [
                ["bajoMonto", "Bajo monto"],
                ["mismoDia", "Mismo día"],
                ["fallaProveedor", "Falla de proveedor"],
                ["excepcion", "Excepción autorizada"],
              ] as const
            ).map(([k, label]) => (
              <label key={k} className="flex items-center gap-1.5 pb-2 text-sm text-slate-700">
                <input type="checkbox" checked={flags[k]} onChange={(e) => setFlags((f) => ({ ...f, [k]: e.target.checked }))} />
                {label}
              </label>
            ))}
            <div className="flex gap-2 pb-0.5">
              <Button size="sm" loading={guardando} onClick={onGuardar}>
                Guardar
              </Button>
              <Button size="sm" variant="ghost" disabled={guardando} onClick={() => setEditando(false)}>
                Cancelar
              </Button>
            </div>
          </div>
        )}
        {error && <Alert tone="danger">{error}</Alert>}
      </CardBody>
    </Card>
  );
}
