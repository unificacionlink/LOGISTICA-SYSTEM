import { useDeferredValue, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Download, ExternalLink, FolderArchive, Search } from "lucide-react";
import {
  descargarDelRepositorio,
  descargarZipRepositorio,
  listarRepositorio,
  type DocumentoRepositorio,
  type TipoDocumentoRepositorio,
} from "../api/repositorio";
import { useDatos } from "../api/useDatos";
import BotonDescarga from "../components/BotonDescarga";
import { EMPRESAS, nombreEmpresa } from "../components/empresas";
import { fmtDia } from "../components/formato";
import Alert from "../components/ui/Alert";
import { Card } from "../components/ui/Card";
import EmptyState from "../components/ui/EmptyState";
import { inputClass } from "../components/ui/Field";
import PageHeader from "../components/ui/PageHeader";
import { SkeletonTable } from "../components/ui/Skeleton";

const TIPOS: { id: TipoDocumentoRepositorio; label: string }[] = [
  { id: "REQUERIMIENTO", label: "Requerimiento" },
  { id: "ORDEN_COMPRA", label: "Orden de compra" },
  { id: "NOTA_INVENTARIO", label: "Nota de inventario" },
  { id: "CONSTANCIA_PAGO", label: "Constancia de pago" },
  { id: "FACTURA", label: "Factura" },
  { id: "GUIA", label: "Guía de remisión" },
  { id: "COMPROBANTE_CAJA_CHICA", label: "Comprobante de caja chica" },
  { id: "DOC_PROVEEDOR", label: "Documento de proveedor" },
];
const ETIQUETA_TIPO = new Map(TIPOS.map((t) => [t.id, t.label]));

/** Los últimos 12 meses (AAAA-MM), el actual primero. */
function ultimosMeses(): { id: string; label: string }[] {
  const hoy = new Date();
  return Array.from({ length: 12 }, (_, i) => {
    const d = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const id = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const label = d.toLocaleDateString("es-PE", { month: "long", year: "numeric" });
    return { id, label: label.charAt(0).toUpperCase() + label.slice(1) };
  });
}

function vencido(vence: string | null): boolean {
  return !!vence && vence < new Date().toISOString().slice(0, 10);
}

/**
 * Repositorio de documentos (DEC-L091): los PDF oficiales que archiva el ERP
 * (requerimientos aprobados, órdenes de compra aprobadas y notas de inventario)
 * y los archivos que se suben (pagos, facturas, guías, caja chica y documentos de
 * proveedores con su vencimiento), por tipo, empresa y mes.
 */
export default function Repositorio() {
  const meses = useMemo(() => ultimosMeses(), []);
  const [tipo, setTipo] = useState("");
  const [empresa, setEmpresa] = useState("");
  const [periodo, setPeriodo] = useState(meses[0].id);
  const [texto, setTexto] = useState("");
  const textoDiferido = useDeferredValue(texto);
  const filtros = { tipo, empresa, periodo, texto: textoDiferido };
  const docs = useDatos(`repositorio:${tipo}|${empresa}|${periodo}|${textoDiferido.trim()}`, () => listarRepositorio(filtros), {
    conservar: true,
  });
  const lista = docs.datos ?? [];

  return (
    <div>
      <PageHeader
        title="Documentos"
        actions={<BotonDescarga etiqueta="ZIP" descargar={() => descargarZipRepositorio(filtros)} />}
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <select aria-label="Mes" value={periodo} onChange={(e) => setPeriodo(e.target.value)} className={`${inputClass} w-48`}>
          {meses.map((m) => (
            <option key={m.id} value={m.id}>
              {m.label}
            </option>
          ))}
          <option value="">Todos los meses</option>
        </select>
        <select aria-label="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)} className={`${inputClass} w-56`}>
          <option value="">Todos los tipos</option>
          {TIPOS.map((t) => (
            <option key={t.id} value={t.id}>
              {t.label}
            </option>
          ))}
        </select>
        <select aria-label="Empresa" value={empresa} onChange={(e) => setEmpresa(e.target.value)} className={`${inputClass} w-44`}>
          <option value="">Todas las empresas</option>
          {EMPRESAS.map((e) => (
            <option key={e.code} value={e.code}>
              {e.label}
            </option>
          ))}
        </select>
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Buscar por código o detalle"
            className={`${inputClass} w-full pl-9`}
          />
        </div>
      </div>

      {docs.error && <Alert tone="danger" className="mb-4">{docs.error}</Alert>}

      <Card className="overflow-hidden">
        {!docs.datos ? (
          <SkeletonTable cols={6} />
        ) : lista.length === 0 ? (
          <EmptyState icon={FolderArchive} title="No hay documentos con este filtro" />
        ) : (
          <div className={`transition-opacity duration-200 ${docs.previo ? "opacity-60" : ""}`}>
            <table className="tabla">
              <thead>
                <tr className="border-b border-slate-200 cabecera-tabla">
                  <th className="px-5 py-2.5">Tipo</th>
                  <th className="px-5 py-2.5">Documento</th>
                  <th className="px-5 py-2.5">Detalle</th>
                  <th className="px-5 py-2.5">Empresa</th>
                  <th className="px-5 py-2.5">Fecha</th>
                  <th className="px-5 py-2.5">Vence</th>
                  <th className="px-5 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {lista.map((d, i) => (
                  <Fila key={`${d.tipo}-${d.codigo}-${i}`} d={d} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

function Fila({ d }: { d: DocumentoRepositorio }) {
  const [error, setError] = useState<string | null>(null);

  async function bajar() {
    if (!d.descarga) return;
    setError(null);
    // Los documentos de proveedor pueden ser imagen: su nombre no se fuerza a .pdf.
    const res = await descargarDelRepositorio(d.descarga, d.tipo === "DOC_PROVEEDOR" ? d.codigo : `${d.codigo}.pdf`);
    if (!res.ok) setError(res.message);
  }

  return (
    <tr>
      <td className="px-5 py-2.5 text-xs text-slate-500">{ETIQUETA_TIPO.get(d.tipo) ?? d.tipo}</td>
      <td className="px-5 py-2.5 font-mono text-xs text-slate-800">{d.codigo}</td>
      <td className="col-flex px-5 py-2.5 text-sm text-slate-600" title={error ?? d.detalle ?? ""}>
        {error ? <span className="text-rose-700">{error}</span> : (d.detalle ?? "—")}
      </td>
      <td className="px-5 py-2.5 text-sm text-slate-600">{d.empresaCode ? nombreEmpresa(d.empresaCode) : "—"}</td>
      <td className="px-5 py-2.5 text-sm text-slate-600">
        {d.fecha ? new Date(d.fecha).toLocaleDateString("es-PE", { day: "2-digit", month: "2-digit", year: "numeric" }) : "—"}
      </td>
      <td className={`px-5 py-2.5 text-sm ${vencido(d.vence) ? "font-medium text-rose-700" : "text-slate-600"}`}>
        {d.vence ? fmtDia(d.vence) : "—"}
      </td>
      <td className="px-5 py-2.5 text-right text-sm">
        <span className="inline-flex gap-3">
          {d.descarga && (
            <button type="button" onClick={() => void bajar()} className="inline-flex items-center gap-1 font-medium text-brand-600 hover:text-brand-700">
              <Download className="size-3.5" />
              Descargar
            </button>
          )}
          {d.url && (
            <a href={d.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-medium text-brand-600 hover:text-brand-700">
              <ExternalLink className="size-3.5" />
              Abrir
            </a>
          )}
          {d.ruta && (
            <Link to={d.ruta} className="font-medium text-brand-600 hover:text-brand-700">
              Ver
            </Link>
          )}
        </span>
      </td>
    </tr>
  );
}
