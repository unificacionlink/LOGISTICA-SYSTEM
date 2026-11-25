export type EstadoRequerimiento =
  | "BORRADOR"
  | "OBSERVADO"
  | "PENDIENTE_APROBACION"
  | "VERIFICACION_STOCK"
  | "EN_ATENCION"
  | "ATENCION_PARCIAL"
  | "ATENCION_COMPLETA"
  | "RECHAZADO";

/** DEC-L068: cada prioridad tiene su tiempo de atencion en dias habiles. */
export type Prioridad = "URGENTE" | "APROVISIONAMIENTO" | "IMPLEMENTACION";

/** Plazos por prioridad con la fecha tentativa si se envia hoy (GET /api/requerimientos/sla). */
export interface SlaRequerimiento {
  hoy: string;
  plazos: { prioridad: Prioridad; etiqueta: string; diasHabiles: number; fechaTentativa: string }[];
}
export type Proposito = "CONSUMO" | "VENTA" | "PRESTAMO" | "IMPLEMENTACION";

export interface Usuario {
  id: string;
  nombre: string;
  correo: string;
  companyId: string | null;
  empresaCode: string | null;
  roles: string[];
  debeCambiarPassword: boolean;
  /** Recorridos guiados ya vistos o saltados (DEC-L055). */
  tutorialesVistos: string[];
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
  /** Solo en el listado. */
  itemsCount?: number | null;
  /** Solo en el listado: nombres de los productos pedidos. */
  resumenItems?: string | null;
  /** DEC-L054: el solicitante sugiere la caja chica del proyecto. */
  cajaChicaSugerida: boolean;
  cajaChicaNota: string | null;
  /** DEC-L068: ultimo envio a aprobacion; desde ahi corre el tiempo de atencion. */
  submittedAt: string | null;
  /** transcurridos: dias habiles desde el envio (null en borrador o cerrado). */
  sla: { diasHabiles: number; transcurridos: number | null };
}

export interface RequerimientoItem {
  id: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  /** null en borradores generados desde un presupuesto con producto de tallas/colores (DEC-L049). */
  codigoVariante: string | null;
  unidadMedida: string;
  cantidad: number;
  requisitoTecnico: string | null;
  observacion: string | null;
  /** DEC-L054: unidades sugeridas para la caja chica del proyecto. */
  cantidadCajaChica: number;
}

export interface RequerimientoDetalle {
  requerimiento: Requerimiento;
  items: RequerimientoItem[];
  pendingApprovalId: string | null;
  /** RF-048: motivo vigente mientras el REQ esta OBSERVADO. */
  observacion: { motivo: string; observadoPor: string | null; fecha: string | null } | null;
  /** DEC-L038: ultimo control presupuestal al enviarlo; null si el periodo no lo exige. */
  controlPresupuestal: ControlPresupuestal | null;
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

/** Variante con el disponible del almacén central (físico - reservado), pedido aparte y fresco. */
export interface VarianteConStock extends VarianteCatalogo {
  stockDisponible: number;
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

/** SKU en el almacén central del grupo (DEC-L045). minimo null = sin mínimo. */
export interface SaldoStock {
  variantId: string;
  productId: string;
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string;
  tipoId: string;
  tipoNombre: string;
  familiaId: string;
  familiaNombre: string;
  unidad: string;
  fisico: number;
  reservado: number;
  disponible: number;
  minimo: number | null;
  ultimoMovimiento: string | null;
  /** Devuelto desde destino, recibido en cuarentena (no disponible). */
  enCuarentena: number;
}

export interface MovimientoKardex {
  fecha: string;
  tipo: "ENTRADA" | "SALIDA" | "AJUSTE" | string;
  origen: "RECEPCION" | "SALIDA" | "CAJA_CHICA" | "AJUSTE" | "CARGA_STOCK" | string;
  cantidad: number;
  saldo: number;
  referencia: string | null;
  empresa: string;
  motivo: string | null;
  usuario: string;
}

export interface Kardex {
  saldo: SaldoStock;
  movimientos: MovimientoKardex[];
}

export interface CargaStock {
  archivo: string;
  codigo: string | null;
  filas: number;
  contadas: number;
  ajustes: number;
  minimos: number;
  sinCambios: number;
  conError: number;
  aplicada: boolean;
  detalle: {
    fila: number;
    codigoVariante: string;
    producto: string | null;
    fisico: number | null;
    contado: number | null;
    diferencia: number | null;
    minimoAnterior: number | null;
    minimoNuevo: number | null;
    aviso: string | null;
    error: string | null;
  }[];
}

export interface ProveedorProducto {
  productId: string;
  supplierId: string;
  proveedorNombre: string;
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
  tradeName: string | null;
  contactRole: string | null;
  phone: string | null;
  website: string | null;
  address: string | null;
  city: string | null;
  offeringDescription: string | null;
  paymentCondition: string | null;
  creditDays: number | null;
  leadDays: number | null;
  warrantyTerms: string | null;
  status: EstadoProveedor;
  statusReason: string | null;
  submittedAt: string | null;
  decidedAt: string | null;
  createdAt: string;
  /** Empresa en la que se registro (DEC-L081): solo clasifica, no restringe compras. */
  empresaCode: string | null;
}

/** Ciclo de incorporacion del proveedor (DEC-L069). */
export type EstadoProveedor = "BORRADOR" | "PENDIENTE_REVISION" | "HABILITADO" | "OBSERVADO" | "RECHAZADO";

/** Vigencia de la calificacion o de un documento (DEC-L070). */
export type Vigencia = "VIGENTE" | "PROXIMO_A_VENCER" | "VENCIDO" | "SIN_EVALUACION";

export type TipoEvaluacion = "SELECCION" | "DESEMPENO";

export type ResultadoEvaluacion = "APROBADO" | "NO_APROBADO" | "PENDIENTE_CONFORMIDAD";

export interface FilaProveedor {
  supplierId: string;
  ruc: string;
  legalName: string;
  tradeName: string | null;
  contacto: string | null;
  supplierType: "CRITICO" | "NO_CRITICO";
  status: EstadoProveedor;
  active: boolean;
  city: string | null;
  productos: number;
  categorias: string | null;
  busqueda: string | null;
  ultimaNota: number | null;
  escala: number | null;
  ultimoResultado: ResultadoEvaluacion | null;
  ultimoTipo: TipoEvaluacion | null;
  ultimaEvaluacion: string | null;
  proximaEvaluacion: string | null;
  vigencia: Vigencia;
  empresaCode: string | null;
}

export interface PanelProveedores {
  hoy: string;
  kpis: {
    registrados: number;
    habilitados: number;
    enRevision: number;
    porVencer: number;
    vencidas: number;
    sinEvaluacion: number;
  };
  proveedores: FilaProveedor[];
}

export interface EvaluacionResumen {
  evaluationId: string;
  kind: TipoEvaluacion;
  templateCode: string;
  evaluatedAt: string;
  finalScore: number;
  scaleMax: number;
  threshold: number;
  result: ResultadoEvaluacion;
  nextEvaluationDate: string;
}

export interface FichaProveedor {
  proveedor: Proveedor;
  categorias: { productTypeId: string; familyId: string | null; tipo: string; familia: string | null }[];
  productos: { productId: string; skuBase: string; nombre: string; tipo: string; familia: string }[];
  ultimaSeleccion: EvaluacionResumen | null;
  ultimaEvaluacion: EvaluacionResumen | null;
  vigencia: Vigencia;
  proximaEvaluacion: string | null;
  advertencias: string[];
}

export interface EvaluacionProveedor {
  evaluationId: string;
  kind: TipoEvaluacion;
  templateCode: string;
  templateVersion: number;
  evaluatedAt: string;
  evaluador: string | null;
  area: string | null;
  scope: string | null;
  userAreaReviewer: string | null;
  logisticsConformity: "SI" | "NO" | null;
  userAreaConformity: "SI" | "NO" | null;
  finalScore: number;
  scaleMax: number;
  threshold: number;
  result: ResultadoEvaluacion;
  observations: string | null;
  nextEvaluationDate: string;
  status: "REGISTRADA" | "ANULADA";
  voidReason: string | null;
  voidedAt: string | null;
  criterios: {
    sortOrder: number;
    criterionName: string;
    weight: number;
    score: number;
    scoreLabel: string | null;
    observation: string | null;
  }[];
}

export interface PlantillaEvaluacion {
  templateId: string;
  kind: TipoEvaluacion;
  code: string;
  version: number;
  name: string;
  scaleMax: number;
  approvalThreshold: number;
  validityMonths: number;
  alertDays: number;
  criterios: {
    criterionId: string;
    sortOrder: number;
    name: string;
    weight: number;
    opciones: { score: number; label: string }[];
  }[];
}

export interface RequisitoDocumento {
  requirementId: string;
  name: string;
  description: string | null;
  required: boolean;
  hasExpiry: boolean;
  sortOrder: number;
  active: boolean;
}

export interface ConfiguracionProveedores {
  plantillas: PlantillaEvaluacion[];
  requisitos: RequisitoDocumento[];
}

export interface DocumentosProveedor {
  requisitos: {
    requirementId: string;
    name: string;
    description: string | null;
    required: boolean;
    hasExpiry: boolean;
    estado: "FALTA" | Vigencia;
    supplierDocumentId: string | null;
    expiresAt: string | null;
  }[];
  documentos: {
    supplierDocumentId: string;
    fileName: string;
    mimeType: string | null;
    requirementId: string | null;
    requisito: string | null;
    evaluationId: string | null;
    issuedAt: string | null;
    expiresAt: string | null;
    notes: string | null;
    status: "ACTIVO" | "ANULADO";
    voidReason: string | null;
    subidoPor: string | null;
    createdAt: string;
    vigencia: Vigencia;
  }[];
}

export interface HistorialProveedor {
  accion: string;
  estadoAntes: string | null;
  estadoDespues: string | null;
  motivo: string | null;
  usuario: string | null;
  fecha: string;
}

export interface ProductoDisponible {
  productId: string;
  skuBase: string;
  nombre: string;
  tipo: string;
  familia: string;
}

export interface ComprasProveedor {
  rfqs: { enviadas: number; respondidas: number; diasRespuestaPromedio: number | null };
  totales: { moneda: string; ordenes: number; total: number }[];
  cotizaciones: {
    quotationId: string;
    rfqCode: string;
    numero: string | null;
    recibida: string | null;
    moneda: string;
    total: number;
    plazoDias: number | null;
    vigenteHasta: string | null;
    status: string;
  }[];
  ordenes: {
    orderId: string;
    orderCode: string;
    empresa: string;
    emitida: string;
    status: string;
    moneda: string;
    total: number;
    pago: string;
    aprobada: string | null;
    liberada: string | null;
    primeraRecepcion: string | null;
  }[];
  productos: {
    productId: string;
    skuBase: string;
    nombre: string;
    variante: string | null;
    moneda: string;
    ordenes: number;
    cantidad: number;
    ultimoPrecio: number;
    precioMinimo: number;
    precioMaximo: number;
    ultimaCompra: string;
    precios: { fecha: string; precio: number; orderCode: string }[];
  }[];
}

export interface DesempenoProveedor {
  ordenes: number;
  ordenesConRecepcion: number;
  cantidadOrdenada: number;
  cantidadRecibida: number;
  atencionPct: number | null;
  recepciones: number;
  recepcionesObservadas: number;
  incidencias: {
    total: number;
    abiertas: number;
    cerradas: number;
    diasCierrePromedio: number | null;
    porTipo: { tipo: string; cantidad: number }[];
  };
  plazoRealPromedioDias: number | null;
  plazoOfrecido: { ordenesConPlazo: number; dentroDelPlazo: number };
  cotizaciones: number;
  cotizacionesAdjudicadas: number;
  pendientes: { indicador: string; motivo: string }[];
}

export interface Moneda {
  currencyCode: string;
  name: string;
}

export type EstadoRfq = "BORRADOR" | "ENVIADA" | "RESPONDIDA" | "CERRADA";
export type EstadoCotizacion = "RECIBIDA" | "VALIDADA" | "DESCARTADA" | "SELECCIONADA";
export type EstadoOrden =
  | "POR_APROBAR"
  | "EMITIDA"
  | "EN_ATENCION"
  | "RECEPCION_PARCIAL"
  | "RECEPCION_COMPLETA"
  | "CERRADA"
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
  /** Solo si cotiza un unico requerimiento. */
  requestId: string | null;
  /** Codigos de los requerimientos de origen, separados por coma. */
  requestCode: string | null;
  supplierId: string;
  proveedorNombre: string;
  empresaCode: string;
  issueDate: string;
  paymentCondition: string | null;
  deliveryMode: string | null;
  deliveryLocation: string | null;
  requestedLeadDays: number | null;
  currencyRequested: string | null;
  status: EstadoRfq;
  createdAt: string;
  sentAt: string | null;
  sentChannel: "CORREO" | "WHATSAPP" | "OTRO" | null;
}

export interface RfqItem {
  rfqItemId: string;
  consolidationId: string | null;
  consolidationCode: string | null;
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
  source: OrigenCotizacion;
  items: CotizacionItem[];
}

export interface RfqDetalle {
  rfq: Rfq;
  items: RfqItem[];
  cotizaciones: Cotizacion[];
  proveedorRuc: string | null;
  empresaCode: string;
  proveedorEmail: string | null;
  proveedorTelefono: string | null;
  proveedorContacto: string | null;
}

export interface ComprasDetalle {
  requerimiento: Requerimiento;
  /** cantidadPendiente = lo que aun se puede comprar por Caja Chica. */
  items: ItemPendienteCompra[];
  rfqs: Rfq[];
  necesidades: Necesidad[];
}

// ---------------------------------------------------------------- Lote 16 (DEC-L039)

export type EstadoNecesidad = "PENDIENTE_RUTA" | "COMPRA" | "CAJA_CHICA" | "ANULADA";
export type EstadoConsolidado = "ABIERTA" | "EN_COTIZACION" | "ADJUDICADA" | "ORDENADA" | "ANULADA";

export interface Necesidad {
  needId: string;
  requestId: string;
  requestCode: string;
  empresaCode: string;
  cecoCode: string;
  cecoNombre: string;
  priority: Prioridad;
  requiredDate: string | null;
  skuBase: string;
  codigoVariante: string | null;
  nombreProducto: string;
  atributo: string | null;
  unidadMedida: string;
  qty: number;
  status: EstadoNecesidad;
  suggestedReason: "BAJO_MONTO" | "MISMO_DIA" | "FALLA_PROVEEDOR" | "SOLICITANTE" | null;
  consolidationId: string | null;
  consolidationCode: string | null;
  consolidationStatus: EstadoConsolidado | null;
  createdAt: string;
  /** DEC-L054: nota del solicitante al sugerir la caja chica. */
  notaSolicitante: string | null;
  /** Disponible en el almacen central (solo en la bandeja de Necesidades). */
  stockDisponible: number | null;
}

export interface OrigenConsolidado {
  needId: string;
  requestId: string;
  requestCode: string;
  cecoCode: string;
  cecoNombre: string;
  priority: Prioridad;
  requiredDate: string | null;
  qty: number;
}

export interface Adjudicacion {
  quotationItemId: string;
  quotationId: string;
  proveedorNombre: string;
  currencyCode: string;
  qty: number;
  unitCost: number;
  lineTotal: number;
}

/** Proveedor al que se le puede pedir cotizacion (DEC-L082); consolidados = los que cubre. */
export interface CandidatoRfq {
  supplierId: string;
  nombre: string;
  ruc: string;
  critico: boolean;
  empresaCode: string | null;
  consolidados: string[];
}

export interface Consolidado {
  consolidationId: string;
  consolidationCode: string;
  empresaCode: string;
  productId: string;
  skuBase: string;
  codigoVariante: string | null;
  nombreProducto: string;
  atributo: string | null;
  unidadMedida: string;
  totalQty: number;
  status: EstadoConsolidado;
  origenes: OrigenConsolidado[];
  /** Proveedores habilitados del producto. */
  proveedores: string[];
  cotizaciones: number;
  /** Mayor cantidad ofertada en las cotizaciones vigentes; null si aun no hay ofertas. */
  cantidadCotizada: number | null;
  adjudicado: Adjudicacion | null;
  createdAt: string;
}

export interface Oferta {
  rfqId: string;
  rfqCode: string;
  rfqStatus: EstadoRfq;
  proveedorNombre: string;
  quotationId: string | null;
  quotationItemId: string | null;
  supplierQuoteNumber: string | null;
  cotizacionStatus: EstadoCotizacion | null;
  currencyCode: string | null;
  qty: number;
  unitCost: number | null;
  lineTotal: number | null;
  paymentCondition: string | null;
  deliveryMode: string | null;
  leadDays: number | null;
  offeredBrand: string | null;
  offeredModel: string | null;
  documentoUrl: string | null;
  adjudicada: boolean;
}

export interface ConsolidadoDetalle {
  consolidado: Consolidado;
  ofertas: Oferta[];
  ultimoCosto: number | null;
  orderId: string | null;
  orderCode: string | null;
}

export interface PorEmitir {
  quotationId: string;
  supplierQuoteNumber: string | null;
  proveedorNombre: string;
  empresaCode: string;
  currencyCode: string;
  paymentCondition: string | null;
  total: number;
  lineas: {
    consolidationId: string;
    consolidationCode: string;
    skuBase: string;
    codigoVariante: string | null;
    nombreProducto: string;
    unidadMedida: string;
    qty: number;
    unitCost: number;
    lineTotal: number;
  }[];
}

export interface Orden {
  orderId: string;
  orderCode: string;
  /** Solo si compra para un unico requerimiento. */
  requestId: string | null;
  /** Codigos de los requerimientos de origen, separados por coma. */
  requestCode: string | null;
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
  createdById: string;
  createdByName: string;
  approvedAt: string | null;
  /** Control del pago (DEC-L061): no frena la recepcion. */
  paymentStatus: EstadoPagoOrden;
  paymentProofUrl: string | null;
  paidAt: string | null;
  /** Lista para recibir en el almacen. */
  releasedAt: string | null;
  releaseNote: string | null;
  /** Por que el almacen todavia no puede recibirla; null si puede o ya no aplica. */
  bloqueoRecepcion: string | null;
  /** Formato F-LOG-09 (DEC-L079): como se aplica el IGV; total = PRECIO TOTAL. */
  taxMode: ModoIgv;
  /** VALOR TOTAL (sin IGV). */
  subtotal: number;
  taxAmount: number;
  deliveryTerm: string | null;
  additionalSpecs: string | null;
  /** OC directa (DEC-L080): referencias opcionales y si solo trae servicios. */
  cecoCode: string | null;
  cecoNombre: string | null;
  servicesOnly: boolean;
  /** El proveedor entrega en el CECO; no se recibe en el almacén (DEC-L084). */
  entregaDirecta: boolean;
  /** DEC-L089: plazo en días hábiles o calendario, lugar de la lista y fecha que debe entregar. */
  deliveryDays: number | null;
  deliveryDayType: TipoPlazo | null;
  deliveryPointId: string | null;
  fechaEntregaComprometida: string | null;
  /** DEC-L089: factura del proveedor registrada en el pago final. */
  invoiceNumber: string | null;
  invoiceUrl: string | null;
}

export type TipoPlazo = "HABILES" | "CALENDARIO";

/** Lugar de entrega de la OC (DEC-L089): el almacén central y las direcciones de los proyectos. */
export interface PuntoEntrega {
  id: string;
  nombre: string;
  direccion: string;
  tipo: string;
  empresaCode: string;
  cecoCode: string | null;
  cecoNombre: string | null;
}

/** Documento relacionado con una OC (DEC-L089): pagos, guías, recepciones y notas de inventario. */
export interface DocumentoOrden {
  tipo: "PAGO" | "GUIA" | "RECEPCION" | "NOTA_INVENTARIO";
  titulo: string;
  detalle: string | null;
  fecha: string | null;
  url: string | null;
  ruta: string | null;
}

export type ModoIgv = "MAS_IGV" | "IGV_INCLUIDO" | "NO_AFECTO";

/** Datos de la empresa para la cabecera de la OC (F-LOG-09, DEC-L079). */
export interface EmpresaDatos {
  id: string;
  code: string;
  nombre: string;
  ruc: string | null;
  direccion: string | null;
  telefono: string | null;
  tieneLogo: boolean;
  tieneFirmaGerencia: boolean;
  tieneFirmaLogistica: boolean;
}

export type EstadoPagoOrden = "PENDIENTE" | "ANTICIPO" | "PAGADO";

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
  /** Como llama el proveedor a este producto en su cotizacion (DEC-L053). */
  referenciaProveedor: string | null;
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
export type EstadoNota = "BORRADOR" | "EMITIDA" | "DESPACHADA" | "RECEPCION_PARCIAL" | "RECEPCIONADA" | "ANULADA";
export type EstadoEnvio =
  | "PENDIENTE"
  | "EN_TRANSITO"
  | "RECEPCION_PARCIAL"
  | "RECEPCIONADO"
  | "RECEPCIONADO_OBSERVADO"
  | "OBSERVADO"
  | "CERRADO";
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
  /** Entrega directa: requerimiento al que se entrego. */
  requestCode: string | null;
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
  /** Entrega directa (DEC-L084): CECO que recibio y su constancia; null si fue en el almacén. */
  cecoDestino: string | null;
  constanciaUrl: string | null;
}

/** Entrega directa del proveedor al CECO de una OC (DEC-L084). */
export interface EntregaDirecta {
  orderId: string;
  orderCode: string;
  proveedor: string;
  proveedorRuc: string;
  estado: EstadoOrden;
  /** Por qué todavía no se puede recibir (falta aprobar o liberar). */
  bloqueo: string | null;
  destinos: DestinoEntrega[];
  recepciones: Recepcion[];
  incidencias: Incidencia[];
}

export interface DestinoEntrega {
  cecoId: string;
  cecoCode: string;
  cecoNombre: string;
  empresaCode: string | null;
  lineas: LineaEntrega[];
}

export interface LineaEntrega {
  orderItemId: string;
  needId: string | null;
  requestItemId: string | null;
  requestId: string | null;
  requestCode: string | null;
  solicitante: string | null;
  sku: string;
  descripcion: string;
  unidad: string | null;
  asignado: number;
  recibido: number;
  pendiente: number;
  puedeRegistrar: boolean;
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
  conformityStatus: "CONFORME" | "NO_CONFORME" | null;
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
  /** Preparacion antes del envio (ALM-02): picking y embalaje. */
  pickedAt: string | null;
  packedAt: string | null;
  packingEvidenceUrl: string | null;
  /** DEC-L086: empresa a la que TDEM vende lo despachado (null si el destino es de TDEM) y su total en PEN. */
  empresaVentaCode: string | null;
  totalSaleValue: number | null;
  /** DEC-L088: anulacion con motivo (solo antes del envio). */
  annulledAt: string | null;
  annulmentReason: string | null;
}

export interface NotaItem {
  skuBase: string;
  nombreProducto: string;
  atributo: string | null;
  codigoVariante: string | null;
  qty: number;
  purchaseCostReference: number | null;
  /** Recepcion en destino (ALM-03): recibido = aceptado + observado + devuelto. */
  issueItemId: string;
  destRecibido: number;
  destAceptado: number;
  destObservado: number;
  destDevuelto: number;
  /** DEC-L086: precio de venta de TDEM a la empresa del destino (null si no es venta o no hay precio). */
  saleUnitPrice: number | null;
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
  evidenciaEmbalaje: Omit<DocumentoAdjunto, "documentType"> | null;
  recepciones: RecepcionDestino[];
  resoluciones: ResolucionDestino[];
}

export type ResultadoRecepcion = "CONFORME" | "NO_CONFORME";
export type AccionObservado = "SUBSANAR" | "DEVOLVER";

export interface RecepcionDestino {
  destinationReceiptId: string;
  numero: number;
  fecha: string;
  recibidoPor: string;
  resultado: ResultadoRecepcion;
  observacion: string | null;
  enlaceConformidad: string | null;
  documentoUrl: string | null;
  documentoNombre: string | null;
  items: { sku: string; nombreProducto: string; qty: number }[];
}

export interface ResolucionDestino {
  resolutionId: string;
  sku: string;
  nombreProducto: string;
  qty: number;
  accion: AccionObservado;
  evidencia: string;
  estado: "RESUELTA" | "EN_TRANSITO" | "RECIBIDA_OBSERVADA";
  fecha: string;
  registradoPor: string;
  recibidaEn: string | null;
  recibidaPor: string | null;
  evidenciaRetorno: string | null;
}

export interface DiferenciasAlmacen {
  observados: {
    shipmentId: string;
    inventoryNoteId: string;
    notaCodigo: string;
    requestCode: string;
    empresaCode: string;
    issueItemId: string;
    sku: string;
    nombreProducto: string;
    observado: number;
  }[];
  devoluciones: {
    resolutionId: string;
    inventoryNoteId: string;
    notaCodigo: string;
    requestCode: string;
    empresaCode: string;
    sku: string;
    nombreProducto: string;
    qty: number;
    estado: "EN_TRANSITO" | "RECIBIDA_OBSERVADA";
    evidencia: string;
    fecha: string;
    recibidaEn: string | null;
    evidenciaRetorno: string | null;
  }[];
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
  /** null en una entrega directa del proveedor (DEC-L084), que trae orderId. */
  inventoryNoteId: string | null;
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
  orderId: string | null;
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

// ---- Dashboard del Inicio (DEC-L020, DEC-L083) ----

export type CodigoEtapa = "POR_APROBAR" | "EN_COTIZACION" | "OC_EN_CURSO" | "POR_DESPACHAR" | "EN_TRANSITO";

export interface Dashboard {
  abiertos: number;
  /** Abiertos con la fecha requerida ya pasada. */
  vencidos: number;
  cumplimiento: {
    entregados: number;
    aTiempo: number;
    desdeStock: number;
    desdeStockATiempo: number;
    conCompra: number;
    conCompraATiempo: number;
  };
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

/** Barra de la pestaña Demanda: clave = familia, mes (YYYY-MM) o estado. */
export interface GrupoDemanda {
  clave: string;
  lineas: number;
  unidades: number | null;
}

export type ClaseAbc = "A" | "B" | "C";

export interface DemandaDashboard {
  requerimientos: number;
  lineas: number;
  skus: number;
  familias: number;
  porFamilia: GrupoDemanda[];
  porMes: GrupoDemanda[];
  productos: { sku: string; nombre: string; lineas: number; unidades: number }[];
  porEstado: GrupoDemanda[];
  abc: {
    total: number;
    clases: { clase: ClaseAbc; productos: number; valor: number }[];
    productos: { sku: string; nombre: string; unidades: number; valor: number; pct: number; clase: ClaseAbc }[];
    sinCosto: number;
  };
  totalDetalle: number;
  detalle: {
    requerimiento: string;
    empresa: string;
    ceco: string | null;
    fecha: string;
    estado: EstadoRequerimiento;
    familia: string;
    subfamilia: string;
    sku: string;
    producto: string;
    cantidad: number;
  }[];
}

/** Monto de la pestaña Gasto (sin IGV); clave null en CECO = compra para stock. */
export interface MontoGasto {
  clave: string | null;
  detalle: string | null;
  moneda: string;
  monto: number;
  ordenes: number | null;
}

export interface GastoDashboard {
  monedas: { moneda: string; monto: number; ordenes: number; proveedores: number }[];
  meses: string[];
  porEmpresa: MontoGasto[];
  porMes: MontoGasto[];
  porCeco: MontoGasto[];
  porFamilia: MontoGasto[];
  proveedores: MontoGasto[];
}

/** Ventas de TDEM a GOYDEL y METRIN (DEC-L086), en PEN al precio de venta de cada empresa. */
export interface VentasDashboard {
  /** monto = solo lineas con precio; sinPrecio = lineas sin precio de venta. */
  totales: { empresa: string; monto: number; lineas: number; sinPrecio: number }[];
  meses: string[];
  porMes: { empresa: string; clave: string; detalle: string | null; monto: number }[];
  porCeco: { empresa: string; clave: string | null; detalle: string | null; monto: number }[];
  lineas: {
    fecha: string;
    documento: string;
    requerimiento: string | null;
    empresa: string;
    ceco: string | null;
    cecoNombre: string | null;
    sku: string;
    producto: string;
    cantidad: number;
    precio: number | null;
    total: number | null;
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

/** Persona del padron (DEC-L041). userId null = sin cuenta. */
export interface PersonalAdmin {
  id: string;
  empresaCode: string;
  dni: string;
  nombre: string;
  correo: string | null;
  cargo: string | null;
  cecoId: string | null;
  cecoCode: string | null;
  cecoNombre: string | null;
  fuente: "RRHH_SYNC" | "MANUAL";
  userId: string | null;
  /** Nadie se borra: se inactiva con fecha (DEC-L064). */
  activo: boolean;
  inactivadoEn: string | null;
  /** ADMIN que lo inactivo; null en un inactivo = cese de RR.HH. */
  inactivadoPor: string | null;
  /** INACTIVO lo hizo un ADMIN; CESADO lo trajo RR.HH. */
  estado: "ACTIVO" | "INACTIVO" | "CESADO";
  /** Sin cuenta y, si viene de RR.HH., cesado (DEC-L065). */
  eliminable: boolean;
}

export interface DarAccesoResultado {
  personal: PersonalAdmin[];
  /** null: la persona ya tenia cuenta y solo se vinculo. */
  acceso: { correo: string; password: string } | null;
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
  /** Suplente del aprobador (DEC-L087): también decide y recibe los avisos. */
  suplenteId: string | null;
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

export type TipoAtributo = "TALLA" | "CLASE" | "COLOR" | "MODELO" | "OTRO";

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

export type MotivoCajaChica = "BAJO_MONTO" | "MISMO_DIA" | "FALLA_PROVEEDOR" | "EXCEPCION_AUTORIZADA" | "SOLICITANTE";

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
  /** PROYECTO (DEC-L054) o ALMACEN (compras anteriores, entraron al stock). */
  destino: "PROYECTO" | "ALMACEN";
  items: {
    requestItemId: string;
    sku: string;
    producto: string;
    cantidad: number;
    costoUnitario: number;
    total: number;
  }[];
}

/** Compra con la caja chica del proyecto de un requerimiento (DEC-L054). */
export interface FormularioCajaChica {
  requestId: string;
  requestCode: string;
  empresaCode: string;
  cecoNombre: string | null;
  requestStatus: EstadoRequerimiento;
  puedeRegistrar: boolean;
  items: {
    requestItemId: string;
    sku: string;
    producto: string;
    atributo: string | null;
    unidad: string;
    /** Lo que Logistica paso a caja chica. */
    autorizado: number;
    /** Lo que falta registrar. */
    pendiente: number;
  }[];
  compras: CompraCajaChica[];
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
  controlObligatorio: boolean;
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
  controlObligatorio: boolean;
  /** REQ del periodo enviados con excepcion justificada (solo PYC, Gerencia, Logistica y Admin). */
  excepciones: ExcepcionPresupuestal[];
  /** REQ en borrador generados al aprobar, uno por mes (DEC-L049). */
  requerimientos: { id: string; codigo: string; mes: number; estado: EstadoRequerimiento; items: number }[];
}

// ---------------------------------------------------------------- Control presupuestal (Lote 15, DEC-L038)

export interface Desviacion {
  productId: string;
  sku: string;
  solicitado: number;
  disponible: number;
  exceso: number;
  motivo: "SIN_PRESUPUESTO_APROBADO" | "SKU_NO_PRESUPUESTADO" | "EXCESO";
}

export interface ControlPresupuestal {
  estado: "CUBIERTO" | "EXCEPCION_JUSTIFICADA";
  periodo: string;
  presupuestoId: string | null;
  presupuestoCodigo: string | null;
  monto: number | null;
  justificacion: string | null;
  desviaciones: Desviacion[];
  verificadoPor: string | null;
  fecha: string;
}

export interface ExcepcionPresupuestal {
  requestId: string;
  requestCode: string;
  estadoRequerimiento: EstadoRequerimiento;
  solicitante: string | null;
  justificacion: string;
  desviaciones: Desviacion[];
  fecha: string;
}

/** Montos [programado, real, pendiente, saldo, disponible]; null = sin precio / incompleto. */
export type MontosCumplimiento = (number | null)[];

export interface CumplimientoFila {
  productId: string;
  familia: string;
  subfamilia: string;
  sku: string;
  nombre: string;
  unidad: string;
  sinPresupuesto: boolean;
  programado: number;
  real: number;
  pendiente: number;
  saldo: number;
  disponible: number;
  precioVenta: number | null;
  venta: MontosCumplimiento;
  precioCompra: number | null;
  compra: MontosCumplimiento | null;
}

export interface Cumplimiento {
  presupuestoId: string;
  codigo: string;
  empresa: string;
  cecoCode: string;
  cecoNombre: string;
  periodo: string;
  version: number;
  corte: string;
  conCompra: boolean;
  puedeDescargar: boolean;
  filas: CumplimientoFila[];
  resumen: { familia: string; venta: MontosCumplimiento; compra: MontosCumplimiento | null }[];
}

export interface ProductoPresupuesto {
  productId: string;
  sku: string;
  nombre: string;
  familia: string;
  unidad: string;
  precio: number | null;
}

// ---------------------------------------------------------------------------
// Comparativo de cotizaciones (DEC-L047)
// ---------------------------------------------------------------------------

export interface ComparativoFila {
  consolidationId: string;
  consolidationCode: string;
  status: EstadoConsolidado;
  codigo: string;
  nombreProducto: string;
  atributo: string | null;
  unidadMedida: string;
  totalQty: number;
  adjudicadoQuotationItemId: string | null;
  adjudicadoProveedor: string | null;
}

export interface ComparativoPrecio {
  consolidationId: string;
  quotationId: string;
  quotationItemId: string;
  currencyCode: string;
  qty: number;
  unitCost: number;
  lineTotal: number;
  offeredBrand: string | null;
  offeredModel: string | null;
}

export interface ComparativoColumna {
  supplierId: string;
  proveedorNombre: string;
  ruc: string | null;
  rfqId: string;
  rfqCode: string;
  /** null: tiene RFQ pero aun no cotiza. */
  quotationId: string | null;
  supplierQuoteNumber: string | null;
  source: OrigenCotizacion | null;
  currencyCode: string | null;
  paymentCondition: string | null;
  deliveryMode: string | null;
  leadDays: number | null;
  documentoUrl: string | null;
  documentoNombre: string | null;
  precios: ComparativoPrecio[];
}

export interface Comparativo {
  empresaCode: string;
  filas: ComparativoFila[];
  proveedores: ComparativoColumna[];
}

export interface OcrComparativo {
  supplierId: string | null;
  proveedorDetectado: string | null;
  rucDetectado: string | null;
  fechaDetectada: string | null;
  monedaDetectada: string | null;
  totalDetectado: number | null;
  plazoDetectadoDias: number | null;
  /** consolidationId -> costo unitario leido. */
  precios: Record<string, number>;
  textoDetectado: string;
  /** consolidationId -> de que linea salio el precio y como se encontro (DEC-L053). */
  coincidencias: Record<string, { indiceLinea: number | null; origen: "SKU" | "EQUIVALENCIA" | "NOMBRE" }>;
  /** Lineas del documento con importes, para elegir otra en la revision. */
  lineas: OcrLinea[];
}

export interface OcrLinea {
  indice: number;
  texto: string;
  descripcion: string;
  codigo: string | null;
  importes: number[];
}

// ---------------------------------------------------------------------------
// Bandeja operativa de Requerimientos (DEC-L048)
// ---------------------------------------------------------------------------

export type CodigoAccion =
  | "DESPACHAR"
  | "EMITIR_OC"
  | "ADJUDICAR"
  | "REGISTRAR_CAJA_CHICA"
  | "CORREGIR_CAJA_CHICA"
  | "RECIBIR_EN_PROYECTO"
  | "REGULARIZAR_CAJA_CHICA"
  | "DECIDIR_RUTA"
  | "PEDIR_COTIZACION"
  | "ESPERA_COTIZACION"
  | "APROBAR_OC"
  | "LIBERAR_OC"
  | "RECIBIR_OC"
  | "CONFIRMAR_ENTREGA"
  | "APROBACION"
  | "CORRECCION"
  | "ENVIAR"
  | "CORREGIR";

export interface AccionBandeja {
  codigo: CodigoAccion;
  /** Depende de otro (proveedor, aprobador, destino). */
  espera: boolean;
  /** Id que necesita la pantalla: REQ, consolidados separados por coma, OC o NI. */
  destino: string | null;
}

export interface AvanceBandeja {
  /** Fracciones 0..1 promediadas por producto. */
  entregado: number;
  enCamino: number;
  listo: number;
  enCompra: number;
}

export interface FilaBandeja {
  id: string;
  code: string;
  empresaCode: string;
  cecoCode: string | null;
  cecoNombre: string | null;
  requesterUserId: string | null;
  solicitante: string | null;
  priority: Prioridad;
  status: EstadoRequerimiento;
  requiredDate: string | null;
  createdAt: string | null;
  itemsCount: number;
  itemsEntregados: number;
  resumenItems: string | null;
  avance: AvanceBandeja;
  acciones: AccionBandeja[];
  tablero: TableroFila;
}

/** Etapa del tablero de Logistica (DEC-L067): la primera del flujo con algo pendiente. */
export type EtapaTablero = "aprobacion" | "abastecimiento" | "preparacion" | "transito" | "diferencias" | "atendido";

export interface TableroFila {
  /** null: borrador o rechazado (no van al tablero). */
  etapa: EtapaTablero | null;
  /** Nota de inventario sin enviar: que paso le falta. */
  preparacion: "PICKING" | "EMBALAJE" | "ENVIO" | null;
  /** Unidades observadas en destino por resolver (DEC-L063). */
  observado: number;
}

export interface TableroRequerimientos {
  columnas: { id: EtapaTablero; label: string; total: number; tarjetas: FilaBandeja[] }[];
  empresas: { code: string; total: number }[];
}

export interface Bandeja {
  vista: string;
  vistas: { id: string; label: string; total: number }[];
  empresas: { code: string; total: number }[];
  total: number;
  pagina: number;
  porPagina: number;
  filas: FilaBandeja[];
}

export type EtapaCompraItem = "RUTA" | "CAJA_CHICA" | "POR_COTIZAR" | "EN_COTIZACION" | "ADJUDICADO" | "OC";

export interface AvanceItem {
  requestItemId: string;
  codigo: string;
  nombreProducto: string;
  atributo: string | null;
  unidadMedida: string;
  solicitado: number;
  entregado: number;
  enCamino: number;
  listo: number;
  enCompra: number;
  recibidoCompra: number;
  etapaCompra: EtapaCompraItem | null;
  ofertas: number;
  consolidationId: string | null;
  consolidationCode: string | null;
  orderId: string | null;
  orderCode: string | null;
}

// ---------------------------------------------------------------------------
// Enlace del proveedor para cotizar en linea (DEC-L052)

/** MANUAL: la registro Logistica; OCR: leida de su documento; ENLACE: la envio el proveedor. */
export type OrigenCotizacion = "OCR" | "MANUAL" | "ENLACE";

export interface EnlaceCotizacion {
  /** Solo al generarlo: despues no se puede recuperar. */
  url: string | null;
  token: string | null;
  creadoEl: string | null;
  venceEl: string;
  abiertoEl: string | null;
  respondidoEl: string | null;
  vigente: boolean;
}

export interface CotizacionPublicaLinea {
  rfqItemId: string;
  numero: number;
  sku: string;
  producto: string;
  atributo: string | null;
  unidad: string;
  /** Nombre de la unidad (Millar, Caja...); null si aun no esta definida. */
  unidadNombre: string | null;
  cantidad: number;
  especificacion: string | null;
  imagenUrl: string | null;
}

export interface CotizacionPublicaPrecio {
  rfqItemId: string;
  unitCost: number;
  offeredBrand: string | null;
  offeredModel: string | null;
}

export interface CotizacionPublica {
  empresa: string;
  rfqCode: string;
  proveedor: string;
  proveedorRuc: string;
  fechaEmision: string;
  monedaSolicitada: string | null;
  plazoSolicitadoDias: number | null;
  condicionPago: string | null;
  modalidadEntrega: string | null;
  lugarEntrega: string | null;
  venceEl: string;
  lineas: CotizacionPublicaLinea[];
  monedas: Moneda[];
  enviada: {
    enviadaEl: string | null;
    currencyCode: string;
    paymentCondition: string;
    deliveryMode: string;
    leadDays: number | null;
    expirationDate: string | null;
    supplierQuoteNumber: string | null;
    documentoNombre: string | null;
    precios: CotizacionPublicaPrecio[];
  } | null;
}

export interface EnviarCotizacionPublica {
  currencyCode: string;
  paymentCondition: string;
  deliveryMode: string;
  leadDays?: number;
  expirationDate?: string;
  supplierQuoteNumber?: string;
  precios: { rfqItemId: string; unitCost: number; offeredBrand?: string; offeredModel?: string }[];
}

/** Como llama un proveedor a uno de nuestros productos (DEC-L053). */
export interface EquivalenciaProveedor {
  equivalenceId: string;
  supplierDescription: string;
  supplierCode: string | null;
  skuBase: string;
  codigoVariante: string | null;
  nombreProducto: string;
  atributo: string | null;
  timesUsed: number;
  lastUsedAt: string | null;
}
