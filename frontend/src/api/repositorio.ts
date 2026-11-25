import { http } from "./http";

/** Documento del repositorio (DEC-L091): PDF archivados por el ERP y archivos subidos. */
export interface DocumentoRepositorio {
  tipo: TipoDocumentoRepositorio;
  codigo: string;
  detalle: string | null;
  empresaCode: string | null;
  /** AAAA-MM. */
  periodo: string | null;
  fecha: string | null;
  /** Solo documentos de proveedor. */
  vence: string | null;
  /** Ruta de la API que entrega el archivo (bucket privado). */
  descarga: string | null;
  /** Archivo público. */
  url: string | null;
  /** Pantalla del ERP donde está el documento. */
  ruta: string | null;
}

export type TipoDocumentoRepositorio =
  | "REQUERIMIENTO"
  | "ORDEN_COMPRA"
  | "NOTA_INVENTARIO"
  | "CONSTANCIA_PAGO"
  | "FACTURA"
  | "GUIA"
  | "COMPROBANTE_CAJA_CHICA"
  | "DOC_PROVEEDOR";

export interface FiltrosRepositorio {
  tipo: string;
  empresa: string;
  periodo: string;
  texto: string;
}

function consulta(f: Partial<FiltrosRepositorio>): string {
  const p = new URLSearchParams();
  if (f.tipo) p.set("tipo", f.tipo);
  if (f.empresa) p.set("empresa", f.empresa);
  if (f.periodo) p.set("periodo", f.periodo);
  if (f.texto?.trim()) p.set("texto", f.texto.trim());
  return p.toString();
}

export function listarRepositorio(f: FiltrosRepositorio) {
  return http.get<DocumentoRepositorio[]>(`/api/repositorio?${consulta(f)}`);
}

/** Un archivo que entrega el backend (PDF archivado o documento privado de proveedor). */
export function descargarDelRepositorio(ruta: string, nombre: string) {
  return http.descargar(ruta, nombre);
}

/** Los PDF archivados del filtro en un ZIP con carpetas tipo/empresa/mes. */
export function descargarZipRepositorio(f: FiltrosRepositorio) {
  const nombre = `REPOSITORIO_${f.periodo || "todo"}_${f.empresa || "todas"}_${f.tipo || "todos"}.zip`;
  return http.descargar(`/api/repositorio/zip?${consulta({ tipo: f.tipo, empresa: f.empresa, periodo: f.periodo })}`, nombre);
}
