import { http } from "./http";

// Registro de la plataforma para el administrador (DEC-L060).

export interface PaginaRegistro<T> {
  filas: T[];
  hayMas: boolean;
}

export interface ActividadRegistro {
  id: string;
  en: string;
  usuarioId: string | null;
  usuario: string | null;
  correo: string | null;
  metodo: string;
  ruta: string;
  modulo: string | null;
  entidadId: string | null;
  status: number;
  ms: number;
  ip: string | null;
  navegador: string | null;
  detalle: string | null;
}

export interface ErrorRegistro {
  id: string;
  en: string;
  origen: "SERVIDOR" | "NAVEGADOR";
  usuarioId: string | null;
  usuario: string | null;
  metodo: string | null;
  ruta: string | null;
  status: number | null;
  tipo: string | null;
  mensaje: string | null;
  pila: string | null;
  navegador: string | null;
}

export interface CambioRegistro {
  id: string;
  en: string;
  usuarioId: string | null;
  usuario: string | null;
  entidad: string;
  entidadId: string;
  accion: string;
  /** JSON del estado anterior/posterior (texto). */
  antes: string | null;
  despues: string | null;
}

export interface MovimientoRegistro {
  id: string;
  en: string;
  usuarioId: string | null;
  usuario: string | null;
  empresa: string;
  almacen: string;
  codigo: string;
  producto: string;
  tipo: string;
  cantidad: number;
  costoUnitario: number | null;
  referenciaTipo: string;
  referenciaId: string;
}

export interface ResumenRegistro {
  operaciones: number;
  rechazadas: number;
  ingresosFallidos: number;
  erroresServidor: number;
  erroresNavegador: number;
}

export interface FiltrosRegistro {
  desde: string;
  hasta: string;
  usuario: string;
  q: string;
  tipo: string;
  pagina: number;
}

function consulta(f: FiltrosRegistro): string {
  const p = new URLSearchParams();
  if (f.desde) p.set("desde", f.desde);
  if (f.hasta) p.set("hasta", f.hasta);
  if (f.usuario) p.set("usuario", f.usuario);
  if (f.q.trim()) p.set("q", f.q.trim());
  if (f.tipo) p.set("tipo", f.tipo);
  p.set("pagina", String(f.pagina));
  return p.toString();
}

export function obtenerResumenRegistro() {
  return http.get<ResumenRegistro>("/api/admin/registro/resumen");
}

export function listarActividad(f: FiltrosRegistro) {
  return http.get<PaginaRegistro<ActividadRegistro>>(`/api/admin/registro/actividad?${consulta(f)}`);
}

export function listarErrores(f: FiltrosRegistro) {
  return http.get<PaginaRegistro<ErrorRegistro>>(`/api/admin/registro/errores?${consulta(f)}`);
}

export function listarCambios(f: FiltrosRegistro) {
  return http.get<PaginaRegistro<CambioRegistro>>(`/api/admin/registro/cambios?${consulta(f)}`);
}

export function listarMovimientos(f: FiltrosRegistro) {
  return http.get<PaginaRegistro<MovimientoRegistro>>(`/api/admin/registro/movimientos?${consulta(f)}`);
}
