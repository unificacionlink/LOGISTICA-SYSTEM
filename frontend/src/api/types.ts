export type EstadoRequerimiento =
  | "BORRADOR"
  | "OBSERVADO"
  | "PENDIENTE_APROBACION"
  | "VERIFICACION_STOCK"
  | "EN_ATENCION"
  | "ATENCION_PARCIAL"
  | "ATENCION_COMPLETA"
  | "RECHAZADO";

export type Prioridad = "NORMAL" | "URGENTE" | "CRITICA";
export type Proposito = "CONSUMO" | "VENTA" | "PRESTAMO" | "IMPLEMENTACION";

export interface Usuario {
  id: string;
  nombre: string;
  correo: string;
  companyId: string | null;
  empresaCode: string | null;
  roles: string[];
  debeCambiarPassword: boolean;
}

export interface Requerimiento {
  id: string;
  code: string;
  requesterUserId: string | null;
  cecoId: string | null;
  cecoNombre: string | null;
  requesterNombre: string | null;
  priority: Prioridad;
  purpose: Proposito;
  justification: string;
  status: EstadoRequerimiento;
  requiredDate: string | null;
  /** RF-068: fecha comprometida por Logistica (no reemplaza la requerida). */
  scheduledDeliveryDate: string | null;
  createdAt: string | null;
  empresaCode: string | null;
}

export interface RequerimientoItem {
  id: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  unidadMedida: string;
  cantidad: number;
  requisitoTecnico: string | null;
  observacion: string | null;
}

export interface RequerimientoDetalle {
  requerimiento: Requerimiento;
  items: RequerimientoItem[];
  pendingApprovalId: string | null;
  /** RF-048: motivo vigente mientras el REQ esta OBSERVADO. */
  observacion: { motivo: string; observadoPor: string | null; fecha: string | null } | null;
}

export interface VarianteCatalogo {
  variantId: string;
  productId: string;
  skuBase: string;
  nombre: string;
  atributo: string | null;
  codigoVariante: string;
  etiqueta: string;
  familiaId: string;
  familiaNombre: string;
  tipoId: string;
  tipoNombre: string;
  imagenUrl: string | null;
  /** Plazo de atención del SKU en días calendario; null = sin configurar (DEC-L035). */
  plazoAtencionDias: number | null;
}

/** Subfamilia del maestro SKU (`familia_producto`) con su familia (`tipo_producto`), DEC-L034. */
export interface Familia {
  familyId: string;
  code: string;
  nombre: string;
  tipoId: string;
  tipoCode: string;
  tipoNombre: string;
}

export interface Ceco {
  cecoId: string;
  code: string;
  name: string;
  empresaCode: string;
}

export interface CatalogoFormulario {
  variantes: VarianteCatalogo[];
  familias: Familia[];
  cecos: Ceco[];
}

export interface AprobacionPendiente {
  approvalId: string;
  requerimiento: Requerimiento;
}

export interface EmpresaOpcion {
  companyId: string;
  code: string;
}

/** Precio de venta vigente de un SKU en una empresa (DEC-L035). */
export interface PrecioVenta {
  productId: string;
  precio: number;
  actualizadoPor: string;
  actualizadoEn: string;
}

export interface PreciosVenta {
  empresas: EmpresaOpcion[];
  companyId: string;
  precios: PrecioVenta[];
}

export interface SaldoStock {
  variantId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  fisico: number;
  reservado: number;
  disponible: number;
}

export interface ProveedorHabitual {
  productId: string;
  supplierId: string | null;
  proveedorNombre: string | null;
}

export interface SaldoAjustado {
  codigoVariante: string;
  saldoDisponible: number;
}

export interface Proveedor {
  supplierId: string;
  ruc: string;
  legalName: string;
  contactName: string | null;
  email: string | null;
  whatsappPhone: string | null;
  supplierType: "CRITICO" | "NO_CRITICO";
  preferredDeliveryMode: "AGENCIA" | "ALMACEN" | "RECOJO" | null;
  active: boolean;
}

export interface Moneda {
  currencyCode: string;
  name: string;
}

export type EstadoRfq = "BORRADOR" | "ENVIADA" | "RESPONDIDA" | "CERRADA";
export type EstadoCotizacion = "RECIBIDA" | "VALIDADA" | "DESCARTADA" | "SELECCIONADA";
export type EstadoOrden =
  | "EMITIDA"
  | "EN_ATENCION"
  | "RECEPCION_PARCIAL"
  | "RECEPCION_COMPLETA"
  | "PEND_VERIFICACION"
  | "VERIFICADA"
  | "ANULADA";

export interface ItemPendienteCompra {
  requestItemId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  unidadMedida: string;
  cantidadSolicitada: number;
  cantidadPendiente: number;
}

export interface Rfq {
  rfqId: string;
  rfqCode: string;
  requestId: string;
  requestCode: string;
  supplierId: string;
  proveedorNombre: string;
  issueDate: string;
  paymentCondition: string | null;
  deliveryMode: string | null;
  deliveryLocation: string | null;
  requestedLeadDays: number | null;
  currencyRequested: string | null;
  status: EstadoRfq;
  createdAt: string;
}

export interface RfqItem {
  rfqItemId: string;
  requestItemId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  unidadMedida: string;
  qty: number;
  technicalSpecification: string | null;
}

export interface CotizacionItem {
  quotationItemId: string;
  rfqItemId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  qty: number;
  unitCost: number;
  lineTotal: number;
  offeredBrand: string | null;
  offeredModel: string | null;
}

export interface Cotizacion {
  quotationId: string;
  rfqId: string;
  supplierId: string;
  proveedorNombre: string;
  supplierQuoteNumber: string | null;
  issueDate: string | null;
  expirationDate: string | null;
  currencyCode: string;
  subtotal: number;
  taxAmount: number;
  total: number;
  paymentCondition: string | null;
  deliveryMode: string | null;
  leadDays: number | null;
  status: EstadoCotizacion;
  documentoUrl: string | null;
  items: CotizacionItem[];
}

export interface RfqDetalle {
  rfq: Rfq;
  items: RfqItem[];
  cotizaciones: Cotizacion[];
  proveedorRuc: string | null;
  empresaCode: string;
}

export interface ComprasDetalle {
  requerimiento: Requerimiento;
  items: ItemPendienteCompra[];
  rfqs: Rfq[];
}

export interface Orden {
  orderId: string;
  orderCode: string;
  requestId: string;
  requestCode: string;
  proveedorNombre: string;
  orderType: string;
  issueDate: string;
  paymentCondition: string;
  deliveryMode: string;
  deliveryLocation: string | null;
  currencyCode: string;
  total: number;
  status: EstadoOrden;
  createdAt: string;
}

export interface OrdenItem {
  orderItemId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  description: string;
  unidadMedida: string | null;
  qtyOrdered: number;
  unitCost: number;
  lineTotal: number;
}

export interface OrdenDetalle {
  orden: Orden;
  items: OrdenItem[];
  proveedorRuc: string | null;
  empresaCode: string;
}

export interface DocumentoSubido {
  documentId: string;
  fileUrl: string;
  fileName: string;
}

export interface OcrItemSugerido {
  rfqItemId: string;
  codigoVariante: string;
  costoSugerido: number;
}

export interface OcrCotizacion {
  proveedorDetectado: string | null;
  rucDetectado: string | null;
  fechaDetectada: string | null;
  monedaDetectada: string | null;
  totalDetectado: number | null;
  plazoDetectadoDias: number | null;
  itemsSugeridos: OcrItemSugerido[];
  textoDetectado: string;
}

// ---------------------------------------------------------------------------
// Almacen (Lote 6): recepcion de OC, despacho con Nota de Inventario y envio
// ---------------------------------------------------------------------------

export type EstadoRecepcion = "PARCIAL" | "COMPLETA" | "OBSERVADA";
export type CondicionRecepcion = "CONFORME" | "OBSERVADO";
export type TipoIncidencia = "FALTANTE" | "DANO" | "PRODUCTO_ERRADO" | "OTRO";
export type AccionProveedor = "REPONER" | "CAMBIO" | "NOTA_CREDITO";
export type EstadoIncidencia = "ABIERTA" | "EN_GESTION" | "CERRADA";
export type EstadoNota = "BORRADOR" | "EMITIDA" | "DESPACHADA" | "RECEPCIONADA" | "ANULADA";
export type EstadoEnvio = "PENDIENTE" | "EN_TRANSITO" | "RECEPCIONADO" | "OBSERVADO" | "CERRADO";
export type EstadoBien = "NUEVO" | "USADO" | "REACONDICIONADO" | "OTRO";
export type ModalidadEntrega = "AGENCIA" | "PROVEEDOR_ENTREGA" | "RECOJO" | "TERRESTRE_NORMAL" | "COURIER" | "PROPIO";
export type TipoAdjuntoEnvio = "GUIA" | "TICKET_ENVIO" | "CONFORMIDAD_ENTREGA";

export interface ItemPorRecibir {
  orderItemId: string;
  skuBase: string | null;
  nombreProducto: string | null;
  atributo: string | null;
  codigoVariante: string | null;
  description: string;
  unidadMedida: string | null;
  qtyOrdered: number;
  qtyReceived: number;
  qtyPending: number;
}

export interface RecepcionItemRegistrado {
  orderItemId: string;
  description: string;
  qtyReceived: number;
  conditionStatus: CondicionRecepcion;
  observation: string | null;
}

export interface Recepcion {
  receiptId: string;
  receiptNumber: number;
  receiptDate: string;
  status: EstadoRecepcion;
  receivedByName: string;
  observation: string | null;
  guia: string | null;
  items: RecepcionItemRegistrado[];
}

export interface Incidencia {
  receiptIssueId: string;
  receiptNumber: number;
  orderItemId: string;
  itemDescription: string;
  issueType: TipoIncidencia;
  qtyAffected: number | null;
  description: string;
  supplierAction: AccionProveedor | null;
  status: EstadoIncidencia;
  createdAt: string;
  closedAt: string | null;
}

export interface RecepcionOrden {
  orden: Orden;
  proveedorRuc: string;
  items: ItemPorRecibir[];
  recepciones: Recepcion[];
  incidencias: Incidencia[];
}

export interface ItemPorDespachar {
  requestItemId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string | null;
  unidadMedida: string;
  qtyRequested: number;
  qtyDespachado: number;
  qtyEntregado: number;
  qtyPorDespachar: number;
}

export interface Envio {
  shipmentId: string;
  deliveryMode: ModalidadEntrega;
  carrierName: string | null;
  trackingCode: string | null;
  packageCount: number | null;
  declaredWeight: number | null;
  currencyCode: string | null;
  shippingCostNet: number | null;
  shippingTaxAmount: number | null;
  shippingCostTotal: number | null;
  shipDate: string | null;
  expectedDate: string | null;
  status: EstadoEnvio;
  receivedAt: string | null;
  receivedByName: string | null;
  deliveryObservation: string | null;
}

export interface NotaInventario {
  inventoryNoteId: string;
  inventoryNoteCode: string;
  requestId: string;
  requestCode: string;
  empresaCode: string;
  cecoNombre: string | null;
  movementType: string;
  pickingDate: string;
  pickingUserName: string;
  itemState: EstadoBien;
  totalItemQty: number | null;
  totalValueSnapshot: number | null;
  packageCount: number | null;
  comments: string | null;
  status: EstadoNota;
  issuedAt: string | null;
  envio: Envio | null;
  almacenOrigen: string | null;
  cecoCodigo: string | null;
  solicitante: string | null;
}

export interface NotaItem {
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string | null;
  qty: number;
  purchaseCostReference: number | null;
}

export interface DocumentoAdjunto {
  documentId: string;
  documentType: TipoAdjuntoEnvio;
  fileName: string;
  fileUrl: string;
  uploadedAt: string;
}

export interface NotaInventarioDetalle {
  nota: NotaInventario;
  items: NotaItem[];
  documentos: DocumentoAdjunto[];
}

export interface DespachoRequerimiento {
  requerimiento: Requerimiento;
  items: ItemPorDespachar[];
  notas: NotaInventario[];
}


// ---- Tracking del requerimiento (RF-039/081/083) ----

export type CodigoHito = "EMITIDO" | "APROBADO" | "RECEPCIONADO" | "ATENCION_PARCIAL" | "ATENCION_COMPLETA";

export interface HitoTracking {
  codigo: CodigoHito;
  etiqueta: string;
  cumplido: boolean;
  fecha: string | null;
  detalle: string;
}

export interface EntregaTracking {
  posicion: number;
  inventoryNoteId: string;
  notaCodigo: string;
  emitidaEn: string | null;
  itemsDespachados: string;
  valorNota: number | null;
  shipmentId: string | null;
  modalidad: ModalidadEntrega | null;
  transportista: string | null;
  codigoSeguimiento: string | null;
  fechaEnvio: string | null;
  moneda: string | null;
  costoEnvioNeto: number | null;
  estadoEntrega: EstadoEnvio | "SIN_ENVIO";
  recepcionadoEn: string | null;
  urlGuia: string | null;
  urlTicket: string | null;
  urlConformidad: string | null;
}

export interface EventoTracking {
  fecha: string | null;
  etapa: string;
  accion: string;
  usuario: string | null;
  referencia: string | null;
}

export interface Tracking {
  requerimiento: Requerimiento;
  cecoCodigo: string;
  entregaProgramada: string | null;
  direccionEnvio: string | null;
  valorPedido: number;
  unidadesSolicitadas: number;
  unidadesEntregadas: number;
  porcentajeAtencion: number;
  hitos: HitoTracking[];
  entregas: EntregaTracking[];
  eventos: EventoTracking[];
}

// ---- Dashboard del Inicio (DEC-L020) ----

export type CodigoEtapa = "POR_APROBAR" | "EN_COTIZACION" | "OC_EN_CURSO" | "POR_DESPACHAR" | "EN_TRANSITO";

export interface Dashboard {
  abiertos: number;
  creados: number;
  completados: number;
  horasAprobacion: number | null;
  diasCiclo: number | null;
  rfqEnviadas: number;
  rfqRespondidas: number;
  diasRespuesta: number | null;
  ahorro: { moneda: string; monto: number; compras: number }[];
  comprasConComparativo: number;
  comprasTotal: number;
  etapas: { codigo: CodigoEtapa; cantidad: number }[];
  semanas: { semana: string; creados: number }[];
  proveedores: {
    proveedor: string;
    enviadas: number;
    respondidas: number;
    diasRespuesta: number | null;
    elegidas: number;
  }[];
  comparativos: {
    codigo: string;
    proveedor: string;
    cotizaciones: number;
    moneda: string;
    elegido: number;
    mayor: number;
    ahorro: number;
    fecha: string;
  }[];
}

// ---------- Carga masiva de ingresos (RF-031) ----------

export interface CargaMasivaFila {
  fila: number;
  orden: string;
  proveedor: string;
  sku: string;
  producto: string;
  cantidad: number | null;
  pendiente: number | null;
  estadoProducto: string;
  guia: string | null;
  error: string | null;
}

export interface CargaMasiva {
  archivo: string;
  omitidas: number;
  validas: number;
  conError: number;
  ordenes: number;
  aplicada: boolean;
  filas: CargaMasivaFila[];
}

// ---------- Administracion e identidad (DEC-L024) ----------

/** Resultado de validar/aplicar el Excel de personal manual (DEC-L028). */
export interface PersonalCarga {
  archivo: string;
  nuevos: number;
  actualizados: number;
  desactivados: number;
  sinCambios: number;
  conError: number;
  aplicada: boolean;
  filas: {
    fila: number;
    empresa: string;
    dni: string;
    nombre: string;
    correo: string;
    accion: "NUEVO" | "ACTUALIZADO" | "DESACTIVADO" | "SIN_CAMBIOS" | null;
    error: string | null;
  }[];
}

export interface UsuarioAdmin {
  id: string;
  nombre: string;
  correo: string;
  dni: string | null;
  empresaCode: string | null;
  cargo: string | null;
  roles: string[];
  /** CECOs para los que puede pedir, de cualquier empresa (DEC-L029). */
  cecoIds: string[];
  fuente: "RRHH_SYNC" | "MANUAL";
  activo: boolean;
  sincronizadoEn: string | null;
  tieneAcceso: boolean;
  debeCambiarPassword: boolean;
}

export interface EmpresaAdmin {
  id: string;
  code: string;
  nombre: string;
  altaManual: boolean;
}

export interface CecoAdmin {
  id: string;
  empresaCode: string;
  code: string;
  nombre: string;
  activo: boolean;
  /** Aprobador asignado del CECO (DEC-L030); null = cualquier aprobador. */
  aprobadorId: string | null;
}

export interface SyncResultado {
  ejecutadoEn: string;
  recibidos: number;
  creados: number;
  actualizados: number;
  desactivados: number;
  cecosCreados: number;
  /** CECOs que RR.HH. ya no tiene (DEC-L031). */
  cecosDesactivados: number;
  avisos: string[];
}

export interface AdminDatos {
  usuarios: UsuarioAdmin[];
  empresas: EmpresaAdmin[];
  cecos: CecoAdmin[];
  roles: string[];
  syncConfigurado: boolean;
  ultimaSincronizacion: string | null;
  ultimoResultado: SyncResultado | null;
}

// ---------- Maestro de productos (DEC-L025) ----------

export interface Clasificacion {
  tipos: { id: string; code: string; nombre: string; familias: { id: string; code: string; nombre: string }[] }[];
  unidades: { id: string; code: string; nombre: string }[];
}

export type TipoAtributo = "TALLA" | "COLOR" | "MODELO" | "OTRO";

export interface ProductoCreado {
  productId: string;
  skuBase: string;
  nombre: string;
  variantes: string[];
}

export interface SolicitudProducto {
  id: string;
  codigo: string;
  estado: "PENDIENTE_APROBACION_LOGISTICA" | "APROBADO" | "RECHAZADO" | "OBSERVADO" | "BORRADOR" | "DUPLICADO";
  empresaCode: string;
  solicitante: string;
  solicitadaEn: string;
  familyId: string;
  familia: string;
  nombre: string;
  uomId: string;
  unidad: string;
  atributoTipo: TipoAtributo | null;
  atributoValor: string | null;
  descripcionTecnica: string | null;
  justificacion: string;
  posibleDuplicado: string | null;
  comentarioRevision: string | null;
  skuGenerado: string | null;
}

// ---------- Caja Chica (DEC-L026) ----------

export type MotivoCajaChica = "BAJO_MONTO" | "MISMO_DIA" | "FALLA_PROVEEDOR" | "EXCEPCION_AUTORIZADA";

export interface ReglaCajaChica {
  id: string;
  empresaCode: string | null;
  umbral: number;
  moneda: string;
  bajoMonto: boolean;
  mismoDia: boolean;
  fallaProveedor: boolean;
  excepcion: boolean;
  actualizadaEn: string | null;
}

export interface EvaluacionCajaChica {
  regla: ReglaCajaChica;
  estimado: number | null;
  motivos: { codigo: MotivoCajaChica; detalle: string }[];
}

export interface CompraCajaChica {
  id: string;
  codigo: string;
  requestId: string;
  requestCode: string;
  empresaCode: string;
  fechaCompra: string;
  proveedor: string;
  proveedorRuc: string | null;
  comprobante: string;
  moneda: string;
  monto: number;
  motivo: MotivoCajaChica;
  motivoDetalle: string | null;
  estado: "PENDIENTE_REGULARIZACION" | "REGULARIZADO" | "OBSERVADO" | "ANULADO";
  observacion: string | null;
  codigoExterno: string | null;
  documentoUrl: string | null;
  registradoPor: string;
  registradoEn: string;
  items: { sku: string; producto: string; cantidad: number; costoUnitario: number; total: number }[];
}

// ---------------------------------------------------------------- Presupuestos (Lote 14, DEC-L036)

export type EstadoPresupuesto = "BORRADOR" | "EMITIDO" | "APROBADO" | "OBSERVADO" | "MODIFICADO" | "ANULADO";

export interface PeriodoPresupuestal {
  periodId: string;
  cecoId: string;
  empresa: string;
  cecoCode: string;
  cecoNombre: string;
  anio: number;
  semestre: number;
  cerrado: boolean;
  presupuestoId: string | null;
  presupuestoCodigo: string | null;
}

export interface CecoOpcion {
  cecoId: string;
  empresa: string;
  code: string;
  nombre: string;
}

export interface PeriodosPresupuestales {
  periodos: PeriodoPresupuestal[];
  cecosConfigurables: CecoOpcion[];
  puedeCrear: boolean;
}

export interface PresupuestoResumen {
  id: string;
  codigo: string;
  empresa: string;
  cecoCode: string;
  cecoNombre: string;
  anio: number;
  semestre: number;
  estado: EstadoPresupuesto;
  solicitante: string;
  aprobador: string | null;
  total: number | null;
  lineasSinPrecio: number;
  porDecidir: boolean;
  actualizadoEn: string;
}

export interface LineaPresupuesto {
  productId: string;
  sku: string;
  nombre: string;
  familia: string;
  unidad: string;
  meses: number[];
  cantidad: number;
  precio: number | null;
  importe: number | null;
  precioCongelado: boolean;
}

export interface PresupuestoDetalle {
  id: string;
  codigo: string;
  periodId: string;
  empresa: string;
  cecoCode: string;
  cecoNombre: string;
  anio: number;
  semestre: number;
  periodoCerrado: boolean;
  estado: EstadoPresupuesto;
  solicitanteId: string;
  solicitante: string;
  aprobador: string | null;
  observaciones: string | null;
  motivoDecision: string | null;
  emitidoEn: string | null;
  versionVigente: number | null;
  aprobadoEn: string | null;
  aprobadoPor: string | null;
  lineas: LineaPresupuesto[];
  total: number | null;
  lineasSinPrecio: number;
  permisos: { editar: boolean; emitir: boolean; decidir: boolean; modificar: boolean; anular: boolean };
  historial: { accion: string; usuario: string | null; fecha: string; motivo: string | null }[];
}

export interface ProductoPresupuesto {
  productId: string;
  sku: string;
  nombre: string;
  familia: string;
  unidad: string;
  precio: number | null;
}
