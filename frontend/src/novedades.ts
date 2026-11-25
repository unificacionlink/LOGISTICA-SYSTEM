/**
 * Novedades de cada version, en lenguaje de usuario (pie "vX · Novedades" al
 * final de cada pantalla, sin fecha). Es la fuente de la version que muestra la app: la primera entrada es
 * la actual. docs/CHANGELOG.md sigue siendo el registro tecnico; al publicar
 * una version se actualizan los dos con el mismo numero. vite.config.ts lee de
 * aqui la version actual (version.ts) para no cargar todo el historial al entrar.
 */
export interface VersionApp {
  version: string;
  titulo: string;
  cambios: string[];
}

export const NOVEDADES: VersionApp[] = [
  {
    version: "0.71.0",
    titulo: "Repositorio de documentos",
    cambios: [
      "Nueva pantalla Documentos: reúne los requerimientos aprobados, las órdenes de compra, las notas de inventario, las constancias de pago, las facturas, las guías, los comprobantes de caja chica y los documentos de proveedores con su vencimiento.",
      "Se filtra por mes, tipo de documento, empresa o código, y se pueden descargar juntos en un ZIP ordenado por tipo, empresa y mes.",
      "El sistema guarda solo una copia en PDF de cada requerimiento aprobado, orden de compra aprobada y nota de inventario.",
    ],
  },
  {
    version: "0.70.0",
    titulo: "Un usuario, una sesión",
    cambios: [
      "Cada usuario puede tener una sola sesión abierta: si alguien ingresa con el mismo usuario en otro equipo, la sesión anterior se cierra con el aviso \"Tu sesión se abrió en otro dispositivo\".",
      "Al cerrar sesión, esa sesión ya no se puede volver a usar.",
    ],
  },
  {
    version: "0.69.0",
    titulo: "Plazo, lugar de entrega y factura en la orden de compra",
    cambios: [
      "El plazo de entrega de la orden se indica en días y se elige si son hábiles o calendario; la orden muestra la fecha en que el proveedor debe entregar.",
      "El lugar de entrega se elige de una lista de direcciones (empieza con el almacén central); si falta una, se agrega desde la misma orden.",
      "Al registrar el pago final se pide la factura del proveedor (número y archivo).",
      "La orden de compra muestra sus documentos: constancias de pago, factura, guías, recepciones y notas de inventario.",
    ],
  },
  {
    version: "0.68.0",
    titulo: "Búsquedas más fáciles, suplente y anular notas",
    cambios: [
      "Los productos se buscan por palabras sueltas y en cualquier orden (por ejemplo \"camisa denim tdem\"), y se pueden ver todos los resultados.",
      "El centro de costo se elige escribiendo su código o su nombre, y cada opción dice de qué empresa es.",
      "En la lista de requerimientos se ve primero el número, con la empresa, el CECO, el solicitante y la fecha; los productos, al abrirlo.",
      "Cada CECO puede tener un suplente del aprobador, que también aprueba y recibe los avisos.",
      "Logística puede anular una nota de inventario antes de enviarla, indicando el motivo: lo despachado vuelve al stock y la nota queda en la lista como anulada.",
    ],
  },
  {
    version: "0.67.0",
    titulo: "TDEM compra para las tres empresas",
    cambios: [
      "Todas las compras a proveedores salen a nombre de TDEM: lo que piden TDEM, GOYDEL y METRIN del mismo producto se compra junto.",
      "La orden de compra directa ya no pide empresa; el CECO puede ser de cualquiera de las tres.",
      "Las Notas de Inventario las emite TDEM. Lo que se entrega a un proyecto de GOYDEL o METRIN queda como venta, al precio de venta de esa empresa.",
      "Nueva pestaña Ventas en Inicio: lo vendido a GOYDEL y METRIN por mes y CECO, con descarga en Excel para facturar.",
    ],
  },
  {
    version: "0.66.1",
    titulo: "Recibir en proyecto y CECOs por empresa",
    cambios: [
      "La entrega directa del proveedor ahora dice \"Recibir en proyecto\".",
      "En Administración › CECOs se puede filtrar por empresa.",
    ],
  },
  {
    version: "0.66.0",
    titulo: "Entrega directa del proveedor en obra",
    cambios: [
      "Una orden de compra se puede marcar como entrega directa en obra: el proveedor lleva cada parte al CECO de su requerimiento y no pasa por el almacén.",
      "Cada CECO registra lo que recibió con la guía del proveedor y la constancia firmada; lo que llega mal queda como incidencia para que el proveedor lo reponga.",
      "El solicitante ve la entrega en su requerimiento y puede registrar lo que le llegó; la atención se actualiza sola.",
    ],
  },
  {
    version: "0.65.0",
    titulo: "Dashboard con Proceso, Demanda y Gasto",
    cambios: [
      "Inicio tiene tres pestañas: Proceso, Demanda y Gasto. El período y la empresa sirven para las tres.",
      "Proceso muestra qué porcentaje de requerimientos se entregó a tiempo (desde stock y con compra) y cuántos abiertos ya pasaron su fecha.",
      "Demanda muestra qué se pide por familia, mes, producto y estado, la matriz ABC de lo despachado y el detalle de líneas, con descarga en Excel.",
      "Gasto muestra lo comprado sin IGV por empresa, mes, CECO, familia y proveedor.",
    ],
  },
  {
    version: "0.64.0",
    titulo: "Proveedores por empresa y cotizaciones a quien elijas",
    cambios: [
      "En Proveedores se puede filtrar por empresa; los indicadores siguen al filtro. Cada proveedor indica en qué empresa se registró.",
      "Al pedir cotización, Compras muestra los proveedores que atienden esos productos (primero los que cubren más) y tú marcas a cuáles pedirla.",
      "Los proveedores ya están clasificados por las familias del catálogo y los de seguridad figuran como críticos.",
    ],
  },
  {
    version: "0.63.0",
    titulo: "Orden de compra directa",
    cambios: [
      "Logística puede emitir una orden de compra sin pedir cotizaciones, desde Órdenes de compra › Orden directa.",
      "La orden puede llevar productos del catálogo y servicios descritos a mano; sale aprobada y con el formato F-LOG-09.",
      "Los productos se reciben en el almacén como siempre; una orden solo de servicios no pasa por el almacén y se cierra al registrar su pago.",
      "Se puede indicar un CECO y un requerimiento como referencia.",
    ],
  },
  {
    version: "0.62.0",
    titulo: "Orden de compra con el formato F-LOG-09",
    cambios: [
      "El PDF de la orden de compra sale con el formato F-LOG-09: logo y datos de la empresa, cuentas del proveedor, IGV, especificaciones y los V° B° de Gerencia y Logística.",
      "En cada orden se elige si los precios son sin IGV, con IGV incluido o no afectos, y se indican el plazo de entrega y las especificaciones mientras espera aprobación.",
      "Las firmas solo aparecen cuando la orden está aprobada.",
      "Administración tiene una pestaña Empresas para registrar RUC, dirección, teléfono, logo y firmas de cada empresa.",
    ],
  },
  {
    version: "0.61.0",
    titulo: "Cuentas bancarias de los proveedores",
    cambios: [
      "En la ficha de cada proveedor hay una pestaña nueva con sus cuentas bancarias: banco, tipo, moneda, número, CCI y la cuenta de detracción.",
      "Solo Logística y Administración pueden verlas y editarlas.",
    ],
  },
  {
    version: "0.60.0",
    titulo: "PYC con todos los CECOs",
    cambios: [
      "PYC ve y gestiona los presupuestos y precios de venta de todos los CECOs de las 3 empresas, sin tener que asignárselos.",
    ],
  },
  {
    version: "0.59.2",
    titulo: "Usuarios sin repetirse",
    cambios: [
      "En Administración › Usuarios, cada persona aparece una sola vez, en la pestaña de su rol principal, y debajo de su correo se ven sus otros roles.",
    ],
  },
  {
    version: "0.59.1",
    titulo: "Ficha del usuario más clara",
    cambios: [
      "En Administración, la ficha de cada usuario indica para qué le sirven sus CECOs: pedir, aprobar como Gerencia o presupuestos.",
    ],
  },
  {
    version: "0.59.0",
    titulo: "Tus requerimientos de un vistazo y avisos en la plataforma",
    cambios: [
      "En Inicio ves el resumen de todo lo que pediste: cuántos están por aprobar, en atención o entregados, cuánto ya te entregaron y lo que más pides.",
      "La campana del menú te avisa cuando tu requerimiento se aprueba, se observa, se rechaza, sale del almacén o se entrega.",
      "Esos avisos ya no llegan por correo: todo queda en la plataforma.",
    ],
  },
  {
    version: "0.58.0",
    titulo: "Códigos de variantes según su atributo",
    cambios: [
      "Al sugerir un producto con talla, clase, color, modelo u otro atributo, su código se arma solo según el tipo: por ejemplo, talla L queda -L, clase 0 queda -C0 y color azul queda -CLAZUL.",
      "Mientras escribes el atributo ves cómo quedará el código de la variante.",
    ],
  },
  {
    version: "0.57.0",
    titulo: "Guardados más rápidos",
    cambios: [
      "Aprobar un requerimiento, registrar recepciones y despachos y emitir órdenes de compra responde más rápido.",
      "La plataforma abre más rápido al iniciar sesión.",
    ],
  },
  {
    version: "0.56.0",
    titulo: "Pantallas más rápidas",
    cambios: [
      "Al volver a una pantalla ves al instante lo último que cargaste, y se pone al día sola en segundos.",
      "Después de aprobar o guardar, la información ya no desaparece mientras se actualiza.",
      "Guardar ya no oscurece toda la pantalla: solo verás el aviso si tarda más de un segundo.",
      "Al pasar el mouse por un enlace, su pantalla se prepara para abrir sin esperas.",
    ],
  },
  {
    version: "0.55.1",
    titulo: "Logística con su propio espacio de datos",
    cambios: [
      "La información y los archivos de Logística ahora se guardan en un espacio propio, separado del de RR.HH. No cambia nada en el uso de la plataforma.",
    ],
  },
  {
    version: "0.55.0",
    titulo: "Proveedores: incorporación, evaluación y desempeño",
    cambios: [
      "Proveedores muestra cuántos hay registrados, habilitados, en revisión y por reevaluar, y se busca por RUC, razón social, producto o categoría.",
      "Un proveedor nuevo pasa por borrador, revisión y habilitación; solo los habilitados aparecen en Compras. Si falta algo para habilitarlo, el sistema lo advierte y pide el motivo.",
      "Cada proveedor tiene su ficha con datos, documentos, selección (F-LOG-03), evaluaciones (F-LOG-07), compras y desempeño.",
      "La nota se calcula sola con los criterios y pesos de los formatos, y avisa cuándo toca reevaluar. Una evaluación vencida solo advierte: no frena compras.",
      "Los documentos del proveedor se guardan de forma privada y se descargan solo desde la plataforma.",
    ],
  },
  {
    version: "0.54.0",
    titulo: "Prioridad con tiempo de atención",
    cambios: [
      "Las prioridades ahora son Urgente (2 días hábiles), Aprovisionamiento (10 días hábiles) e Implementación (20 días hábiles).",
      "La fecha ya no se escribe: el sistema muestra la fecha de solicitud y la atención tentativa según la prioridad, sin contar fines de semana ni feriados.",
      "El botón «Tiempo de atención» muestra cuánto demora cada prioridad y qué fecha tendría si envías hoy.",
      "En el detalle del requerimiento ves en qué día hábil va frente a su plazo, o si ya se pasó.",
    ],
  },
  {
    version: "0.53.0",
    titulo: "Usuarios ordenados por rol",
    cambios: [
      "Administración › Usuarios ahora separa a los usuarios por rol: solicitantes, aprobadores, gerencia, PYC, logística y administradores, con su cantidad.",
      "Cada lista muestra lo que importa de ese rol: para qué CECOs pide un solicitante o qué CECOs aprueba un aprobador.",
      "Al hacer clic en un usuario se abre su ficha al costado para cambiar roles, CECOs, acceso y datos en un solo lugar.",
    ],
  },
  {
    version: "0.52.0",
    titulo: "Tablero de requerimientos",
    cambios: [
      "Logística puede ver los requerimientos como tablero: Por aprobar, Abastecimiento, Por despachar, En tránsito, Con diferencias y Atendido.",
      "Cada tarjeta dice por qué el requerimiento aún no llega al final: qué falta hacer, qué se está esperando o qué llegó observado.",
    ],
  },
  {
    version: "0.51.0",
    titulo: "Avisos por correo",
    cambios: [
      "Los aprobadores reciben un correo cuando llega un requerimiento por aprobar, y un recordatorio a las 8:00 si sigue pendiente.",
      "El solicitante recibe un correo cuando su requerimiento se aprueba, se observa (con el motivo) o se rechaza, cuando su pedido sale del almacén y cuando se registra la recepción.",
      "El botón del correo abre directamente la pantalla correspondiente; si no has iniciado sesión, te lleva ahí después de ingresar.",
    ],
  },
  {
    version: "0.50.0",
    titulo: "Personal cesado y eliminación",
    cambios: [
      "El personal que RR.HH. da de baja ahora aparece como Cesado, con su fecha, y tiene su propio filtro.",
      "Un administrador puede eliminar definitivamente a una persona del padrón indicando el motivo. Solo se puede si no tiene cuenta en la plataforma.",
    ],
  },
  {
    version: "0.49.0",
    titulo: "Mantenimiento del personal",
    cambios: [
      "En Administración › Personal ahora se puede agregar y editar personal de GOYDEL y METRIN sin usar el Excel.",
      "Ya no se elimina a nadie: la persona queda inactiva y se muestra desde qué fecha y quién la inactivó.",
      "Solo un administrador puede reactivar a una persona inactiva. Al inactivarla, también pierde el acceso a la plataforma.",
      "Nuevo filtro para ver al personal inactivo.",
    ],
  },
  {
    version: "0.48.0",
    titulo: "Recepción en destino con diferencias",
    cambios: [
      "Al recibir una nota de inventario en destino se indica cuánto llegó de cada producto y si llegó conforme o no conforme, con la nota firmada o el enlace de la conformidad.",
      "Se puede recibir por partes. El requerimiento cuenta como atendido solo lo que llegó conforme.",
      "Lo que llega observado se subsana o se devuelve al almacén central, donde queda en cuarentena.",
      "Nueva pestaña Almacén › Diferencias con lo observado y los retornos; Stock muestra lo que está en cuarentena.",
      "La evidencia del embalaje ahora es un enlace.",
    ],
  },
  {
    version: "0.47.0",
    titulo: "Picking y embalaje antes del envío",
    cambios: [
      "La nota de inventario ahora muestra su preparación: picking, embalaje y envío.",
      "Antes de enviar, se confirma el picking y se registra el embalaje con la cantidad de bultos y una foto o PDF como evidencia.",
      "En Almacén › Notas de inventario se ve en qué paso está cada nota que todavía no sale.",
    ],
  },
  {
    version: "0.46.0",
    titulo: "Órdenes de compra con aprobación",
    cambios: [
      "Cada orden de compra nueva queda «por aprobar»: la aprueba Gerencia u otra persona de Logística, nunca quien la emitió. Si se rechaza, sus productos vuelven a cotización.",
      "Las órdenes por aprobar aparecen en Aprobaciones y llega un aviso al momento.",
      "En el detalle de la orden se ve su aprobación, su pago y su liberación. El pago (anticipo o pagado) se registra con su constancia.",
      "El almacén recibe una orden solo después de que Logística la libera; si todavía no se puede, la pantalla dice por qué.",
    ],
  },
  {
    version: "0.45.0",
    titulo: "Registro de la plataforma",
    cambios: [
      "Administración tiene una nueva pestaña «Registro»: quién hizo qué y cuándo, los ingresos al sistema, los cambios importantes, los errores y los movimientos de inventario.",
      "Si una pantalla falla, ahora muestra un aviso para recargar en vez de quedar en blanco, y el error queda registrado.",
    ],
  },
  {
    version: "0.44.0",
    titulo: "Stock al pedir: con stock o sin stock",
    cambios: [
      "Al armar un requerimiento, cada producto indica si tiene stock en el almacén o si se comprará, sin mostrar cantidades.",
      "Se corrigió «¿No encuentras el producto? Solicita su alta»: la solicitud ahora sí llega a Logística, en Catálogo de productos › Solicitudes de alta.",
    ],
  },
  {
    version: "0.43.0",
    titulo: "Consolidados en dos columnas",
    cambios: [
      "«Listos para orden de compra» y «Consolidados» se ven lado a lado.",
      "Deja el mouse sobre cualquier fila para ver su detalle: de qué requerimientos viene, ofertas, adjudicación y proveedores.",
      "Toca el nombre del producto para abrir el consolidado.",
    ],
  },
  {
    version: "0.42.0",
    titulo: "Consolidados más ordenado",
    cambios: [
      "Compras › Consolidados se divide en dos: primero lo que está listo para orden de compra y debajo los consolidados.",
      "Cada fila muestra solo lo esencial; pasa el mouse sobre el origen, lo cotizado o los productos para ver el detalle.",
    ],
  },
  {
    version: "0.41.3",
    titulo: "Solicitudes de cotización más ordenadas",
    cambios: [
      "En la lista de solicitudes, el código y la empresa van en una sola línea.",
      "Los títulos de cada pantalla ya no se parten en dos líneas: si son largos se recortan y se leen completos al pasar el mouse.",
    ],
  },
  {
    version: "0.41.2",
    titulo: "Solicitudes de cotización por QR y enlace",
    cambios: [
      "La solicitud se hace llegar al proveedor con su PDF con código QR o con el enlace para cotizar en línea.",
      "Se retiró el atajo «Abrir correo» del detalle de la solicitud.",
    ],
  },
  {
    version: "0.41.1",
    titulo: "Textos en una sola línea",
    cambios: [
      "Botones, pestañas, menús y encabezados de tabla ya no se parten en dos líneas.",
      "Los nombres largos en las tablas se recortan con «…»; pasa el mouse para ver el texto completo.",
      "Las solicitudes de cotización se hacen llegar al proveedor con su PDF o su enlace; la opción de WhatsApp se retiró por ahora.",
    ],
  },
  {
    version: "0.41.0",
    titulo: "Tutorial para empezar y pedidos en un solo paso",
    cambios: [
      "Al tocar «Solicitar» ves el resumen de tu pedido: cuánto sale del almacén, cuánto se compra y cuánto va por caja chica, y lo envías a aprobación desde ahí mismo.",
      "Si el pedido sale del presupuesto, escribes la justificación en esa misma ventana.",
      "La primera vez que entras, el sistema te ofrece un recorrido de 2 minutos que te muestra dónde tocar en cada paso.",
      "Quien pide aprende a crear un requerimiento y seguirlo; Logística, el camino de la compra al despacho.",
      "Puedes volver a verlo cuando quieras con «Ver tutorial» en el menú o buscando «tutorial» con Ctrl+K.",
      "En la computadora, todas las pantallas se ven más compactas para que quepa más información.",
    ],
  },
  {
    version: "0.40.0",
    titulo: "El proveedor cotiza con foto y unidad de cada producto",
    cambios: [
      "En el enlace del proveedor cada producto muestra su foto, la cantidad con su unidad (por ejemplo «10 millares») y el precio que se pide («por millar, sin IGV»).",
      "La página se ordena como una cotización: datos de la solicitud, condiciones de la oferta, tabla de productos con subtotales y total.",
      "Con Enter el proveedor pasa al precio del siguiente producto.",
      "Si un producto aún no tiene su unidad definida en el catálogo, el proveedor lo ve como «Unidad por definir».",
    ],
  },
  {
    version: "0.39.0",
    titulo: "Elige cuánto de cada producto va por caja chica",
    cambios: [
      "En tu pedido, cada producto tiene «Por caja chica del proyecto»: indica cuántas unidades comprar así (por ejemplo 2 de 5).",
      "El resto se atiende como siempre, con el stock del almacén o por compra.",
      "En Necesidades, Logística puede dividir una cantidad para darle a cada parte su ruta.",
      "En el detalle del requerimiento verás «Caja chica: n» en esos productos.",
    ],
  },
  {
    version: "0.38.1",
    titulo: "Caja chica aunque haya stock",
    cambios: [
      "Si sugieres la caja chica del proyecto, el pedido llega a Logística aunque haya stock en el almacén.",
      "En Necesidades, Logística ve cuánto hay en el almacén y puede usarlo o autorizar la caja chica.",
    ],
  },
  {
    version: "0.38.0",
    titulo: "Caja chica del proyecto",
    cambios: [
      "Al crear un requerimiento puedes sugerir comprar lo que falte con la caja chica de tu proyecto.",
      "Logística decide en Necesidades y te avisa cuando lo autoriza.",
      "Registras lo que compraste con la foto o el PDF del comprobante; si Logística lo observa, lo corriges ahí mismo.",
      "Lo comprado se queda en tu proyecto y cuenta como atendido cuando Logística verifica el comprobante.",
    ],
  },
  {
    version: "0.37.1",
    titulo: "Reemplazar una cotización ganadora",
    cambios: [
      "Si vuelves a subir la cotización de un proveedor que ya ganó productos, la adjudicación pasa a la nueva.",
      "Descartar una cotización ganadora deja sus productos sin adjudicar para elegir otra oferta, con aviso previo.",
    ],
  },
  {
    version: "0.37.0",
    titulo: "El sistema aprende cómo llama cada proveedor a tus productos",
    cambios: [
      "Al revisar una cotización ves qué línea del documento del proveedor se tomó para cada producto, y puedes elegir otra con «Cambiar».",
      "Lo que confirmas se recuerda: la próxima cotización de ese proveedor reconoce sus nombres y códigos sola («Reconocido»).",
      "La orden de compra muestra también cómo llama el proveedor a cada producto.",
      "En la ficha del proveedor puedes revisar y quitar lo aprendido.",
    ],
  },
  {
    version: "0.36.0",
    titulo: "El proveedor cotiza desde un enlace",
    cambios: [
      "En cada solicitud de cotización puedes generar un enlace para el proveedor: lo abre sin usuario ni contraseña y pone sus precios sobre nuestros mismos productos.",
      "El PDF de la solicitud puede llevar ese enlace con un código QR.",
      "Su cotización llega sola al comparativo, marcada «Por enlace», y te avisa al instante.",
      "El proveedor puede corregir su cotización mientras el enlace esté vigente; tú puedes anularlo o generar otro.",
    ],
  },
  {
    version: "0.35.0",
    titulo: "El stock se descuenta mientras armas tu pedido",
    cambios: [
      "Al agregar un producto a tu requerimiento, el stock que ves baja según la cantidad que llevas.",
      "Si tu pedido ya toma todo el stock, se marca en ámbar: lo que pidas de más se comprará.",
    ],
  },
  {
    version: "0.34.0",
    titulo: "Más rápida en cada pantalla",
    cambios: [
      "La aplicación abre más rápido y, desde la segunda visita, casi no descarga nada.",
      "Cada pantalla se carga al entrar a ella; al pasar el mouse por el menú ya queda lista.",
      "Buscar en Requerimientos y editar las cantidades de un presupuesto responden al instante.",
      "Recibir una orden, aprobar un requerimiento y abrir una solicitud de cotización tardan menos.",
    ],
  },
  {
    version: "0.33.1",
    titulo: "Carga más rápida",
    cambios: [
      "Las fotos de los productos cargan mucho más rápido: se descargan al tamaño en que se ven.",
      "La aplicación muestra el texto apenas abre, sin esperar a que lleguen las fuentes.",
      "La pestaña del navegador muestra el ícono de la empresa.",
    ],
  },
  {
    version: "0.33.0",
    titulo: "Stock a la vista al pedir",
    cambios: [
      "Al armar un requerimiento ves cuánto hay en el almacén de cada producto (lo libre, sin contar lo ya reservado para otros pedidos).",
      "Si pides más de lo que hay, el pedido te avisa cuántas unidades se comprarán.",
    ],
  },
  {
    version: "0.32.1",
    titulo: "Menús que se ven completos",
    cambios: [
      "El menú ⋯ y las listas de búsqueda ya no se cortan al final de una tarjeta o tabla: se muestran completos y, si no hay espacio abajo, se abren hacia arriba.",
    ],
  },
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
      "Una sola solicitud de cotización por proveedor con varios productos, con su PDF lista para hacérsela llegar.",
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

