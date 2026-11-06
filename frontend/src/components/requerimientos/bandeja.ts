import type { EstadoRequerimiento, Prioridad, Requerimiento } from "../../api/types";

/**
 * Bandeja de Requerimientos (DEC-L046): las tres empresas nunca se mezclan en
 * una fila sin su marca, lo abierto se agrupa por etapa del flujo y dentro de
 * cada etapa va primero lo vencido y lo mas prioritario.
 */

export type Vista = "abiertos" | "atendidos" | "rechazados" | "todos";

export const VISTAS: { id: Vista; label: string }[] = [
  { id: "abiertos", label: "Abiertos" },
  { id: "atendidos", label: "Atendidos" },
  { id: "rechazados", label: "Rechazados" },
  { id: "todos", label: "Todos" },
];

export type Etapa = "borrador" | "observado" | "aprobacion" | "atencion" | "parcial" | "atendido" | "rechazado";

export const ETAPAS: Record<Etapa, { label: string; marca: string; estados: EstadoRequerimiento[] }> = {
  borrador: { label: "Borradores", marca: "bg-slate-400", estados: ["BORRADOR"] },
  observado: { label: "Observados", marca: "bg-rose-500", estados: ["OBSERVADO"] },
  aprobacion: { label: "Por aprobar", marca: "bg-amber-500", estados: ["PENDIENTE_APROBACION"] },
  atencion: { label: "En atención", marca: "bg-blue-600", estados: ["VERIFICACION_STOCK", "EN_ATENCION"] },
  parcial: { label: "Atención parcial", marca: "bg-sky-500", estados: ["ATENCION_PARCIAL"] },
  atendido: { label: "Atendidos", marca: "bg-emerald-600", estados: ["ATENCION_COMPLETA"] },
  rechazado: { label: "Rechazados", marca: "bg-slate-500", estados: ["RECHAZADO"] },
};

/** Orden de las etapas abiertas: quien atiende ve primero su trabajo; quien pide, lo que debe corregir. */
export function etapasAbiertas(esGestor: boolean): Etapa[] {
  return esGestor
    ? ["atencion", "parcial", "aprobacion", "observado", "borrador"]
    : ["observado", "borrador", "aprobacion", "atencion", "parcial"];
}

export function etapaDe(estado: EstadoRequerimiento): Etapa {
  return (Object.keys(ETAPAS) as Etapa[]).find((e) => ETAPAS[e].estados.includes(estado)) ?? "atencion";
}

export function enVista(v: Vista, r: Requerimiento): boolean {
  const e = etapaDe(r.status);
  if (v === "todos") return true;
  if (v === "atendidos") return e === "atendido";
  if (v === "rechazados") return e === "rechazado";
  return e !== "atendido" && e !== "rechazado";
}

const PESO_PRIORIDAD: Record<Prioridad, number> = { CRITICA: 0, URGENTE: 1, NORMAL: 2 };

function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Dias que faltan para la fecha requerida (negativo = vencido); null si no tiene o ya se cerro. */
export function diasParaVencer(r: Requerimiento): number | null {
  const e = etapaDe(r.status);
  if (!r.requiredDate || e === "atendido" || e === "rechazado") return null;
  const [a, m, d] = r.requiredDate.split("-").map(Number);
  const [ha, hm, hd] = hoy().split("-").map(Number);
  return Math.round((Date.UTC(a, m - 1, d) - Date.UTC(ha, hm - 1, hd)) / 86_400_000);
}

export function estaVencido(r: Requerimiento): boolean {
  const d = diasParaVencer(r);
  return d != null && d < 0;
}

export function esPrioritario(r: Requerimiento): boolean {
  return r.priority === "CRITICA" || r.priority === "URGENTE";
}

/** Abiertos: vencidos, luego prioridad, luego la fecha requerida mas cercana y el mas antiguo. Cerrados: el mas reciente. */
export function ordenar(filas: Requerimiento[], abiertos: boolean): Requerimiento[] {
  const creado = (r: Requerimiento) => r.createdAt ?? "";
  if (!abiertos) return [...filas].sort((a, b) => creado(b).localeCompare(creado(a)));
  return [...filas].sort((a, b) => {
    const va = estaVencido(a) ? 0 : 1;
    const vb = estaVencido(b) ? 0 : 1;
    if (va !== vb) return va - vb;
    const p = PESO_PRIORIDAD[a.priority] - PESO_PRIORIDAD[b.priority];
    if (p !== 0) return p;
    const fa = a.requiredDate ?? "9999";
    const fb = b.requiredDate ?? "9999";
    if (fa !== fb) return fa.localeCompare(fb);
    return creado(a).localeCompare(creado(b));
  });
}

function normalizar(t: string): string {
  return t.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Busca en codigo, CECO, solicitante y productos; varias palabras deben aparecer todas. */
export function coincideBusqueda(r: Requerimiento, texto: string): boolean {
  const palabras = normalizar(texto).split(/\s+/).filter(Boolean);
  if (palabras.length === 0) return true;
  const fuente = normalizar([r.code, r.cecoNombre, r.requesterNombre, r.resumenItems].filter(Boolean).join(" "));
  return palabras.every((p) => fuente.includes(p));
}
