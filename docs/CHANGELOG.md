# Changelog

## 0.22.0 — 2026-11-02

- **Lote 13 — Precios de venta y plazo de atencion** (DEC-L035, Flyway V17).
- **Precio de venta por empresa** en el Catalogo: lo registran solo PYC
  (boton "Registrar precio") y Logistica (menu del producto); Gerencia y
  Admin lo ven sin editar. Selector de empresa (si hay varias) y filtro
  "Sin precio". Historial y auditoria.
  API `GET /api/maestro/precios`, `PUT /api/maestro/productos/{id}/precio`.
- **Plazo de atencion por SKU** (dias calendario, Logistica): se ve en el
  Catalogo y en el carrito del requerimiento, con la atencion estimada bajo
  la fecha requerida. API `PUT /api/maestro/productos/{id}/plazo`.
- PYC y Gerencia ven el Catalogo de productos.
- Test: `PreciosPlazosTest`.

## 0.21.4 — 2026-10-12

- **Familia › Subfamilia del maestro SKU** (DEC-L034): Catalogo, buscador
  del requerimiento y altas de producto muestran `ACCESORIO › ELECTRICO`.
  Filtro unico agrupado por familia (con opcion "Todo ACCESORIO"): las
  subfamilias con el mismo nombre en distintas familias ya no se mezclan.
- API del catalogo: `familias[]` y `variantes[]` suman `tipoId`/`tipoNombre`
  (la familia del Excel). Sin migracion.

## 0.21.3 — 2026-10-09

- **Nombres de CECO en mayusculas** (DEC-L033) en el sync, la carga Excel y
  el alta manual; Flyway V16 normaliza los existentes.
- PDF: el destino "(ADM) ADMINISTRACION" ya no pierde el codigo.

## 0.21.2 — 2026-10-09

- **CECO repetidos consolidados** (DEC-L032): codigo numerico canonico de
  4 digitos (`0102` = `102`) en el sync, la carga Excel y el alta manual;
  Flyway V15 une y borra los 25 repetidos que ya existian. Quedan 36 CECO
  activos de RR.HH. sin duplicados.

## 0.21.1 — 2026-10-09

- **Todos los CECO de RR.HH.** (DEC-L031): el sync trae el catalogo completo
  (`GET /api/integraciones/cecos` de RRHH-SYSTEM, nuevo) y no solo los CECO
  con personal; en las empresas de RR.HH. desactiva los que ya no existen
  (ADM/OPS de relleno). Probado en local: 61 CECO nuevos, 2 desactivados.
- Requiere desplegar primero RRHH-SYSTEM con el endpoint nuevo.

## 0.21.0 — 2026-10-09

- **Adopcion de la V38.7** (DEC-L030): analisis de la entrega del
  06/10/2026 y plan por lotes 12–19. La entrega queda en
  `docs_base/V38_7_ENTREGA_2026-10-06/` (gitignored).
- **Lote 12 — Roles PYC y GERENCIA** (Flyway V14).
- **Aprobador por CECO**: Administracion -> CECOs -> columna "Aprobador"
  (`PUT /api/admin/cecos/{id}/aprobador`), auditado. Los REQ enviados
  quedan asignados a ese aprobador (`aprobacion.assigned_to`); reasignar
  mueve los pendientes.
- Bandeja y detalle: solo decide el aprobador asignado, la GERENCIA del
  CECO o ADMIN; **nadie aprueba su propio requerimiento**. El aprobador solo
  ve los REQ que le tocan.
- Aviso en vivo dirigido al aprobador asignado. GERENCIA ve Aprobaciones y
  el dashboard de Inicio.
- Seed de prueba: `gerencia@` y `pyc@logistica.test` (con los CECOs de
  TDEMSRL).
- Test: `AprobadorCecoTest`.

## 0.20.0 — 2026-10-02

- **Ambito del solicitante por CECO** (DEC-L029): el formulario de
  requerimiento solo ofrece los CECOs asignados al usuario, de cualquier
  empresa (agrupados por empresa si son varias); la empresa del REQ sale del
  CECO. Sin CECOs asignados se avisa y no se puede crear.
- **Administracion**: asignar varios CECOs a cualquier usuario (tambien de
  RR.HH.) con buscador, `PUT /api/admin/usuarios/{id}/cecos`; aviso
  "Sin CECO" en solicitantes sin ambito; el filtro por empresa incluye a
  quien tiene CECOs de esa empresa. "Editar datos" ya no cambia el CECO.
- La carga Excel de personal agrega el CECO de planilla sin quitar los
  asignados a mano. El seed de prueba asigna al solicitante los CECOs de
  TDEMSRL.
- Test: `AmbitoCecoTest`.

## 0.19.0 — 2026-09-30

- **Personal de GOYDEL y METRIN** (DEC-L028): Administracion → "Carga de
  personal" con plantilla Excel (trae al personal actual), validacion sin
  escribir y aplicacion todo o nada: altas, correcciones y ceses en un
  mismo archivo; crea CECOs nuevos. API `/api/admin/personal/plantilla`,
  `/validar`, `/confirmar`.
- **Editar datos** de usuarios manuales (nombre, correo, DNI, cargo, CECO)
  y cargo en el alta manual (`PUT /api/admin/usuarios/{id}`).
- **Sidebar por secciones**: Inicio · Solicitudes (Requerimientos,
  Aprobaciones) · Abastecimiento (Compras, Proveedores) · Inventario
  (Almacen, Stock, Catalogo) · Sistema (Administracion). Cada rol ve solo
  las secciones con modulos suyos; con el riel plegado, una linea fina
  separa las secciones.
- Test: `PersonalManualTest`.

## 0.18.0 — 2026-09-30

- **Observar requerimiento** (DEC-L027, RF-006/048): el aprobador lo
  devuelve con motivo obligatorio; el solicitante ve el motivo, lo corrige y
  lo reenvia (nueva aprobacion). Estado `OBSERVADO` en badges, dashboard,
  aviso en vivo y Tracking.
- **Programar entrega** (RF-068): Logistica fija o cambia la fecha de entrega
  en el detalle del REQ, sin tocar la fecha requerida. `PUT
  /api/requerimientos/{id}/programacion`. El detalle muestra ambas fechas.
- **Confirmaciones corporativas** (RF-047/064): ventana propia en todas las
  acciones sensibles; anular OC y cerrar RFQ piden motivo (tambien exigido
  por la API: cuerpo `{ "motivo": ... }`).
- Auditoria de acciones criticas en `log_cambios`; el Tracking muestra la
  programacion de entrega y el motivo de anulacion de la OC.
- Flyway `V13__observar_programar_entrega.sql`.
- Test: `ObservarProgramarTest`.

## 0.17.0 — 2026-09-28

- **Caja Chica** (DEC-L026, RF-023..027, 029, 051, 056): "Gestionar compra"
  ofrece las dos rutas (cotizar a proveedor / Caja chica) y sugiere Caja
  Chica segun la regla (bajo monto, mismo dia, falla de proveedor). La
  compra entra al almacen reservada para el REQ y sigue el despacho normal.
  Comprobante duplicado bloqueado.
- **Compras en pestañas**: Pendientes de compra (con marca "Hoy" para
  urgentes del dia), RFQ en curso y Caja chica (regularizar / observar y
  regla vigente, editable por ADMIN).
- Tracking: la compra por Caja Chica aparece en el historial.
- Flyway `V12__caja_chica.sql`; API `/api/caja-chica`.
- Test: `CajaChicaTest`.

## 0.16.0 — 2026-09-28

- **Alta de productos** (DEC-L025): Catalogo → "Nuevo producto" (Logistica/
  Admin) con codigo SKU generado por familia y variantes por talla/color/
  modelo; inactivar desde el menu "⋯" de cada producto.
- **Solicitudes de alta** (RF-004): "¿No encuentras el producto?" en el
  formulario del requerimiento; bandeja en Catalogo → "Solicitudes de alta"
  para aprobar (crea el SKU) o rechazar con motivo.
- API `/api/maestro` (clasificacion, productos, variantes, solicitudes).
- Test: `MaestroProductosTest`.

## 0.15.0 — 2026-09-28

- **Alcance v1.0 definido** (DEC-L023).
- **Identidad desde RR.HH.** (DEC-L024, RF-040/041): sincronizacion de solo
  lectura del personal activo (usuarios, CECOs, desactivacion por cese),
  manual y diaria. Variables `RRHH_API_URL`, `RRHH_SYNC_SERVICE_TOKEN`,
  `RRHH_SYNC_CRON`.
- **Administracion** (`/admin`, solo ADMIN): usuarios (roles, dar acceso /
  restablecer contrasena temporal, activar manuales, alta manual para
  METRIN/GOYDEL), CECOs y estado de la sincronizacion. Todo en
  `GET /api/admin` (una sola respuesta).
- **Cambio de contrasena** (`/cambiar-password`), obligatorio tras una
  temporal. Flyway `V11__identidad_rrhh_admin.sql`.
- **Seguridad**: roles y estado vigentes en cada request (`AccesoVigente`),
  no los del login.
- Test: `IdentidadRrhhTest` (RRHH falso por HTTP: altas, conversion de
  MANUAL, CECOs, roles, contrasena temporal y cambio, alta manual acotada,
  desactivacion por cese y proteccion ante respuesta vacia).

## 0.14.0 — 2026-09-28

- **Lote 7 (parte 2)** (DEC-L022).
- **PDFs corporativos** (RF-038/082/083): Requerimiento, Solicitud de
  Cotizacion, Orden de Compra, Nota de Inventario y Tracking, con logo,
  version de plantilla, usuario y fecha de generacion y numeracion de
  paginas. Endpoints `GET .../pdf` junto a cada recurso
  (`/api/requerimientos/{id}/pdf`, `/api/requerimientos/{id}/tracking/pdf`,
  `/api/compras/rfq/{id}/pdf`, `/api/compras/ordenes/{id}/pdf`,
  `/api/almacen/notas/{id}/pdf`). Boton "PDF" en cada pantalla de detalle y
  en la tarjeta de Tracking.
- **Carga masiva de ingresos** (RF-031): pantalla `/almacen/carga-masiva`
  con plantilla Excel de lo pendiente, validacion fila por fila sin afectar
  stock y confirmacion todo-o-nada (`/api/almacen/carga-masiva/plantilla`,
  `/validar`, `/confirmar`).
- `NotaInventarioDto` suma almacen de origen, codigo de CECO y solicitante;
  `RfqDetalleDto`/`OrdenDetalleDto` suman RUC del proveedor y empresa.
- Fix: rutas `/almacen/*` agregadas a `SpaController` y `SecurityConfig`.
- Test: `DocumentosYCargaMasivaTest` (plantilla, archivo con errores sin
  efecto, carga aplicada con guia, reintento rechazado, los cinco PDFs y
  sus permisos, salto de pagina y caracteres fuera de WinAnsi).

## 0.13.0 — 2026-09-25

- **Paleta azul corporativa** (DEC-L021): grises frios y azul marino/acero en
  toda la app (tokens de `globals.css`).
- **Login rediseñado**: solo "ERP LOGÍSTICO" y las tres empresas, con una
  escena animada del rubro eléctrico (operario que lleva una caja del almacén
  a la subestación bajo el tendido de alta tensión). SVG en tiempo real, sin
  dependencias nuevas.

## 0.12.0 — 2026-09-25

- **Dashboard en Inicio** (DEC-L020): filtros de periodo (30/90/365 dias) y
  empresa; indicadores de abiertos, tiempo de aprobacion, ciclo completo,
  % y tiempo de respuesta de proveedores y ahorro por comparar
  cotizaciones; trabajo pendiente por etapa (con enlace a cada modulo);
  requerimientos creados por semana; desempeno por proveedor y ultimos
  comparativos. `GET /api/dashboard` en una sola consulta SQL. Flyway
  `V10__cotizacion_fecha_recepcion.sql`.
- **Requerimientos por empresa**: tres columnas verticales (TDEM, GOYDEL,
  METRIN) con tarjetas; `RequerimientoDto.empresaCode`.
- **Sidebar colapsable**: riel de iconos que se despliega al pasar el mouse
  (recorte con `clip-path`, sin recalcular el layout) y empuja el contenido.
  Todas las pantallas usan todo el ancho con margen minimo.
- **Rendimiento**: "Pendientes de compra" en una sola consulta (antes
  1 + 3 por item); `hibernate.default_batch_fetch_size: 50`; capa de carga
  y animacion de entrada mas cortas.
- Tests: `AlmacenFlujoTest` verifica el dashboard (comparativo 70 vs 50,
  filtro de empresa, permisos); `AtencionAutomaticaTest` verifica la lista de
  pendientes de compra.

## 0.11.0 — 2026-09-25

- **Lote 7 (parte 1): Tracking del requerimiento** (DEC-L019, RF-039/081/083).
  `GET /api/requerimientos/{id}/tracking` devuelve cabecera, avance
  (unidades entregadas en destino / solicitadas), los 5 hitos
  (Emitido, Aprobado, Recepcionado, Atencion parcial, Atencion completa)
  calculados desde los datos, una fila por NI/envio con sus adjuntos y el
  historial reconstruido (aprobacion, reserva de stock, RFQ, OC,
  recepciones, NI, envio, entrega). Mismo permiso que el detalle del REQ.
- `GET /api/requerimientos/{id}/tracking/excel`: Excel con las 17 columnas
  del prototipo V38 (una fila por NI). Nueva dependencia Apache POI
  (`poi-ooxml` 5.5.1).
- Frontend: seccion "Tracking" en el detalle del requerimiento (barra de
  avance, hitos, entregas con enlaces a guia/ticket/conformidad, historial
  desplegable, boton Excel). `http.descargar` para bajar archivos con la
  cookie de sesion.
- **Rediseno visual** (skills `redesign-existing-projects` + `minimalist-ui`):
  tipografia Geist / Geist Mono con titulos en serif Newsreader (Google
  Fonts); paleta monocroma calida (la escala `slate` se redefine en
  `globals.css`, el azul TDEM queda como acento de enlaces/foco); estados en
  pasteles apagados; cards planas sin sombra y una sola sombra difusa
  (`shadow-flotante`) para menus, toast y modal; botones color carbon con
  `active:scale-[0.98]`; sidebar claro con item activo en superficie blanca;
  Login en dos columnas sin degradado; Inicio como indice de todos los
  modulos visibles para el rol; cifras tabulares y hover de fila en tablas;
  clase `.rotulo` para etiquetas de dato; entrada suave de cada pantalla
  (respeta `prefers-reduced-motion`); enlace "Saltar al contenido"; favicon y
  meta description en `index.html`.
- `RequerimientoService.exigirAcceso` extraido para reutilizar la regla de
  visibilidad del REQ.
- Test: `AlmacenFlujoTest` verifica el Tracking a mitad (9/12, 75%) y al
  final (5 hitos, 2 entregas, historial, Excel de 17 columnas, acceso
  denegado a otro solicitante).

## 0.10.0 — 2026-09-24

- **Atencion automatica al aprobar** (DEC-L018): al aprobar un requerimiento
  se aparta solo el stock disponible y se crea sola la RFQ del faltante al
  proveedor habitual de cada producto (una por proveedor). Se elimina el paso
  manual de verificacion de stock (endpoints `/api/stock/pendientes`,
  `/api/stock/requerimientos/{id}`, `/decidir` y su seccion en el detalle).
- Flyway `V9__proveedor_habitual_producto.sql` (`producto.preferred_supplier_id`).
  Se completa solo con la primera OC del producto; editable en el Catalogo
  (`GET /api/compras/proveedores-habituales`,
  `PUT /api/compras/productos/{id}/proveedor-habitual`).
- Compras: nueva seccion "RFQ en curso" (`GET /api/compras/rfqs-abiertas`)
  ademas de "Pendientes de compra".
- Stock: la pagina muestra saldos por SKU (fisico, reservado, disponible)
  con filtro por empresa y busqueda (`GET /api/stock/saldos`).
- UI: se quitaron los textos de ayuda largos en todas las pantallas.
- Test: `AtencionAutomaticaTest` (stock parcial + RFQ al habitual, sin
  habitual, stock suficiente, dos proveedores, habitual fijado por la OC).

## 0.9.0 — 2026-09-24

- **Lote 6: Almacen Central** (DEC-L017). Flyway `V8__almacen_recepcion_despacho.sql`:
  `recepcion`, `recepcion_item`, `incidencia_recepcion`, `guia_remision`,
  `salida`, `salida_item`, `nota_inventario`, `envio`, y
  `reserva_stock.origin/receipt_item_id`. Backend `modules/almacen/`
  (`AlmacenController` en `/api/almacen`, `RecepcionService`,
  `DespachoService`, `AtencionCalculator`). Frontend: menu "Almacen" con
  pestanas Por recibir / Por despachar / Notas de inventario, paginas de
  recepcion de OC (cantidad aceptada, condicion, incidencias, guia),
  despacho (emite NI) y detalle de NI (envio con costo neto/IGV/total,
  adjuntos guia/ticket/conformidad, confirmar recepcion en destino). El
  detalle de la OC enlaza a su recepcion.
- **Fix**: verificar stock ya no marca el REQ como atencion parcial/completa
  (RF-079: solo entregas confirmadas en destino).
- **Fix**: el pendiente de compra no se descuadra al recibir ni al despachar.
- **Fix**: el aviso en vivo al solicitante decia "aprobado" para cualquier
  estado no rechazado; ahora distingue entrega parcial/completa.
- Una OC con recepciones ya no se puede anular (backend y UI).
- Test: `AlmacenFlujoTest` (flujo completo sobre Postgres real: recepcion
  parcial/total, exceso rechazado, incidencia, 2 NI con FIFO de reservas,
  envio, confirmacion y atencion parcial -> completa, regresion del
  pendiente de compra, permisos).

## 0.8.0 — 2026-09-24

- **LOGISTICA pasa a la BD de RRHH-SYSTEM, en su propio esquema**
  (DEC-L016). Esquema `logistica` + rol `logistica_app` (script manual
  `db_design_reference/setup/rrhh_db_esquema_logistica.sql`). Migraciones
  V1..V7 reescritas con el placeholder `${schema}` en vez de `public.`; V1
  sin las funciones/policies de Supabase Auth; V2 pasa a no-op (antes
  borraba las policies de TODO `public`, lo que habria dejado expuestos los
  datos de RRHH); nuevo callback `beforeMigrate.sql` que aborta si el
  esquema fuera `public`. `application.yml`: `DB_SCHEMA` (Flyway,
  Hibernate `default_schema`, `search_path` de Hikari), pool de 5
  conexiones, sin `baseline-on-migrate`.
- `TestDataSeeder` ahora **crea** los 4 usuarios de prueba (antes solo les
  ponia contrasena, porque existian solo en la BD vieja).
- `scripts/seed_catalogo_sku.py`: genera con prefijo `logistica.` y escribe
  en `db_design_reference/seed/` (apuntaba a la carpeta `supabase/`, que ya
  no existe). Salida identica al seed versionado.
- **Storage S3-compatible** (DEC-L015): interfaz `storage/StorageClient` con
  implementacion unica `S3StorageClient` (AWS SDK v2 modulo `s3`, cliente
  HTTP `url-connection`). Hoy apunta a Supabase Storage del proyecto de RRHH
  (bucket `logistica-productos`, llaves S3 de Storage); sirve tambien para
  Cloudflare R2 cambiando solo variables. Se elimina `SupabaseStorageClient`
  (REST + service key). Variables `STORAGE_*` (tambien en `render.yaml`).
  Los objetos se suben con `Cache-Control: public, max-age=31536000, immutable`.
- **Carga masiva de fotos**: `scripts/cargar_imagenes.py` sube una carpeta
  de archivos nombrados por SKU base o codigo de variante (en paralelo,
  opcional `--optimizar` a WebP max 1600px) y genera
  `db_design_reference/seed/0002_imagenes_producto.sql` idempotente
  (DOCUMENTO + PRODUCTO_IMAGEN, una principal activa por producto).
- **Visor de fotos**: clic en cualquier miniatura de producto (Catalogo,
  buscador y pedido de Requerimientos) la amplia con el nombre del
  producto y su codigo/familia. Cierra con Esc, la X o clic fuera
  (`Miniatura` en `components/BuscadorProductos.tsx`).
- Tests nuevos: `MigracionesFlywayTest` (todas las migraciones sobre
  Postgres 17 real embebido que imita la BD de RRHH: 589 productos, RLS y
  policies de `public` intactas, `logistica_app` sin acceso a tablas de
  RRHH, guardia contra `public`), `LogisticaApplicationTests` (arranca la
  app completa contra esa BD: Hibernate `validate` + usuarios de prueba) y
  `S3StorageClientTest`. `./mvnw test` ya no necesita una BD externa.

## 0.7.3 — 2026-09-23 (5)

- **OCR: sugerencia de costo por item + presentacion ordenada para
  Logistica.** El usuario probo con una cotizacion real y pidio que la
  informacion mostrada sea la relevante para Logistica (no un volcado
  tecnico) y que el codigo generalice bien entre proveedores con formatos
  de tabla distintos. `POST /api/compras/rfq/{id}/cotizaciones/ocr`
  (antes generico, ahora atado a la RFQ): `CotizacionOcrParser` busca en
  el texto los codigos de producto que YA se enviaron en esa RFQ (mas
  confiable entre proveedores que tratar de "entender" una tabla con
  formato libre) y propone un costo unitario por item (el menor importe
  encontrado cerca del codigo, acotado para no leer montos de la fila
  siguiente). Tambien se agrega deteccion de razon social del proveedor
  (busca sufijos societarios peruanos SAC/SRL/EIRL/SA). Frontend: el
  resultado del OCR ahora es una ficha ordenada (Proveedor/RUC/Fecha/
  Total/Plazo/cantidad de costos sugeridos) en vez de una oracion corrida;
  el texto crudo queda colapsado al final, marcado como "referencia
  tecnica, no hace falta leerlo"; los inputs de costo unitario que se
  autocompletaron muestran un icono para indicar que vienen de una
  sugerencia y hay que revisarlos.
- Bug real corregido de paso: `parsearMonto` interpretaba mal un monto sin
  centavos con coma de miles (ej. "1,420" se leia como 1.42 en vez de
  1420) — ahora solo trata la coma como separador decimal si quedan 1-2
  digitos despues.
- Cubierto por tests: `CotizacionOcrParserTest` (proveedor detectado,
  costo por item sin cruzar a la fila del siguiente item, sin
  falsos positivos si el codigo no aparece).

## 0.7.2 — 2026-09-23 (4)

- **Fix real encontrado probando el OCR con una cotizacion real** (PDF de
  un proveedor real, con logo/imagenes incrustadas). `OcrExtractor` siempre
  renderizaba la pagina del PDF a imagen para pasarla a Tesseract, y ese
  render fallaba cuando la pagina traia una imagen incrustada que ImageIO
  no puede decodificar (tipico con JPEG en espacio de color CMYK, comun en
  logos/fotos de producto). Ahora primero intenta el texto embebido del
  PDF (`PDFTextStripper`) — la mayoria de las cotizaciones ya traen texto
  real, no son un escaneo — y solo renderiza a imagen (con cada pagina
  protegida en su propio try/catch) si no hay texto embebido. De paso se
  encontro y corrigio otro bug: el regex de "total" tomaba el primer
  "Sub-Total" en vez del total final; ahora excluye "Sub-Total" y toma la
  ultima coincidencia. Se agrega soporte de fecha con mes en texto
  ("19-Ago-2026", formato comun en documentos peruanos). Regresion cubierta
  por `CotizacionOcrParserTest` con el texto real de la cotizacion que
  disparo el bug.

## 0.7.1 — 2026-09-23 (3)

- **Editar un requerimiento en BORRADOR** (gap encontrado probando el Lote 1
  en vivo). No existia forma de corregir CECO/prioridad/proposito/
  justificacion/items una vez creado el requerimiento. Backend:
  `RequerimientoService.actualizar()` + `PUT /api/requerimientos/{id}`
  (mismo permiso que enviar: dueno o LOGISTICA/ADMIN, solo mientras el
  status siga BORRADOR); reemplaza todos los items de una — seguro porque
  en BORRADOR todavia no puede existir ninguna ReservaStock/RfqItem/
  OrdenItem apuntando a esos `request_item_id`. `RequerimientoDto` ahora
  expone `cecoId` (antes solo `cecoNombre`, no alcanzaba para precargar el
  select). Frontend: `RequerimientosNuevo.tsx` pasa a ser el mismo
  formulario para crear y editar (via `/requerimientos/:id/editar`,
  mismo patron que `ProveedorForm.tsx`), boton "Editar" en
  `RequerimientoDetalle.tsx` cuando aplica.
- **OCR gratuito y local para cotizaciones** (Tesseract via Tess4j +
  PDFBox, sin costo ni API externa — se evaluo Claude/GPT vision y se
  descarto por presupuesto: el usuario va a integrar varias APIs y prefirio
  no sumar otro costo recurrente). Backend: `storage/OcrExtractor.java`
  (extrae texto de imagen/PDF, tessdata en espanol embebido como recurso),
  `modules/compras/CotizacionOcrParser.java` (regex sobre el texto para
  proponer RUC/fecha/moneda/total/plazo — nunca se guarda ciego, el usuario
  siempre revisa), endpoint `POST /api/compras/cotizaciones/ocr`. Prueba de
  humo (`OcrExtractorTest`, sin BD/Spring) verifica que las librerias
  nativas cargan y el pipeline completo reconoce una cotizacion sintetica.
  Frontend: boton "Extraer datos (OCR)" en `ComprasRfqDetalle.tsx` junto al
  adjunto de la cotizacion, precarga moneda/fecha/plazo detectados y
  muestra el texto crudo como referencia para completar el costo por item
  a mano (el diseno de referencia nunca extrajo precios por item
  automaticamente tampoco). Se agrega tambien el campo "Fecha de emision"
  al formulario, que faltaba desde el Lote 5.

## 0.7.0 — 2026-09-23 (2)

- **Lote 5: Proveedores + RFQ + Cotizaciones + Orden de Compra** (DEC-L013).
  Cierra el flujo que quedaba abierto desde el Lote 3: un requerimiento que
  termina `EN_ATENCION`/`ATENCION_PARCIAL` ahora puede seguir hacia una
  compra. Backend: migracion Flyway `V7__proveedores_rfq_compras.sql`
  (`proveedor`, `rfq`, `rfq_item`, `cotizacion`, `cotizacion_item`,
  `orden_abastecimiento`, `orden_item`), modulo `modules/proveedores/`
  (CRUD simple, sin evaluacion/scoring) y modulo `modules/compras/`
  (`ComprasService`/`ComprasController`): armar una RFQ desde items
  pendientes de un requerimiento, registrar las cotizaciones que Logistica
  recibe fuera del sistema (con PDF/foto opcional via
  `SupabaseStorageClient`), seleccionar una para emitir la Orden de Compra
  (descarta automaticamente las demas cotizaciones de esa RFQ y la cierra),
  y anular una orden. La "cantidad pendiente de compra" por item
  (solicitado - reservado - en RFQ activa - ya ordenado) se calcula en Java,
  nunca una columna, mismo patron que `StockService.saldoDisponible`.
  Endpoints nuevos bajo `/api/proveedores` y `/api/compras/*`. Frontend:
  paginas `Proveedores`/`ProveedorForm`, `Compras` (bandeja),
  `ComprasRfqNuevo`, `ComprasRfqDetalle` (registrar cotizacion + seleccionar/
  descartar), `ComprasOrdenes`/`ComprasOrdenDetalle`, bloque de solo lectura
  "Compras" en `RequerimientoDetalle.tsx`, badges `RfqEstadoBadge`/
  `CotizacionEstadoBadge`/`OrdenEstadoBadge`, links "Compras"/"Proveedores"
  en el nav (LOGISTICA/ADMIN).
- Recortes deliberados respecto al diseno de referencia (detalle completo en
  DEC-L013): sin consolidacion cruzada entre requerimientos (una RFQ nace de
  UN solo requerimiento), sin convenios ni catalogo de precios por
  proveedor (solo ruta RFQ), sin `consulta_proveedor`/`comunicacion_proveedor`
  (no hay SMTP/WhatsApp integrado), sin ruta Caja Chica (lote aparte, PD-05
  sigue sin validar), sin documentos de compra (`guia_remision`/
  `factura_proveedor`, dependen de Almacen/Fase 2). `requerimiento.status`
  no cambia al emitir una OC.
- **6 bugs reales encontrados y corregidos en revision de codigo** antes de
  cerrar el lote:
  1. `SpaController`/`SecurityConfig`: las rutas nuevas del frontend
     (`/proveedores`, `/compras/*`, y `/catalogo` del Lote 4 que tampoco
     estaba) faltaban en la lista de forward del SPA y en el `permitAll` de
     Security — en el despliegue empaquetado, refrescar o navegar directo a
     esas URLs devolvia 404/401 en vez de servir el SPA.
  2. `ComprasMapper.toDto(OrdenItem)`: el detalle de la Orden de Compra
     mostraba el SKU/variante del `requerimiento_item` original en vez de
     lo efectivamente cotizado y ordenado (`orden_item.product_id`/
     `variant_id`), relevante cuando la cotizacion ganadora ofrece una
     marca/modelo distinto.
  3. `seleccionarCotizacion`: sin proteccion contra doble clic/reintento —
     se agrego `unique(quotation_id)` en `orden_abastecimiento` para que un
     segundo intento concurrente choque contra el indice unico (409) en vez
     de crear una segunda Orden de Compra para la misma cotizacion.
  4. `registrarCotizacion`: no validaba items de cotizacion duplicados
     (mismo `rfqItemId` dos veces en un mismo request), lo que inflaba el
     total.
  5. Los totales de cotizacion se calculaban con `BigDecimal` sin redondear
     explicitamente (dependian del truncamiento implicito de Postgres al
     insertar) — ahora cada linea se redondea a 2 decimales en Java antes
     de sumar.
  6. `RequerimientoDetalle.tsx`: el `useEffect` que carga el detalle de
     compra no se cancelaba al cambiar de requerimiento, permitiendo que
     una respuesta tardia de un requerimiento anterior pisara el estado del
     que se esta viendo ahora.
- No se pudo verificar end-to-end en navegador durante esta sesion (sin
  credenciales de BD/Supabase disponibles) — backend (`mvn compile`) y
  frontend (`tsc --noEmit` + `vite build`) verificados en verde; la prueba
  real la hace el usuario en su entorno, mismo criterio que el Lote 4.

## 0.6.0 — 2026-09-23

- **Lote 4: Catalogo de Productos con imagenes** (DEC-L012). Backend:
  `entity/Documento.java` (repositorio logico de archivos: la BD nunca guarda
  el binario, solo la URL en storage externo), `entity/FamiliaProducto.java`,
  `entity/ProductoImagen.java` (`variant_id` null = aplica a todas las
  variantes del SKU base; solo una imagen principal activa por producto,
  indice parcial ya existente desde `V1__baseline_lote1.sql`), migracion
  Flyway `V6__imagen_producto_tipo_documento.sql` (habilita `IMAGEN_PRODUCTO`
  como `documento.document_type` valido). `storage/SupabaseStorageClient.java`
  sube el binario al Storage de Supabase via su API REST (sin SDK Java
  oficial) y devuelve la URL publica. Endpoint nuevo
  `POST /api/requerimientos/productos/{productId}/imagen` (LOGISTICA/ADMIN).
  `GET /api/requerimientos/catalogo` ahora trae tambien `familias` y la foto
  principal por producto (`VarianteCatalogoDto.imagenUrl`). Frontend: pagina
  nueva `pages/CatalogoProductos.tsx` (buscar/filtrar por familia, subir o
  cambiar la foto), miniaturas y subida integradas tambien en
  `BuscadorProductos` (selector de producto del formulario de
  requerimientos), `PrioridadBadge.tsx`.
- **Redisenio de UI a Tailwind v4** — se retira el CSS propio
  (`globals.css`, -365 lineas de clases como `page-head`/`banner`/`tbl`/
  `form-grid`) y se migra a Tailwind v4 + un design system propio en
  `components/ui/` (`Alert`, `Badge`, `Button`, `Card`, `EmptyState`,
  `Field`, `PageHeader`, `Skeleton`). Todas las paginas existentes (Login,
  Inicio, Requerimientos, RequerimientoDetalle, Aprobaciones, Stock,
  StockAjustes) migradas sin cambios de comportamiento (verificado por
  revision de codigo: mismas condiciones de guarda, mismos textos, mismos
  estados de carga/error). `RequerimientosNuevo.tsx` pasa de "filas de items
  en blanco" a un flujo de carrito (buscar producto en `BuscadorProductos` →
  agregar → ajustar cantidad/observacion en el panel lateral "Tu pedido").
- **3 bugs reales encontrados y corregidos en revision de codigo** antes de
  cerrar el lote (sin poder probar en vivo por falta de credenciales de BD/
  Supabase en esa sesion — la prueba en navegador la hizo el usuario
  despues, exitosa):
  1. `components/ui/Button.tsx`: el spread de props (`{...buttonRest}`)
     pisaba el `disabled` calculado a partir de `loading`, desactivando el
     guard anti doble-submit en botones que reciben `disabled` explicito
     ademas de `loading` (ej. "Solicitar" en `RequerimientosNuevo.tsx`).
  2. `RequerimientoService.subirImagenProducto`: la extension del archivo en
     el path de Supabase Storage salia del `filename` enviado por el
     cliente sin sanitizar (riesgo de path traversal). Ahora sale de una
     whitelist de content-types (jpg/png/webp/gif).
  3. La validacion de imagen aceptaba `image/svg+xml`, que puede llevar
     `<script>` embebido (XSS si la URL se abre directo en el navegador) —
     excluido de la whitelist anterior.
  4. (menor) `GlobalExceptionHandler`: el mensaje de archivo muy grande
     hardcodeaba "8MB" en vez de leerlo de `application.yml`; se agrego
     tambien un handler generico de `DataIntegrityViolationException` (409)
     para no exponer un 500 crudo si dos subidas concurrentes chocan contra
     el indice unico parcial de `producto_imagen`.

## 0.5.0 — 2026-09-21

- **Lote 3: modulo Verificacion de Stock** (DEC-L011). Backend:
  `entity/Almacen.java`, `entity/MovimientoInventario.java`,
  `entity/ReservaStock.java`, migracion Flyway `V5__stock.sql` (kardex real,
  sin RLS; un almacen `CENTRAL` sembrado por empresa), modulo
  `modules/stock/` (service/controller/dto). El saldo disponible
  (kardex - reservas activas) se calcula en Java (`StockService`), no con
  una funcion SQL. Endpoints nuevos: `GET /api/stock/pendientes`,
  `GET /api/stock/requerimientos/{id}`,
  `POST /api/stock/requerimientos/{id}/decidir`, `POST /api/stock/ajustes`
  (carga/correccion manual de stock, unica via de entrada mientras Almacen
  no exista — Fase 2), `GET /api/stock/empresas`. Frontend:
  `pages/Stock.tsx` (bandeja), `pages/StockAjustes.tsx` (registrar ajuste),
  bloque "Verificacion de stock" en `RequerimientoDetalle.tsx` (saldo por
  item + cantidad a reservar editable), link "Stock" en el nav (solo
  LOGISTICA/ADMIN).
- Reutiliza `RealtimeNotifier.decisionTomada()` (ya generico) para avisar al
  solicitante cuando LOGISTICA confirma la verificacion — no se agrego canal
  WebSocket nuevo.
- Requerimiento avanza a `ATENCION_COMPLETA` (todo cubierto con stock),
  `ATENCION_PARCIAL` (cubierto parcialmente) o `EN_ATENCION` (nada cubierto,
  queda pendiente de Compras) segun lo reservado vs. lo solicitado.

## 0.4.0 — 2026-09-18 (3)

- **Notificaciones en tiempo real (WebSockets/STOMP)** (DEC-L010). Backend:
  `spring-boot-starter-websocket`, `config/WebSocketConfig.java`,
  `security/WebSocketAuthHandshakeInterceptor.java`,
  `security/StompChannelInterceptor.java`, `security/StompPrincipal.java`,
  `realtime/RealtimeNotifier.java`. `RequerimientoService.enviar()` y
  `AprobacionService.decidir()` publican eventos (`/topic/aprobaciones` y
  `/user/queue/mis-requerimientos`). Frontend:
  `realtime/RealtimeProvider.tsx` (`@stomp/stompjs`, WebSocket nativo),
  `components/Toast.tsx`; `Aprobaciones.tsx` antepone el item nuevo con
  badge "nuevo"; `RequerimientoDetalle.tsx` refleja la decision al instante.
- **2 bugs reales encontrados y corregidos**: (1) `.withSockJS()` +
  Tomcat 11.0.24/Spring Framework 7.0.9 produce un `StackOverflowError`
  recursivo — se uso WebSocket nativo sin fallback. (2) `SpaController`
  (regex catch-all para servir el SPA en produccion) matcheaba tambien
  `/ws` y su propio destino de forward (`/index.html`), causando forward
  recursivo infinito — reemplazado por una lista explicita de rutas.
- Verificado end-to-end con dos sesiones de navegador en paralelo
  (Playwright): el APROBADOR ve aparecer un requerimiento nuevo en su
  bandeja sin recargar; el SOLICITANTE ve cambiar el estado de su
  requerimiento al instante cuando se lo deciden, sin recargar.

## 0.3.0 — 2026-09-18 (2)

- **Lote 2: modulo Aprobaciones** (DEC-L009). Backend:
  `entity/Aprobacion.java`, `repository/AprobacionRepository.java`,
  `modules/aprobaciones/` (service/controller/dto), migracion Flyway
  `V4__aprobaciones.sql`. `RequerimientoService.enviar()` ahora crea la fila
  `aprobacion` PENDIENTE; `RequerimientoService.obtener()` amplia la
  autorizacion para que un APROBADOR vea el detalle de un requerimiento ya
  enviado (antes no veia nada). Frontend: `pages/Aprobaciones.tsx` (bandeja),
  bloque de decision Aprobar/Rechazar en `RequerimientoDetalle.tsx`, link
  "Aprobaciones" en el nav (solo APROBADOR/ADMIN).
- Endpoints nuevos: `GET /api/aprobaciones`,
  `POST /api/aprobaciones/{id}/decidir`.
- Verificado end-to-end (Playwright): 2 requerimientos enviados, uno
  aprobado (-> `VERIFICACION_STOCK`) y otro rechazado (-> `RECHAZADO`,
  validando que el motivo es obligatorio), bandeja de aprobaciones vacia al
  terminar.

## 0.2.0 — 2026-09-18

- **Migracion de stack completa: Next.js+Supabase -> Spring Boot + React**
  (DEC-L008). Elimina `app/`, `src/` (Next.js), `node_modules/`, `.next/`;
  renombra `supabase/` -> `db_design_reference/`.
- **Backend nuevo** (`backend/`, Java 21 + Spring Boot 4.1 + Maven):
  entidades JPA del Lote 1 (Empresa, Ceco, Rol, Usuario, UsuarioRol,
  UnidadMedida, Producto, ProductoVariante, Requerimiento,
  RequerimientoItem), Spring Security + JWT en cookie httpOnly
  (`JwtService`, `JwtAuthFilter`, `JsonAuthEntryPoint`), modulo
  `auth` (login/logout/me) y `requerimientos` (listar/obtener/catalogo/
  crear/enviar) con paridad completa al stack anterior. Flyway
  (`V1__baseline_lote1.sql` baseline, `V2__disable_rls.sql`,
  `V3__usuario_password_hash.sql`).
- **Frontend nuevo** (`frontend/`, React 19 + TypeScript + Vite +
  react-router-dom): mismas paginas que la version Next.js (Login, Inicio,
  Requerimientos, RequerimientosNuevo con el combobox de busqueda sobre los
  725 SKU, RequerimientoDetalle), mismo `globals.css`.
- `Dockerfile` + `render.yaml` para desplegar como un unico Web Service
  (Spring Boot sirve el build de React empaquetado).
- **2 bugs encontrados y corregidos durante la migracion** (no relacionados
  con el codigo anterior, propios del nuevo stack):
  1. Conexion JDBC directa a Supabase (`db.<ref>.supabase.co:5432`) falla
     por timeout — el host resuelve solo IPv6 en esta red. Se uso el
     connection pooler de Supabase en modo sesion (puerto 5432).
  2. `LazyInitializationException` en `AuthService`/`RequerimientoService`
     al acceder a asociaciones `FetchType.LAZY` (`Usuario.empresa`,
     `Requerimiento.ceco/requester`) fuera de una transaccion
     (`spring.jpa.open-in-view=false`). Corregido con `@Transactional`
     (`readOnly = true` a nivel de clase, override en `crear()`/`enviar()`).
- Verificado end-to-end con el mismo criterio que el Lote 1 original
  (Playwright headless): login SOLICITANTE -> crear requerimiento con SKU
  real -> enviar a aprobacion -> logout -> login LOGISTICA -> verlo en
  bandeja, mas el caso de permiso cruzado (DEC-L007) reimplementado en Java.

## 0.1.0 — 2026-09-17 (2)

- **Modulo `requerimientos` completo** (5 capas: validator/mapper/data/service/
  actions) + rutas `app/(panel)/requerimientos/{page,nuevo,[id]}` + `app/login`
  + `app/auth/logout` + `app/(panel)/layout.tsx` (shell con nav por rol) +
  `app/(panel)/inicio`. Selector de producto: combobox de busqueda client-side
  sobre los 725 SKU (`producto_variante` + `producto`, RLS catalogo ya
  habilitada). Codigo de requerimiento generado en el service
  (`REQ-<empresa>-<yyyyMMdd>-<sufijo>`), UOM del item tomado del `base_uom_id`
  del producto (unica opcion mientras el maestro no traiga UOM real, ver
  DEC pendiente en `0007_maestros_clasificacion.sql`).
- Fix `src/config/constants.ts`: `ESTADO_REQUERIMIENTO` tenia valores que NO
  coincidian con el CHECK real de `requerimiento.status` en
  `0015_requerimientos.sql` (quedo desalineado desde que se escribio antes del
  diccionario final). Corregido a BORRADOR/PENDIENTE_APROBACION/
  VERIFICACION_STOCK/EN_ATENCION/ATENCION_PARCIAL/ATENCION_COMPLETA/RECHAZADO.
  Agregado `PRIORIDAD_REQUERIMIENTO`.
- Agregado `src/core/utils.ts` (`fmtFecha`/`fmtFechaHora`), `app/globals.css`
  (sistema minimo de estilos propio, sin dependencias de RRHH-SYSTEM).
- **2 bugs encontrados y corregidos probando el flujo real en navegador**
  (Playwright headless: login SOLICITANTE -> crear requerimiento con SKU real
  -> enviar a aprobacion -> verlo en bandeja LOGISTICA) — ver DEC-L007 en
  `04_DECISIONES.md`: `auth.service.ts` (alias de columna `rol_id` -> `role_id`
  en la resolucion de roles) y `0033_rls_modulo_requerimientos.sql` (WITH CHECK
  de `requerimiento_update` bloqueaba el envio a aprobacion del dueno).
- `scripts/seed_test_users.mjs`: crea 4 usuarios de prueba (uno por rol) en
  Supabase Auth + `usuario`/`usuario_rol`/`usuario_ceco`. CECOs de prueba
  `ADM`/`OPS` sembrados para TDEMSRL.

## 0.1.0 — 2026-09-17

- Proyecto Supabase **LOGISTICA-TDEM** (`uwaenymwvixuocwbavnz`, org TDEMSRL,
  us-east-2) creado via Management API. `.env.local` (gitignored) completado
  con URL/anon/service_role. `scripts/apply-migration.mjs` (analogo al de
  RRHH-SYSTEM) para aplicar migraciones/seed via Management API con
  `SUPABASE_ACCESS_TOKEN`.
- **Lote 1 (Requerimientos) desplegado**: 17 migraciones aplicadas
  (0001..0010, 0013..0015, 0027, 0031..0033, ver `docs/03_GUIA_OPERATIVA.md`)
  + seed de catalogo (`supabase/seed/0001_catalogo_producto.sql`). Verificado:
  725 `producto_variante`, 589 `producto`, 3 `empresa`, 4 `rol`, 36 `permiso`.
- Fix de esquema descubierto al aplicar el seed contra la BD real:
  `producto_variante.attribute_type` (constraint en `0008_producto.sql`) solo
  permitia `TALLA/COLOR/MODELO/OTRO`, pero el Excel origen trae tambien
  `CLASE` (1 fila) y `CLASE + TALLA` (8 filas). Se amplio el CHECK a
  `TALLA/COLOR/MODELO/CLASE/CLASE + TALLA/OTRO` (fiel al dato origen, sin
  remapeo con perdida) — ver DEC-L006 en `04_DECISIONES.md`.

## 0.1.0 — 2026-09-15 (3)

- `src/services/personal_sync.service.ts`: sincroniza identidad de solo
  lectura desde el endpoint de integracion de RRHH-SYSTEM (aun no creado en
  ese repo), upsert por DNI en `usuario`, migra automaticamente MANUAL ->
  RRHH_SYNC.
- `src/modules/identidad-manual/`: modulo completo (validator/mapper/data/
  service/actions) para el alta manual acotada a METRIN/GOYDEL mientras RRHH
  no los cubra.
- `0032_usuario_cargo.sql`: agrega `usuario.cargo` (columna aditiva fuera del
  diccionario original de Jonathan, pedida explicitamente por el usuario).

## 0.1.0 — 2026-09-15 (2)

- Migraciones SQL completas (0001-0030, `supabase/migrations/`): 74 tablas de
  la Fase 1 (Requerimientos + Compras + Caja Chica), fieles al diccionario de
  datos de Jonathan (`docs_base/ERP_BASE_DATOS_FINAL_V38_MAESTRO_SKU_CONSOLIDADO.xlsx`,
  hoja `03_Diccionario_Datos`), con RLS completo (`current_tiene_rol()`,
  patron LOGISTICA/ADMIN ven todo, SOLICITANTE ve lo propio), vistas RN-007/008
  (`v_costo_referencia_producto`), `fn_saldo_stock()` (RN-001/002), y las
  extensiones DEC-L002 (usuario.fuente RRHH_SYNC/MANUAL + tabla
  `identidad_manual_empresa_habilitada`). Verificado con un chequeo estatico
  de dependencias de FK (sin forward-refs sin resolver) y que las 74 tablas
  tienen RLS habilitado.
- Corregido `auth.service.ts`: el rol no es una columna directa de `usuario`
  (vive en `usuario_rol`, un usuario puede tener rol distinto por empresa) —
  se habia escrito antes de tener el diccionario completo de columnas.

## 0.1.0 — 2026-09-15

- Bootstrap del proyecto: Next.js (App Router, TS), sin `server.ts`/socket.io
  (ver DEC-L001). Estructura `src/config`, `src/core`, `src/services`,
  `src/modules/README.md`. `middleware.ts`, `app/api/health`. `render.yaml` y
  `.env.example` con las variables de la integracion RRHH-SYSTEM.
