/**
 * Novedades de cada version, en lenguaje de usuario (pie "vX · Novedades" al
 * final de cada pantalla, sin fecha). Es la fuente de la version que muestra la app: la primera entrada es
 * la actual. docs/CHANGELOG.md sigue siendo el registro tecnico; al publicar
 * una version se actualizan los dos con el mismo numero.
 */
export interface VersionApp {
  version: string;
  titulo: string;
  cambios: string[];
}

export const NOVEDADES: VersionApp[] = [
  {
    version: "0.32.0",
    titulo: "Requerimientos desde el presupuesto",
    cambios: [
      "Al aprobar un presupuesto se crea un requerimiento en borrador por cada mes programado: solo quita lo que no va y envíalo.",
      "Si un producto tiene tallas o colores, elige cuál al editar el borrador.",
      "El presupuesto muestra los requerimientos que generó, mes a mes.",
      "Descarga el presupuesto en Excel desde la tarjeta de partidas.",
      "Compras › Consolidados muestra cuántas unidades se cotizaron y cuántas ofertas hay.",
    ],
  },
  {
    version: "0.31.1",
    titulo: "Cantidades sin decimales",
    cambios: [
      "Las cantidades (pedidos, recepciones, despachos, ajustes y presupuestos) solo aceptan números enteros.",
      "Los campos numéricos ya no dejan escribir letras, signos ni números demasiado largos.",
    ],
  },
  {
    version: "0.31.0",
    titulo: "Bandeja de requerimientos por trabajo pendiente",
    cambios: [
      "Requerimientos se ordena por lo que hay que hacer: Requieren atención, Compras, Despacho, Seguimiento y Cerrados.",
      "Cada pedido muestra su siguiente paso (Despachar, Cotizar, Adjudicar, Emitir OC...) y te lleva directo a hacerlo.",
      "Una barra muestra cuánto se entregó, qué está en camino, qué está listo en almacén y qué está en compra.",
      "Al tocar un pedido se abre un panel con el avance de cada producto, sus compras, entregas e historial, sin salir de la lista.",
      "Quien pide ve sus pendientes, lo que está en proceso y lo cerrado.",
    ],
  },
  {
    version: "0.30.0",
    titulo: "Requerimientos ordenados y comparativo de cotizaciones",
    cambios: [
      "Requerimientos: elige una empresa o míralas todas con su color, y ve lo abierto agrupado por etapa.",
      "Primero aparece lo vencido y lo urgente; cada pedido muestra cuántos días le quedan.",
      "Busca un requerimiento también por el producto que pide.",
      "Compras: elige los productos y sube hasta 3 cotizaciones en PDF o foto; el sistema lee el proveedor y los precios.",
      "Las cotizaciones se ven lado a lado con el mejor precio marcado, y adjudicas todo con un clic.",
      "Las órdenes de compra se emiten desde el mismo comparativo.",
      "Las novedades ahora están al pie de cada pantalla, junto al número de versión.",
    ],
  },
  {
    version: "0.29.0",
    titulo: "Stock renovado",
    cambios: [
      "Todo el stock está en un solo almacén, el de TDEM, y atiende también los requerimientos de GOYDEL y METRIN.",
      "Inventario por Excel: descarga la plantilla con todos los productos, escribe lo que contaste y el sistema ajusta solo las diferencias.",
      "Stock mínimo por producto, con aviso de los que están por debajo para reponer.",
      "Al tocar un producto ves su historial: cada ingreso, despacho y ajuste con fecha, documento, motivo y quién lo hizo.",
      "Filtros por con stock, bajo mínimo y sin stock, además de familia y buscador.",
      "El ajuste manual ahora permite ingresos y salidas, y pide el motivo.",
    ],
  },
  {
    version: "0.28.2",
    titulo: "Las fotos reemplazadas se eliminan",
    cambios: [
      "Al cambiar la foto de un producto, la foto anterior se elimina para no ocupar espacio.",
      "Cada noche el sistema borra solo las fotos que ya no usa ningún producto.",
    ],
  },
  {
    version: "0.28.1",
    titulo: "Corrección al cambiar fotos",
    cambios: [
      "Se corrigió el error \"Ocurrió un conflicto al guardar\" al cambiar la foto de un producto que ya tenía una.",
      "En el celular, el Catálogo de productos ya no encima el precio y los botones sobre el nombre.",
    ],
  },
  {
    version: "0.28.0",
    titulo: "Fotos de productos en lote",
    cambios: [
      "En Catálogo de productos, el menú ⋯ tiene \"Carga masiva de fotos\": arrastra la carpeta y cada foto se asigna sola al producto cuyo código lleva en el nombre (por ejemplo AC01001.jpg).",
      "Antes de subir verás qué fotos se reconocieron, cuáles no coinciden con ningún producto y cuáles reemplazarían una foto existente.",
      "Las fotos se reducen automáticamente para que suban rápido, varias a la vez.",
      "En el celular, el botón de cámara junto a \"Subir foto\" permite fotografiar el producto directamente.",
    ],
  },
  {
    version: "0.27.0",
    titulo: "Diseño renovado y búsqueda rápida",
    cambios: [
      "Nuevo buscador: presiona Ctrl+K (o el botón Buscar del menú) y escribe a dónde quieres ir, por ejemplo \"caja chica\" o \"nuevo requerimiento\".",
      "Tablas, estados y etiquetas más fáciles de leer, sin textos en mayúsculas.",
      "Botones y campos con un tamaño uniforme en todas las pantallas.",
      "La bandeja de Requerimientos tiene columnas más claras para cada empresa.",
    ],
  },
  {
    version: "0.26.0",
    titulo: "Personal de cada empresa",
    cambios: [
      "Nueva pestaña Personal en Administración: todo el personal de cada empresa, con una lista separada para TDEM, GOYDEL y METRIN.",
      "Desde RR.HH. llega todo el personal activo, aunque no tenga correo registrado.",
      "Con \"Dar acceso\" se crea la cuenta de una persona: se elige su correo y sus roles, y el sistema entrega la contraseña temporal.",
      "La carga por Excel del personal de GOYDEL y METRIN ahora está en la pestaña Personal y el correo es opcional.",
    ],
  },
  {
    version: "0.25.1",
    titulo: "Corrección de ingreso",
    cambios: ["Se corrigió el error \"No se pudo conectar con el servidor\" al iniciar sesión en el sistema publicado."],
  },
  {
    version: "0.25.0",
    titulo: "Compras consolidadas entre requerimientos",
    cambios: [
      "Al aprobar un requerimiento, lo que falta de cada producto se suma solo a un consolidado del mismo producto, junto con lo que piden otros requerimientos y CECOs.",
      "Cada consolidado muestra de qué CECO y requerimiento viene cada parte, para no confundir a quién va lo comprado.",
      "Si conviene comprar por Caja Chica (urgente para hoy o bajo monto), el faltante espera en Necesidades a que Logística elija la ruta.",
      "Una sola solicitud de cotización por proveedor con varios productos, enviada por correo o WhatsApp desde la pantalla.",
      "Comparativo de ofertas por producto: cada producto puede adjudicarse a un proveedor distinto y la orden de compra junta lo adjudicado a cada proveedor.",
      "Al recibir la mercadería se reparte entre los requerimientos de origen, primero los críticos y urgentes.",
      "En el Catálogo, cada producto puede tener varios proveedores habilitados.",
      "Nuevo botón Novedades en el menú con los cambios de cada versión.",
    ],
  },
  {
    version: "0.24.0",
    titulo: "Control presupuestal",
    cambios: [
      "PYC activa el control presupuestal por periodo.",
      "Al enviar un requerimiento se compara con el presupuesto aprobado del CECO; si se excede o no está presupuestado, se pide una justificación.",
      "Nueva pestaña Cumplimiento: programado, real, pendiente y saldo por producto y familia, con descarga en Excel para PYC.",
    ],
  },
  {
    version: "0.23.0",
    titulo: "Presupuestos semestrales",
    cambios: [
      "Nueva pantalla Presupuestos: programación por producto y mes para cada CECO y semestre.",
      "Emitir, aprobar u observar, modificar y anular presupuestos; los precios quedan fijos al aprobar.",
      "El aprobador de cada CECO se asigna desde la ficha del usuario en Administración.",
    ],
  },
  {
    version: "0.22.0",
    titulo: "Precios de venta y plazo de atención",
    cambios: [
      "Precio de venta por empresa en el Catálogo, registrado por PYC o Logística, con historial.",
      "Plazo de atención por producto: el pedido muestra la fecha estimada de atención.",
      "PYC y Gerencia pueden ver el Catálogo de productos.",
    ],
  },
  {
    version: "0.21.0",
    titulo: "Roles PYC y Gerencia, aprobador por CECO",
    cambios: [
      "Nuevos roles PYC y Gerencia.",
      "Cada CECO puede tener su aprobador; nadie aprueba su propio requerimiento.",
      "Todos los CECO de RR.HH. disponibles, sin repetidos y con nombres en mayúsculas.",
      "Catálogo y buscador muestran Familia › Subfamilia.",
    ],
  },
  {
    version: "0.20.0",
    titulo: "CECOs por usuario",
    cambios: [
      "El solicitante solo pide para los CECOs que tiene asignados, de cualquier empresa.",
      "Administración permite asignar varios CECOs a un usuario.",
    ],
  },
  {
    version: "0.19.0",
    titulo: "Personal de GOYDEL y METRIN",
    cambios: [
      "Carga de personal por Excel para GOYDEL y METRIN: altas, correcciones y ceses en un solo archivo.",
      "Edición de datos de usuarios manuales.",
      "Menú lateral organizado por secciones.",
    ],
  },
  {
    version: "0.18.0",
    titulo: "Observar y programar entregas",
    cambios: [
      "El aprobador puede observar un requerimiento con motivo; el solicitante lo corrige y lo reenvía.",
      "Logística programa la fecha de entrega sin cambiar la fecha requerida.",
      "Las acciones sensibles piden confirmación y, si son irreversibles, un motivo.",
    ],
  },
  {
    version: "0.17.0",
    titulo: "Caja Chica",
    cambios: [
      "Compras por Caja Chica con sugerencia según la regla (bajo monto, mismo día, falla de proveedor).",
      "Lo comprado entra al almacén reservado para el requerimiento.",
      "Regularización de compras y bloqueo de comprobantes duplicados.",
    ],
  },
  {
    version: "0.16.0",
    titulo: "Alta de productos",
    cambios: [
      "Logística crea productos nuevos en el Catálogo con su código SKU y variantes.",
      "Si no encuentras un producto al pedir, puedes solicitar su alta.",
    ],
  },
  {
    version: "0.15.0",
    titulo: "Usuarios desde RR.HH. y Administración",
    cambios: [
      "Los usuarios y CECOs se sincronizan con RR.HH.; el personal cesado pierde acceso.",
      "Pantalla de Administración: roles, accesos, contraseñas temporales y altas manuales.",
      "Cambio de contraseña, obligatorio después de una temporal.",
    ],
  },
  {
    version: "0.14.0",
    titulo: "PDFs y carga masiva de ingresos",
    cambios: [
      "PDF corporativo del requerimiento, solicitud de cotización, orden de compra, nota de inventario y tracking.",
      "Carga masiva de ingresos al almacén con plantilla Excel.",
    ],
  },
  {
    version: "0.13.0",
    titulo: "Nueva imagen",
    cambios: ["Colores corporativos en toda la aplicación y nueva pantalla de inicio de sesión."],
  },
  {
    version: "0.12.0",
    titulo: "Dashboard",
    cambios: [
      "Dashboard en Inicio: requerimientos abiertos, tiempos de aprobación y atención, respuesta de proveedores y ahorro.",
      "Requerimientos agrupados por empresa.",
      "Menú lateral que se pliega para dar más espacio.",
    ],
  },
  {
    version: "0.11.0",
    titulo: "Tracking del requerimiento",
    cambios: [
      "Seguimiento de cada requerimiento: avance, hitos, entregas e historial completo, con descarga en Excel.",
    ],
  },
  {
    version: "0.10.0",
    titulo: "Atención automática",
    cambios: [
      "Al aprobar un requerimiento se reserva solo el stock disponible, sin un paso manual.",
      "La pantalla Stock muestra saldos físico, reservado y disponible.",
    ],
  },
  {
    version: "0.9.0",
    titulo: "Almacén",
    cambios: [
      "Recepción de órdenes de compra con incidencias y guía.",
      "Despacho con nota de inventario, envío y confirmación de entrega en destino.",
      "El requerimiento pasa a atención parcial o completa según lo entregado.",
    ],
  },
  {
    version: "0.8.0",
    titulo: "Fotos de productos",
    cambios: ["Clic en una miniatura para ver la foto del producto en grande."],
  },
  {
    version: "0.7.0",
    titulo: "Proveedores y compras",
    cambios: [
      "Registro de proveedores.",
      "Solicitudes de cotización, registro de cotizaciones (con lectura automática del documento) y órdenes de compra.",
      "Los requerimientos en borrador se pueden editar.",
    ],
  },
  {
    version: "0.6.0",
    titulo: "Catálogo con fotos",
    cambios: ["Catálogo de productos con fotos y nuevo pedido tipo carrito."],
  },
  {
    version: "0.5.0",
    titulo: "Stock",
    cambios: ["Control de stock por almacén y ajustes de inventario."],
  },
  {
    version: "0.4.0",
    titulo: "Avisos en vivo",
    cambios: ["Los aprobadores ven llegar requerimientos y los solicitantes reciben la decisión sin recargar."],
  },
  {
    version: "0.3.0",
    titulo: "Aprobaciones",
    cambios: ["Bandeja de aprobaciones para aprobar o rechazar requerimientos."],
  },
  {
    version: "0.2.0",
    titulo: "Primera versión",
    cambios: ["Inicio de sesión y creación de requerimientos con el catálogo de 725 productos."],
  },
];

export const VERSION_ACTUAL = NOVEDADES[0].version;
