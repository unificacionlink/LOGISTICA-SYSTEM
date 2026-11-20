import { enCache, invalidar, luegoInvalidar, MINUTO } from "./cache";
import { http } from "./http";
import type {
  ComprasProveedor,
  ConfiguracionProveedores,
  DesempenoProveedor,
  DocumentosProveedor,
  EquivalenciaProveedor,
  EvaluacionProveedor,
  FichaProveedor,
  HistorialProveedor,
  PanelProveedores,
  PlantillaEvaluacion,
  ProductoDisponible,
  Proveedor,
  RequisitoDocumento,
  TipoEvaluacion,
} from "./types";

export interface GuardarProveedorInput {
  ruc: string;
  legalName: string;
  tradeName?: string;
  contactName?: string;
  contactRole?: string;
  email?: string;
  whatsappPhone?: string;
  phone?: string;
  website?: string;
  address?: string;
  city?: string;
  supplierType: string;
  preferredDeliveryMode?: string;
  offeringDescription?: string;
  paymentCondition?: string;
  creditDays?: number | null;
  leadDays?: number | null;
  warrantyTerms?: string;
  /** Empresa en la que se registra (DEC-L081). */
  empresaCode?: string;
}

const pedirProveedores = () => http.get<Proveedor[]>("/api/proveedores");

/**
 * Proveedores activos para elegir (comparativo, catalogo): en la pestana 5 min;
 * se invalida con cualquier cambio de un proveedor.
 */
export function listarProveedores(opciones: { fresco?: boolean } = {}) {
  if (opciones.fresco) {
    return pedirProveedores().then((res) => {
      if (res.ok) invalidar("proveedores");
      return res;
    });
  }
  return enCache("proveedores", 5 * MINUTO, pedirProveedores);
}

/** Pantalla de Proveedores: todos los estados e indicadores (DEC-L069). */
export function obtenerPanelProveedores() {
  return http.get<PanelProveedores>("/api/proveedores/gestion");
}

export function obtenerFichaProveedor(id: string) {
  return http.get<FichaProveedor>(`/api/proveedores/${id}/ficha`);
}

export function crearProveedor(input: GuardarProveedorInput) {
  return luegoInvalidar(http.post<Proveedor>("/api/proveedores", input), "proveedores");
}

export function actualizarProveedor(id: string, input: GuardarProveedorInput) {
  return luegoInvalidar(http.put<Proveedor>(`/api/proveedores/${id}`, input), "proveedores");
}

export function guardarCategoriasProveedor(id: string, items: { productTypeId: string; familyId: string | null }[]) {
  return http.put<FichaProveedor>(`/api/proveedores/${id}/categorias`, { items });
}

export function guardarProductosProveedor(id: string, productIds: string[]) {
  return http.put<FichaProveedor>(`/api/proveedores/${id}/productos`, { productIds });
}

export function listarProductosDisponibles() {
  return enCache("productos-proveedor", 10 * MINUTO, () =>
    http.get<ProductoDisponible[]>("/api/proveedores/productos-disponibles"),
  );
}

// Ciclo de incorporacion: cualquier cambio de estado mueve la lista que usa Compras.
export function enviarProveedorARevision(id: string) {
  return luegoInvalidar(http.post<FichaProveedor>(`/api/proveedores/${id}/enviar`), "proveedores");
}

export function decidirProveedor(id: string, decision: "HABILITAR" | "OBSERVAR" | "RECHAZAR", motivo?: string) {
  return luegoInvalidar(http.post<FichaProveedor>(`/api/proveedores/${id}/decision`, { decision, motivo }), "proveedores");
}

export function reabrirProveedor(id: string, motivo: string) {
  return luegoInvalidar(http.post<FichaProveedor>(`/api/proveedores/${id}/reabrir`, { motivo }), "proveedores");
}

export function cambiarActivoProveedor(id: string, activo: boolean, motivo?: string) {
  return luegoInvalidar(
    http.post<FichaProveedor>(`/api/proveedores/${id}/estado-activo`, { activo, motivo }),
    "proveedores",
  );
}

export function obtenerHistorialProveedor(id: string) {
  return http.get<HistorialProveedor[]>(`/api/proveedores/${id}/historial`);
}

// Seleccion y evaluaciones (DEC-L070).
export interface RegistrarEvaluacionInput {
  kind: TipoEvaluacion;
  evaluatedAt: string;
  area?: string;
  scope?: string;
  userAreaReviewer?: string;
  logisticsConformity: "SI" | "NO";
  userAreaConformity: "SI" | "NO";
  observations?: string;
  nextEvaluationDate?: string;
  criterios: { criterionId: string; score: number; observation?: string }[];
}

export function listarEvaluacionesProveedor(id: string) {
  return http.get<EvaluacionProveedor[]>(`/api/proveedores/${id}/evaluaciones`);
}

export function registrarEvaluacionProveedor(id: string, input: RegistrarEvaluacionInput) {
  return http.post<EvaluacionProveedor>(`/api/proveedores/${id}/evaluaciones`, input);
}

export function anularEvaluacionProveedor(evaluationId: string, motivo: string) {
  return http.post<EvaluacionProveedor>(`/api/proveedores/evaluaciones/${evaluationId}/anular`, { motivo });
}

export function obtenerConfiguracionProveedores() {
  return http.get<ConfiguracionProveedores>("/api/proveedores/configuracion");
}

export interface GuardarPlantillaInput {
  name: string;
  code: string;
  scaleMax: number;
  approvalThreshold: number;
  validityMonths: number;
  alertDays: number;
  criterios: { criterionId: string | null; name: string; weight: number; opciones: { score: number; label: string }[] }[];
}

export function guardarPlantillaEvaluacion(kind: TipoEvaluacion, input: GuardarPlantillaInput) {
  return http.put<PlantillaEvaluacion>(`/api/proveedores/configuracion/plantillas/${kind}`, input);
}

export function guardarRequisitosDocumento(
  requisitos: {
    requirementId: string | null;
    name: string;
    description?: string | null;
    required: boolean;
    hasExpiry: boolean;
    active: boolean;
  }[],
) {
  return http.put<RequisitoDocumento[]>("/api/proveedores/configuracion/requisitos", { requisitos });
}

// Documentos en el almacenamiento privado (DEC-L071): se descargan por el backend.
export function listarDocumentosProveedor(id: string) {
  return http.get<DocumentosProveedor>(`/api/proveedores/${id}/documentos`);
}

export function subirDocumentoProveedor(
  id: string,
  archivo: File,
  datos: { requirementId?: string; evaluationId?: string; issuedAt?: string; expiresAt?: string; notes?: string },
) {
  const form = new FormData();
  form.append("file", archivo);
  for (const [clave, valor] of Object.entries(datos)) {
    if (valor) form.append(clave, valor);
  }
  return http.postForm<DocumentosProveedor>(`/api/proveedores/${id}/documentos`, form);
}

export function descargarDocumentoProveedor(supplierDocumentId: string, nombre: string) {
  return http.descargar(`/api/proveedores/documentos/${supplierDocumentId}/archivo`, nombre);
}

export function anularDocumentoProveedor(supplierDocumentId: string, motivo: string) {
  return http.post<DocumentosProveedor>(`/api/proveedores/documentos/${supplierDocumentId}/anular`, { motivo });
}

// Historial comercial y desempeno, leidos de Compras y Almacen.
export function obtenerComprasProveedor(id: string) {
  return http.get<ComprasProveedor>(`/api/proveedores/${id}/compras`);
}

export function obtenerDesempenoProveedor(id: string) {
  return http.get<DesempenoProveedor>(`/api/proveedores/${id}/desempeno`);
}

/** Como llama este proveedor a nuestros productos (DEC-L053). */
export function listarEquivalencias(supplierId: string) {
  return http.get<EquivalenciaProveedor[]>(`/api/proveedores/${supplierId}/equivalencias`);
}

export function quitarEquivalencia(equivalenceId: string) {
  return http.delete<null>(`/api/proveedores/equivalencias/${equivalenceId}`);
}

// ---------------------------------------------------------------- cuentas bancarias (DEC-L078)

/** Cuenta bancaria del proveedor; solo la ven Logística y Administración. */
export interface CuentaBancaria {
  id: string;
  banco: string;
  tipo: "AHORROS" | "CORRIENTE" | "DETRACCION" | "OTRA";
  moneda: string;
  numero: string;
  cci: string | null;
  principal: boolean;
  registradaEn: string;
}

export type GuardarCuentaBancaria = Omit<CuentaBancaria, "id" | "registradaEn">;

export function listarCuentasBancarias(supplierId: string) {
  return http.get<CuentaBancaria[]>(`/api/proveedores/${supplierId}/cuentas-bancarias`);
}

export function agregarCuentaBancaria(supplierId: string, cuenta: GuardarCuentaBancaria) {
  return http.post<CuentaBancaria[]>(`/api/proveedores/${supplierId}/cuentas-bancarias`, cuenta);
}

export function editarCuentaBancaria(cuentaId: string, cuenta: GuardarCuentaBancaria) {
  return http.put<CuentaBancaria[]>(`/api/proveedores/cuentas-bancarias/${cuentaId}`, cuenta);
}

export function quitarCuentaBancaria(cuentaId: string) {
  return http.delete<CuentaBancaria[]>(`/api/proveedores/cuentas-bancarias/${cuentaId}`);
}
