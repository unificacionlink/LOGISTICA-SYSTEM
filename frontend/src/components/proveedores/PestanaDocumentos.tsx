import { useState } from "react";
import { useDatos } from "../../api/useDatos";
import { Download, FileText, Upload } from "lucide-react";
import {
  anularDocumentoProveedor,
  descargarDocumentoProveedor,
  listarDocumentosProveedor,
  subirDocumentoProveedor,
} from "../../api/proveedores";
import type { DocumentosProveedor } from "../../api/types";
import { fmtDia, fmtFecha } from "../formato";
import Alert from "../ui/Alert";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { Card } from "../ui/Card";
import { useConfirmar } from "../ui/Confirmacion";
import Field, { inputClass } from "../ui/Field";
import PanelLateral from "../ui/PanelLateral";
import { Skeleton } from "../ui/Skeleton";
import { VigenciaBadge } from "./Insignias";

type Requisito = DocumentosProveedor["requisitos"][number];

function PanelSubir({
  supplierId,
  requisito,
  requisitos,
  onCerrar,
  onSubido,
}: {
  supplierId: string;
  requisito: Requisito | null;
  requisitos: Requisito[];
  onCerrar: () => void;
  onSubido: (d: DocumentosProveedor, mensaje: string) => void;
}) {
  const [requirementId, setRequirementId] = useState(requisito?.requirementId ?? "");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [emision, setEmision] = useState("");
  const [vence, setVence] = useState("");
  const [notas, setNotas] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const elegido = requisitos.find((r) => r.requirementId === requirementId);
  const faltaVencimiento = Boolean(elegido?.hasExpiry) && !vence;

  async function subir() {
    if (!archivo) return;
    setSubiendo(true);
    setError(null);
    try {
      const res = await subirDocumentoProveedor(supplierId, archivo, {
        requirementId: requirementId || undefined,
        issuedAt: emision || undefined,
        expiresAt: vence || undefined,
        notes: notas || undefined,
      });
      if (res.ok && res.result) onSubido(res.result, res.message);
      else setError(res.message);
    } finally {
      setSubiendo(false);
    }
  }

  return (
    <PanelLateral
      titulo="Subir documento"
      onCerrar={onCerrar}
      pie={
        <>
          <Button variant="secondary" onClick={onCerrar}>
            Cancelar
          </Button>
          <Button loading={subiendo} disabled={!archivo || faltaVencimiento} onClick={subir}>
            Subir
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 px-5 py-4 sm:grid-cols-2">
        {error && <Alert tone="danger" className="sm:col-span-2">{error}</Alert>}
        <Field label="Requisito" className="sm:col-span-2">
          <select className={`${inputClass} w-full`} value={requirementId} onChange={(e) => setRequirementId(e.target.value)}>
            <option value="">Otro documento</option>
            {requisitos.map((r) => (
              <option key={r.requirementId} value={r.requirementId}>
                {r.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Archivo (PDF o imagen, hasta 8 MB)" required className="sm:col-span-2">
          <input
            type="file"
            accept="application/pdf,image/jpeg,image/png,image/webp"
            className="text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium"
            onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
          />
        </Field>
        <Field label="Emisión">
          <input type="date" className={`${inputClass} w-full`} value={emision} onChange={(e) => setEmision(e.target.value)} />
        </Field>
        <Field label="Vencimiento" required={Boolean(elegido?.hasExpiry)}>
          <input type="date" className={`${inputClass} w-full`} min={emision || undefined} value={vence} onChange={(e) => setVence(e.target.value)} />
        </Field>
        <Field label="Notas" className="sm:col-span-2">
          <input className={`${inputClass} w-full`} maxLength={500} value={notas} onChange={(e) => setNotas(e.target.value)} />
        </Field>
      </div>
    </PanelLateral>
  );
}

/**
 * Documentos del proveedor (DEC-L071): checklist de requisitos con su estado
 * y archivos guardados en el almacenamiento privado. Se descargan con sesion;
 * anular conserva el registro con su motivo.
 */
export default function PestanaDocumentos({ supplierId, onCambio }: { supplierId: string; onCambio: () => void }) {
  const confirmar = useConfirmar();
  // Memoria de la pestana (DEC-L073): volver a la pestana no la vacia.
  const docsDatos = useDatos(`proveedor-documentos:${supplierId}`, () => listarDocumentosProveedor(supplierId));
  const datos = docsDatos.datos;
  const setDatos = docsDatos.mutar;
  const [subiendo, setSubiendo] = useState<{ requisito: Requisito | null } | null>(null);
  const [errorAccion, setError] = useState<string | null>(null);
  const error = errorAccion ?? docsDatos.error;
  const [aviso, setAviso] = useState<string | null>(null);
  const [verAnulados, setVerAnulados] = useState(false);

  async function anular(id: string, nombre: string) {
    const r = await confirmar({
      titulo: "¿Anular este documento?",
      mensaje: `«${nombre}» queda en el historial como anulado.`,
      confirmar: "Anular",
      peligro: true,
      motivo: "Motivo de la anulación",
    });
    if (!r) return;
    const res = await anularDocumentoProveedor(id, r.motivo);
    if (res.ok && res.result) {
      setDatos(res.result);
      setAviso(res.message);
      onCambio();
    } else setError(res.message);
  }

  async function descargar(id: string, nombre: string) {
    const res = await descargarDocumentoProveedor(id, nombre);
    if (!res.ok) setError(res.message);
  }

  const documentos = (datos?.documentos ?? []).filter((d) => verAnulados || d.status === "ACTIVO");
  const anulados = (datos?.documentos ?? []).filter((d) => d.status === "ANULADO").length;

  return (
    <div className="space-y-4">
      {error && <Alert tone="danger">{error}</Alert>}
      {aviso && <Alert tone="good">{aviso}</Alert>}

      <Card className="overflow-hidden">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3">
          <p className="text-sm font-semibold text-slate-800">Requisitos</p>
          <Button size="sm" icon={<Upload className="size-4" />} onClick={() => setSubiendo({ requisito: null })} disabled={!datos}>
            Subir documento
          </Button>
        </div>
        {!datos ? (
          <div className="space-y-3 p-5">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-8" />
            ))}
          </div>
        ) : (
          <ul className="divide-y divide-slate-100">
            {datos.requisitos.map((r) => (
              <li key={r.requirementId} className="flex items-center gap-3 px-5 py-2.5">
                <span className="min-w-0 flex-1 truncate text-sm text-slate-800" title={r.name}>
                  {r.name}
                  {r.required && <span className="ml-2 text-xs font-medium text-slate-500">Obligatorio</span>}
                </span>
                {r.expiresAt && <span className="text-xs tabular-nums text-slate-500">Vence {fmtDia(r.expiresAt)}</span>}
                <VigenciaBadge vigencia={r.estado} />
                <Button size="sm" variant="ghost" onClick={() => setSubiendo({ requisito: r })}>
                  {r.estado === "FALTA" ? "Subir" : "Reemplazar"}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card className="overflow-hidden">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3">
          <p className="text-sm font-semibold text-slate-800">Archivos</p>
          {anulados > 0 && (
            <button type="button" className="text-sm font-medium text-brand-700" onClick={() => setVerAnulados((v) => !v)}>
              {verAnulados ? "Ocultar anulados" : `Ver anulados (${anulados})`}
            </button>
          )}
        </div>
        {!datos ? (
          <div className="p-5">
            <Skeleton className="h-8" />
          </div>
        ) : documentos.length === 0 ? (
          <p className="px-5 py-6 text-sm text-slate-500">Sin archivos.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {documentos.map((d) => (
              <li key={d.supplierDocumentId} className={`flex items-center gap-3 px-5 py-2.5 ${d.status === "ANULADO" ? "opacity-60" : ""}`}>
                <FileText className="size-4 shrink-0 text-slate-400" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-slate-800" title={d.fileName}>
                    {d.fileName}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {d.requisito ?? (d.evaluationId ? "Evidencia de evaluación" : "Otro documento")} · {fmtFecha(d.createdAt)}
                    {d.subidoPor ? ` · ${d.subidoPor}` : ""}
                    {d.status === "ANULADO" && d.voidReason ? ` · Anulado: ${d.voidReason}` : ""}
                  </p>
                </div>
                {d.status === "ANULADO" ? (
                  <Badge tone="gris">Anulado</Badge>
                ) : (
                  d.expiresAt && <VigenciaBadge vigencia={d.vigencia} />
                )}
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label="Descargar"
                  icon={<Download className="size-4" />}
                  onClick={() => descargar(d.supplierDocumentId, d.fileName)}
                >
                  Descargar
                </Button>
                {d.status === "ACTIVO" && (
                  <Button size="sm" variant="ghost" onClick={() => anular(d.supplierDocumentId, d.fileName)}>
                    Anular
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      {subiendo && datos && (
        <PanelSubir
          supplierId={supplierId}
          requisito={subiendo.requisito}
          requisitos={datos.requisitos}
          onCerrar={() => setSubiendo(null)}
          onSubido={(d, mensaje) => {
            setDatos(d);
            setAviso(mensaje);
            setSubiendo(null);
            onCambio();
          }}
        />
      )}
    </div>
  );
}
