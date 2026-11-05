# Decisiones — ERP Logistico Corporativo

Registro de decisiones de arquitectura y de negocio. CONFIG no se modifica sin
actualizar esta lista y el CHANGELOG.

## Arquitectura

- **DEC-L043 — Carga masiva de fotos en el navegador (2026-11-05).**
  Logistica necesitaba cargar cientos de fotos sin el script de Python (que
  pide claves de Storage y aplicar SQL). Se hace en el Catalogo, sin endpoint
  nuevo: el navegador empareja cada archivo con su producto por el nombre,
  reduce la foto (1400 px, JPEG) y la sube por la subida individual
  existente, con sus permisos y su regla de una foto principal activa. Por
  defecto no reemplaza fotos existentes. El script queda para cargas
  iniciales hechas por TI.

- **DEC-L042 — Lenguaje visual v2 y busqueda rapida (2026-11-05).** El
  usuario pidio mejorar la UX/UI de todo el ERP con referencias de apps de
  datos (Linear, Stripe Dashboard, Attio) en vez de galerias de landing
  pages. Se cambia desde las primitivas y estilos compartidos, no pantalla por
  pantalla: texto en oracion en tablas, rotulos y estados; sombras tenidas de
  la marca; alturas de control uniformes; foco azul. Se mantienen la paleta
  (DEC-L021), Geist/Newsreader, el ancho completo y el riel lateral. La
  busqueda rapida (Ctrl+K) reduce opciones visibles (ley de Hick): el menu
  queda igual y lo demas se encuentra escribiendo.

- **DEC-L041 — Padron de personal separado de las cuentas (2026-11-05).**
  El sync con RR.HH. solo traia a quien tuviera correo (1 de 283 activos en
  TDEMSRL) porque creaba una cuenta por persona. El usuario quiere ver a todo
  el personal y decidir en Logistica a quien dar acceso.
  - **Tabla `personal`** (Flyway V21): empresa, DNI, nombre, correo (puede
    faltar), cargo, CECO de planilla, fuente, activo y `user_id` (la cuenta,
    si tiene). **Cada empresa tiene su propia lista** (clave empresa + DNI,
    decision del usuario): no se mezclan TDEM, GOYDEL y METRIN.
  - **Sync RR.HH.**: trae a todo el personal activo, tenga o no correo, y
    **ya no crea cuentas**. Vincula la cuenta existente (por DNI, o una alta
    MANUAL previa sin DNI con su correo) y le actualiza datos; nunca roles.
    Quien deja de venir cesa en el padron y su cuenta se desactiva. Lee todo
    de una vez y escribe por lotes (cientos de personas, BD lejana).
  - **Carga Excel GOYDEL/METRIN**: llena el padron (correo opcional, sin
    "Rol inicial"); si la persona ya tiene cuenta MANUAL, la refleja.
  - **Dar acceso** (Administracion → Personal, solo ADMIN): correo de
    ingreso + roles; crea la cuenta con su CECO de planilla, la vincula y
    devuelve la contrasena temporal. Se audita (`PERSONAL`/`DAR_ACCESO`). La
    cuenta hereda la fuente: la de RR.HH. la sigue controlando el sync.
  - V21 pasa al padron las cuentas que ya tenian DNI y empresa, vinculadas.
  - Requiere que RR.HH. publique el cambio de `personal-activo` (devolver
    `correo: null` en vez de omitir). Desplegar Logistica primero: la version
    anterior omitiria a esas personas con un aviso, sin romper.

- **DEC-L040 — Novedades por version en la app (2026-11-05).** Boton
  "Novedades" en el pie del menu con la version actual; abre el historial de
  versiones (la ultima desplegada, las anteriores plegadas) y marca con un
  punto la version que el usuario aun no abrio (`localStorage`, solo aviso).
  Decisiones del usuario: el texto es **redactado para el usuario** en
  `frontend/src/novedades.ts` (el `docs/CHANGELOG.md` es tecnico y sus
  primeras versiones no aplican al usuario final) y **todos los roles ven el
  mismo historial**. `novedades.ts` es la fuente de la version visible; al
  publicar una version se agrega alli y en el CHANGELOG con el mismo numero.
  El historial parte en 0.2.0 (primera version utilizable).

- **DEC-L039 — Lote 16: necesidad, consolidacion y RFQ multiproducto
  (2026-11-05).** Adopta `purchaseNeeds`, `consolidations`, `rfqBundles` y la
  cotizacion por documento de `rfq-multiproducto.js`/`flujo-operativo.js` de
  la V38.7 (COM-01; Flyway V20). **Reemplaza DEC-L013** (una RFQ = un REQ, sin
  consolidacion) **y la RFQ automatica al proveedor habitual de DEC-L018.**
  Decisiones del usuario:
  - **Necesidad al aprobar**: tras reservar stock, el faltante de cada item
    crea una `necesidad_compra`. Si la regla de Caja Chica **no** aplica, entra
    sola al consolidado ABIERTO de su empresa + producto + variante + unidad
    (un solo consolidado abierto por clave: indice unico parcial
    `uq_consolidacion_abierta` + `insert ... on conflict do nothing` + `for
    update`). Si aplica (MISMO_DIA o BAJO_MONTO, a nivel de item) queda
    PENDIENTE_RUTA y **Logistica confirma** Caja Chica o compra normal.
  - **Consolidacion automatica**, con el **origen visible**: cada consolidado
    muestra de que CECO (proyecto) y REQ viene cada parte, para que Logistica
    no confunda a quien va lo comprado.
  - **Proveedores habilitados por producto** (`proveedor_producto`, varios por
    SKU; se edita en el menu del producto del Catalogo). Reemplaza al
    proveedor habitual unico (V9: `preferred_supplier_id` se migra a la
    cobertura y se elimina). Emitir una OC habilita al proveedor para esos
    productos si aun no lo estaba.
  - **Adjudicacion por producto y OC por cotizacion**: cada consolidado se
    adjudica a una linea cotizada (proveedores distintos por SKU); "Emitir
    OC" junta en una OC todo lo adjudicado a esa cotizacion (= proveedor y sus
    condiciones). Se emite directo (EMITIDA); la aprobacion previa de la OC,
    el pago y la liberacion son el Lote 17.
  Modelo:
  - `consolidacion`: ABIERTA (recibe necesidades) -> EN_COTIZACION (con RFQ;
    cerrado: lo aprobado despues abre otro) -> ADJUDICADA -> ORDENADA, o
    ANULADA con motivo. La necesidad apunta directo a su consolidado (entra
    entera a uno solo), sin `consolidacion_item`.
  - **RFQ multiproducto**: "Preparar solicitudes" toma los consolidados
    abiertos elegidos y agrega una linea por cada proveedor habilitado a la
    RFQ BORRADOR ("por enviar") de esa empresa + proveedor (la reutiliza si
    existe). `rfq.request_id` y `rfq_item.request_item_id` quedan solo en las
    RFQ previas; las nuevas lineas llevan `consolidation_id`. El envio sigue
    fuera del sistema (DEC-L009): la pantalla arma el texto para correo
    (`mailto`) o WhatsApp (`wa.me`) y Logistica **confirma el envio** con su
    canal (`sent_at/by/channel`). La RFQ se cierra sola cuando todas sus
    lineas quedan ordenadas o anuladas.
  - La cotizacion registra solo los productos que el proveedor cotizo (lo que
    no cotiza queda "sin respuesta" en el comparativo). No se puede descartar
    una cotizacion con productos adjudicados.
  - **OC por consolidado**: `orden_item.consolidation_id` (sin
    `request_item_id`); se elimina el UNIQUE de `orden_abastecimiento.quotation_id`
    (una cotizacion puede emitir mas de una OC si se adjudica en momentos
    distintos; la carrera la evita el bloqueo de los consolidados). Anular la
    OC devuelve sus consolidados a EN_COTIZACION sin adjudicar.
  - **Reparto de lo recibido**: la recepcion reparte la cantidad entre las
    necesidades del consolidado (CRITICA, URGENTE, NORMAL; luego la mas
    antigua) y reserva para cada item (`reserva_stock.need_id`); lo que sobra
    queda libre en el almacen. Despacho, NI, atencion y Tracking no cambian.
  - **Falla de proveedor** (RF-025): anular un consolidado en cotizacion deja
    sus necesidades ANULADAS y crea otras PENDIENTE_RUTA con sugerencia
    FALLA_PROVEEDOR (Caja Chica o nueva compra).
  - **Caja Chica** compra contra las necesidades del REQ en ruta Caja Chica o
    por decidir (`caja_chica_item.need_id`); registrar la compra confirma la
    ruta. Su pantalla por REQ reemplaza "Gestionar compra" (la RFQ manual por
    requerimiento ya no existe).
  - Datos previos (V20): cada linea de RFQ anterior pasa a ser un consolidado
    con su necesidad (ORDENADA si tenia OC, ANULADA si la RFQ se cerro sin OC,
    EN_COTIZACION si no); las OC y lo ya recibido quedan enlazados; el
    faltante de REQ en atencion que no estaba en ninguna RFQ queda
    PENDIENTE_RUTA. Probado en `MigracionLote16Test`.
  - Dashboard: el comparativo y el ahorro se miden por consolidado
    (adjudicada vs la oferta mas cara a la misma cantidad); las RFQ se
    filtran por su empresa.
  - No se toma de la V38.7: la solicitud integrada de varias empresas en una
    imagen (una RFQ por empresa + proveedor basta) ni los convenios (orden de
    pedido sin RFQ), que no estan construidos.
  API: `GET /api/compras/necesidades`, `POST /necesidades/{id}/ruta`,
  `GET /consolidados`, `GET /consolidados/{id}`, `POST /consolidados/solicitudes`,
  `POST /consolidados/{id}/adjudicar`, `POST /consolidados/{id}/anular`,
  `GET /por-emitir`, `POST /cotizaciones/{id}/emitir-oc`, `POST /rfq/{id}/enviar`,
  `GET /proveedores-producto`, `PUT /productos/{id}/proveedores`. Se eliminan
  `/pendientes`, `/proveedores-habituales`, `/productos/{id}/proveedor-habitual`,
  `POST /requerimientos/{id}/rfq` y `/cotizaciones/{id}/seleccionar`.
  Test: `ConsolidacionComprasTest` (reemplaza `AtencionAutomaticaTest`).

- **DEC-L038 — Lote 15: control presupuestal y cumplimiento (2026-11-02).**
  Adopta `bcCheck`, `bcRecordCheck`, `bcCompliance` y el Excel de PYC de
  `presupuestos-control.js` de la V38.7 (REQ-02, REQ-03, RES-01, RES-02;
  Flyway V19).
  - **Control por periodo**: `periodo_presupuestal.mandatory_control`, lo
    activa o desactiva PYC (menu del periodo). Desactivado por defecto: sin
    control no se valida ni se registra nada.
  - **Al enviar un REQ** (`RequerimientoService.enviar`, tambien al
    reenviar un observado): el semestre sale de la fecha requerida (o la de
    creacion, hora Lima). Disponible por SKU = programado en la **version
    aprobada vigente** (aunque el presupuesto este en revision, DEC-L030) -
    lo pedido por los otros REQ del CECO en el semestre que no estan en
    borrador ni rechazados. Desviaciones: SIN_PRESUPUESTO_APROBADO,
    SKU_NO_PRESUPUESTADO o EXCESO. Con desviaciones el backend responde
    `code = EXCEPCION_PRESUPUESTAL` y el envio exige justificacion (la UI la
    pide con la confirmacion con motivo). Se bloquea el periodo para que dos
    envios no consuman el mismo saldo.
  - Cada envio controlado queda en `control_presupuestal_req` (CUBIERTO o
    EXCEPCION_JUSTIFICADA, monto a precio congelado, justificacion y
    desviaciones). El detalle del REQ muestra el ultimo control; el aprobador
    lo ve al decidir.
  - **Alertas** (decision del usuario): no hay bandeja de notificaciones; las
    excepciones se listan en el detalle del presupuesto ("Excepciones
    presupuestales"), visibles para PYC, Gerencia, Logistica y Admin. Se
    muestra el ultimo control de cada REQ (equivale a la deduplicacion).
  - **Cumplimiento** (pestana del presupuesto, requiere version aprobada):
    por SKU programado, real (lo despachado a los REQ del CECO y semestre
    con envio RECEPCIONADO/CERRADO, como `AtencionCalculator`), pendiente
    (pedido - real), saldo (programado - real) y disponible (saldo -
    pendiente), valorizados con los precios congelados. Incluye SKU pedidos
    sin presupuesto. Sin precio = SIN PRECIO / INCOMPLETO, nunca cero. Precio
    de compra solo para PYC, Gerencia y Logistica.
  - **Excel** (2 hojas: Resumen por familia y Detalle por SKU, con
    metadatos y autofiltro): exclusivo de PYC en su ambito.
  - **No se adoptan**: gastos manuales (`budgetActuals`; decision del
    usuario: la propia V38.7 los marca "no es consumo por conformidad"),
    conformidad de servicios (el catalogo no tiene servicios) ni el filtro
    mensual (RES-03, pendiente tambien en la V38.7).

- **DEC-L037 — El aprobador del CECO se asigna desde el usuario
  (2026-11-02).** Decision del usuario. La V38.7 guarda un aprobador por
  empresa + CECO (`projectAssignments`), pero lo configura en **Accesos /
  usuarios**; el Lote 12 lo habia puesto en Administracion -> CECOs.
  - El dato no cambia: sigue en `ceco.approver_user_id` (un solo aprobador
    por CECO, la regla de quien decide no se toca).
  - La ficha del usuario con rol Aprobador o Gerencia tiene **"CECOs que
    aprueba"** (`PUT /api/admin/usuarios/{id}/cecos-aprobados`). Elegir un
    CECO que tenia otro aprobador se lo reasigna (la UI lo avisa); quitarlo
    lo deja sin aprobador. Cada cambio mueve los REQ pendientes y se audita
    (`ASIGNAR_APROBADOR`), igual que antes.
  - La pestana CECOs solo muestra el aprobador. Se elimina
    `PUT /api/admin/cecos/{id}/aprobador`.
  - Corrige un error previo: dejar un CECO sin aprobador fallaba en SQL
    (`assigned_to` recibia NULL sin tipo).
  - La ficha muestra "CECOs de su ambito" (en vez de "para los que puede
    pedir") a PYC y Gerencia (DEC-L036).

- **DEC-L036 — Lote 14: Presupuestos semestrales por CECO (2026-11-02).**
  Adopta `presupuestos-resultados.js`, `presupuestos-vistas.js`,
  `presupuestos-aprobacion.js` y la parte de periodos de
  `presupuestos-control.js` de la V38.7 (PRE-01..PRE-04; Flyway V18).
  - **Modelo**: `periodo_presupuestal` (CECO + anio + semestre de 6 meses,
    cerrado o habilitado) -> `presupuesto` (uno no anulado por periodo,
    indice unico parcial; codigo `PRE-00001`) -> `presupuesto_linea` (version
    de trabajo: SKU base x 6 cantidades, como el precio de DEC-L035).
    Cada aprobacion congela una `presupuesto_version` +
    `presupuesto_version_linea` con cantidades, **precio de venta vigente**
    (obligatorio) y **ultimo precio de compra** de la empresa (ultima OC no
    anulada; `SIN_COMPRA` o `NO_VALORIZABLE_PEN` si no hay o no es en PEN:
    nunca se inventa). `presupuesto.current_version_id` = version vigente.
  - **Ambito**: CECOs del usuario (`usuario_ceco`) + los CECOs donde es el
    aprobador asignado. LOGISTICA y ADMIN ven todos, solo consulta (ADMIN
    tampoco decide presupuestos: a diferencia de los REQ, no es respaldo).
  - **Periodos**: los habilita, cierra y reabre **PYC** en su ambito. Cerrado
    = todo solo consulta (no se edita, emite ni decide).
  - **Redactan** (crear, programar, emitir, modificar): PYC, GERENCIA y el
    APROBADOR del CECO en su ambito; el SOLICITANTE solo sus presupuestos
    (`bcWriter` de la V38.7; el usuario confirmo mantenerlo asi, aunque la
    corrida de Jonathan lo crea PYC).
    Nadie con rol LOGISTICA (separacion de funciones, como la V38.7).
  - **Estados**: BORRADOR -> EMITIDO -> APROBADO | OBSERVADO; APROBADO ->
    MODIFICADO (revision) -> EMITIDO; ANULADO. Emitir exige partidas, precio
    de venta en todas y aprobador del CECO (activo, APROBADOR o GERENCIA,
    distinto del solicitante); se copia al presupuesto como en los REQ.
  - **Deciden** (aprobar, observar, anular): el aprobador copiado y la
    GERENCIA del CECO, nunca el solicitante. Observar y anular piden motivo.
  - **Simplificaciones frente a la V38.7**: un OBSERVADO se corrige y
    reemite directo (sin pasar por "Modificar"); el redactor puede anular su
    propio BORRADOR (libera el periodo); un MODIFICADO sigue rigiendo con su
    version aprobada (DEC-L030).
  - Historial desde `log_cambios` (CREAR, EMITIR, OBSERVAR, APROBAR,
    MODIFICAR, ANULAR; PROGRAMACION se audita pero no se lista).
  - El control presupuestal en el REQ y el cumplimiento llegan en el Lote 15
    (DEC-L038).

- **DEC-L035 — Lote 13: precio de venta por empresa y plazo de atencion por
  SKU (2026-11-02).** Adopta `catalogo-precios.js` y `plazos-atencion.js` de
  la V38.7 (Flyway V17).
  - **Nivel**: SKU base (`producto`, 589), no variante (725): las tallas y
    colores de un producto comparten precio y plazo. La V38.7 no distingue
    variantes.
  - **Precio de venta** (`precio_venta_producto`): por empresa + SKU, en PEN y
    mayor que cero. Un vigente por empresa+SKU (indice unico parcial);
    registrar otro desactiva el anterior, que queda de historial. Repetir el
    mismo precio no crea fila. Se audita (`PRECIO_VENTA` en `log_cambios`).
  - **Quien registra** (decision del usuario, se aparta de la V38.7): solo
    **PYC y LOGISTICA**. Logistica es quien conoce los costos; PYC usa el
    precio en su presupuesto. La V38.7 tambien dejaba a GERENCIA (y ADMIN
    por ser respaldo de todo): se descarta porque ninguno conoce los costos.
    Excepcion a "ADMIN = todo lo de LOGISTICA".
  - **Quien ve**: PYC, LOGISTICA, GERENCIA (aprobara presupuestos con esos
    precios) y ADMIN. PYC/GERENCIA en su empresa y en las de sus CECOs;
    LOGISTICA/ADMIN en cualquier empresa activa (operan todos los CECOs,
    DEC-L029). PYC y GERENCIA ahora ven el Catalogo; siguen sin crear
    requerimientos.
  - **Plazo de atencion** (`producto.attention_days`): dias calendario
    0-3650 o sin configurar; solo LOGISTICA/ADMIN, desde el Catalogo (menu
    del producto). Se audita (`PLAZO_ATENCION`). Viaja en el catalogo del
    requerimiento: el carrito muestra el plazo de cada SKU y la fecha
    requerida sugiere la atencion estimada (hoy + el mayor plazo).
  - El precio aun no se usa en el flujo: lo consumen Presupuestos (Lote 14,
    precio congelado al aprobar) y el control presupuestal (Lote 15).

- **DEC-L034 — Familia y Subfamilia como en el maestro SKU (2026-10-12).**
  El usuario reviso el Excel: `13_Catalogo_SKU` trae **Familia** (6: AC, EP,
  EQ, HE, MA, MU) y **Subfamilia** (24, p. ej. AC01 ELECTRICO) y el SKU es
  Familia (2) + Subfamilia (2) + Producto (3). El seed las cargo un nivel mas
  arriba de como se llaman en el Excel, siguiendo el diccionario de 3
  niveles: Familia -> `tipo_producto`, Subfamilia -> `familia_producto`, y
  `subfamilia_producto` = "00 GENERAL" de relleno. No se migran datos ni
  cambian SKU: solo la presentacion.
  - La UI dice **Familia › Subfamilia** (`ACCESORIO › ELECTRICO`) en el
    Catalogo, el buscador del requerimiento, el carrito y las altas/
    solicitudes de producto.
  - Filtro unico agrupado (`components/FiltroFamilia`): cada familia con sus
    subfamilias y la opcion de elegirla entera. Corrige que "ELECTRICO",
    "INFORMATICA", "GENERAL" y "OFICINA" de distintas familias se mezclaban
    (limitacion de DEC-L012).
  - API: `FamiliaDto` y `VarianteCatalogoDto` suman la familia (`tipoId`,
    `tipoNombre`); los nombres de campo del backend siguen las tablas.
  - Pendiente con Jonathan: si el nivel `SUBFAMILIA_PRODUCTO` del
    diccionario se usara; mientras tanto queda oculto.

- **DEC-L033 — Nombres de CECO en mayusculas (2026-10-09).** A pedido del
  usuario, todo nombre de CECO se guarda en mayusculas, con tildes y sin
  espacios repetidos (`LIMA LOGÍSTICA`), venga del sync con RR.HH., de la
  carga Excel o del alta manual (`CodigoCeco.nombre`). Flyway V16 normaliza
  los existentes (la BD es `en_US.UTF-8`: `upper()` conserva las tildes).
  Efecto colateral corregido: el PDF omitia el codigo del CECO si el nombre
  "empezaba" con el (`ADM` en `ADMINISTRACION`); ahora solo si lo trae como
  palabra (`0102 LIMA`).

- **DEC-L032 — CECO repetidos por el cero inicial (2026-10-09).** En RR.HH.
  el mismo CECO existe dos veces: `0102 Lima Logística` y `102 LIMA
  LOGÍSTICA` (al cargar el padron desde Excel se perdio el cero). Eran 25
  pares entre los 61 CECO de TDEMSRL. El usuario pidio consolidarlos y
  borrar los repetidos.
  - **Codigo canonico** (`common/CodigoCeco`): un codigo solo numerico se
    rellena a 4 digitos, igual que el DNI a 8 (DEC-L028). Se aplica en el
    sync con RR.HH., la carga Excel de personal y el alta manual de CECOs.
  - **Sync**: `0102` y `102` son un solo CECO; manda el nombre del que ya
    venia con 4 digitos. El personal de RR.HH. colgado de `102` queda en
    `0102`.
  - **Flyway V15** une los que ya estaban en la BD: pasa los usuarios
    (`usuario_ceco`) y el aprobador al de 4 digitos y **borra el repetido**;
    si alguno tuviera requerimientos, NI o puntos de entrega el delete falla
    por FK y no se aplica nada. Sin par, solo corrige el codigo. Resultado en
    la BD: 36 CECO activos, sin repetidos.
  - **Pendiente en RR.HH.**: los repetidos siguen en su BD, con personal y
    perfiles repartidos entre ambos. Consolidarlos alla (mover referencias y
    dar de baja logica el de 3 digitos, mas normalizar su importacion de
    CECO y personal) queda para decidirlo con el responsable de RRHH-SYSTEM:
    toca datos de produccion de otro sistema. Mientras tanto Logistica ya
    no los duplica.

- **DEC-L031 — Catalogo completo de CECO desde RR.HH. (2026-10-09).** El
  usuario detecto que Administracion -> CECOs solo mostraba ADM y OPS, que no
  son CECOs reales (relleno sembrado por V1). Dos causas: el sync con RR.HH.
  nunca habia corrido contra esta BD, y aun corriendo solo traia los CECO del
  personal activo con correo (`personal-activo`): 1 de los 61 de TDEMSRL.
  - RRHH-SYSTEM publica `GET /api/integraciones/cecos` (solo lectura, mismo
    `X-Service-Token` y rate limit, DEC-006 de RRHH): numero, nombre y
    empresa de todos los CECO activos. Sin consultas entre esquemas
    (DEC-L016): sigue siendo HTTP.
  - `PersonalSyncService` trae el catalogo en la misma corrida y crea/
    actualiza todos los CECO por empresa + numero, tengan o no personal.
  - En cada empresa que RR.HH. cubre, **el CECO que ya no viene se desactiva**
    (decision del usuario: asi salen ADM y OPS). No se borra: los REQ
    historicos lo conservan. Nunca se tocan las empresas de alta manual
    (`identidad_manual_empresa_habilitada`).
  - Igual que con el personal: si el catalogo llega vacio no se desactiva
    ningun CECO; si RR.HH. aun no publica el endpoint (404) el sync sigue
    como antes, con un aviso. Por eso **desplegar RR.HH. primero**.
  - Resultado del sync: nuevo contador `cecosDesactivados`.

- **DEC-L030 — Adopcion de la V38.7 y Lote 12: roles PYC/GERENCIA y
  aprobador por CECO (2026-10-09).** Jonathan entrego la V38.7 fusionada
  (06/10/2026, en `docs_base/V38_7_ENTREGA_2026-10-06/`, gitignored: trae
  credenciales demo y datos de la empresa). Cambios principales frente al V38:
  modulo de **Presupuestos** (periodos por CECO/semestre, SKU x 6 meses,
  aprobacion, precios congelados, control en el REQ, cumplimiento), precios
  de venta por empresa, plazo de atencion por SKU, compras con necesidad +
  consolidacion entre REQ + RFQ multiproducto + aprobacion de OC + emision
  agrupada, pago y liberacion antes del ingreso, cuarentena de lo observado,
  NI programada/piqueada, recepcion en destino parcial con
  subsanacion/devolucion, y calificacion de proveedores (F-LOG-03/07).
  Decisiones del usuario:
  - **Roles**: se agregan **PYC** (presupuestos) y **GERENCIA**. COMPRAS y
    ALMACEN siguen dentro de LOGISTICA (no se separan).
  - **Orden de lotes**: 12 roles/aprobador -> 13 precios de venta y plazo
    de atencion -> 14 Presupuestos -> 15 control presupuestal y cumplimiento
    -> 16 necesidad/consolidacion/RFQ multiproducto -> 17 OC con aprobacion,
    pago y liberacion -> 18 almacen V38.7 -> 19 proveedores y dashboard.
  - **Compras**: se adopta el modelo V38.7 completo; al construir el Lote 16
    se reemplaza DEC-L013 (sin consolidacion) y la RFQ automatica al
    proveedor habitual de DEC-L018.
  - **Pago antes del ingreso**: configurable por condicion de pago
    (contado/anticipo exige pago; credito solo la liberacion de Compras). El
    prototipo exigia pago completo siempre.
  - No se copian inconsistencias del prototipo: un presupuesto en revision
    no deja al CECO "sin presupuesto"; el plazo de atencion se edita en el
    Catalogo; "Proveedor fallo" aplica a toda OC emitida; el modo pruebas de
    proveedores no existe; la reevaluacion de proveedores si es alcanzable.
  Lote 12 (Flyway V14):
  - `rol` PYC y GERENCIA. `ceco.approver_user_id` (+ quien/cuando): un
    **aprobador por CECO**, que fija ADMIN (desde el usuario, DEC-L037) (rol
    APROBADOR o GERENCIA, activo). Se audita (`ASIGNAR_APROBADOR`).
  - `aprobacion.assigned_to`: al enviar, el REQ copia el aprobador de su
    CECO. Decide ese usuario o la GERENCIA que tenga el CECO entre los suyos
    (`usuario_ceco`); ADMIN decide cualquiera como respaldo. Sin aprobador
    asignado (o si el asignado es el propio solicitante o esta inactivo)
    decide cualquier APROBADOR/GERENCIA, como antes.
  - **Nadie decide su propio requerimiento**, tampoco ADMIN.
  - A diferencia del prototipo, **cambiar el aprobador del CECO mueve los
    REQ pendientes** al nuevo (un reemplazo por vacaciones no deja la bandeja
    trabada).
  - Visibilidad (`AmbitoAprobacion`): el aprobador ve un REQ enviado si le
    toca o le toco decidirlo, si no tiene asignado, o (GERENCIA) si el CECO
    es suyo. Antes cualquier APROBADOR veia cualquier REQ enviado.
  - El detalle del REQ solo trae `pendingApprovalId` a quien puede decidir;
    el aviso en vivo va al asignado (`/user/queue/aprobaciones`) o, sin
    asignado, al topic de siempre. GERENCIA ve Aprobaciones y el dashboard.
  - Se mantiene la bandeja de Aprobaciones y Rechazar en el menu (DEC-L027);
    el prototipo los movio al detalle del REQ.

- **DEC-L029 — Ambito del solicitante por CECO y empresa (2026-10-02).**
  Igual que en RRHH-SYSTEM (`perfil_cecos`), el SOLICITANTE solo pide para
  los CECOs que ADMIN le asigna (`usuario_ceco`, sin migracion: la tabla ya
  existia). Antes veia todos los CECOs de su empresa.
  - **Varios CECOs de cualquier empresa**: el usuario decidio dejarlo libre
    porque la logica entre empresas puede variar. La **empresa del REQ sale
    del CECO elegido** (y su codigo `REQ-<empresa>-...`), no de
    `usuario.company_id`, que queda como empresa "de planilla".
  - Sin CECOs asignados no puede crear requerimientos (el formulario lo
    avisa; Administracion marca "Sin CECO" en la fila). LOGISTICA/ADMIN
    piden para cualquier CECO activo.
  - Al corregir un REQ (BORRADOR/OBSERVADO) se conserva su CECO aunque ya no
    este asignado, y no se cambia de empresa (crear uno nuevo).
  - Aplica tambien a usuarios de RR.HH.: es autorizacion de Logistica, no
    dato de personal. El sync de RR.HH. y la carga Excel (DEC-L028) solo
    **agregan** el CECO de planilla; nunca quitan los asignados a mano.
  - Administracion → ficha del usuario → "CECOs para los que puede pedir"
    (buscador + chips), `PUT /api/admin/usuarios/{id}/cecos`. El CECO sale
    de "Editar datos" de DEC-L028. La bandeja del solicitante sigue
    mostrando solo sus propios REQ.

- **DEC-L028 — Padron de personal de GOYDEL y METRIN (2026-09-30).**
  RRHH-SYSTEM solo tiene el personal de TDEM; el usuario decidio que el de
  GOYDEL y METRIN se registre **en Logistica** hasta que RR.HH. lo cubra
  (antes solo existia el alta manual de uno en uno, sin edicion ni cargo).
  - **Carga por Excel** (Administracion → Carga de personal, solo ADMIN,
    `PersonalManualService`): la plantilla trae al personal manual actual;
    un mismo archivo da altas, corrige datos y registra ceses (Activo = NO).
    Validar no escribe; confirmar re-valida y aplica todo o nada, igual que
    la carga masiva de almacen (DEC-L022). Solo empresas de
    `identidad_manual_empresa_habilitada`.
  - **El DNI identifica a la persona** (obligatorio en el Excel; si Excel
    quita ceros a la izquierda se rellenan a 8 digitos). Una persona MANUAL
    antigua sin DNI se reconoce por su correo. Nunca toca usuarios de
    RR.HH. ni de otra empresa, ni reutiliza un correo ajeno. Cuando RR.HH.
    incorpore la empresa, el sync los absorbe por DNI (DEC-L024).
  - **Rol inicial** solo para altas (SOLICITANTE por defecto; APROBADOR o
    LOGISTICA). ADMIN no se da por Excel y los roles de los existentes se
    cambian en su ficha. Cargo y CECO en blanco = no se cambian. Un CECO
    que no existe se crea si la fila trae su nombre.
  - El acceso (contrasena temporal) sigue siendo por persona: sin SMTP en
    v1.0 no se entregan contrasenas en lote.
  - **Edicion individual** de usuarios MANUAL (nombre, correo, DNI, cargo,
    CECO) y cargo en el alta manual. Sin migracion: `usuario.cargo` ya
    existia (V11).

- **DEC-L027 — Lote 10: observar, programar entrega y confirmaciones (2026-09-30).**
  - **Observar REQ** (RF-006/048): nuevo estado `OBSERVADO` del requerimiento
    (Flyway V13 amplia el CHECK). El aprobador observa con motivo obligatorio
    (`aprobacion.status = OBSERVADO`, motivo en `decision_reason`). El REQ
    vuelve al solicitante: se edita y se envia igual que un BORRADOR
    (`EstadoRequerimiento.EDITABLES`); reenviar crea **otra** aprobacion
    PENDIENTE, asi el historial conserva cada ronda. El motivo se muestra en
    el detalle solo mientras esta observado; el Tracking lo guarda como
    evento ("Observado: ..." / "Reenviado a aprobacion"). Aviso en vivo al
    solicitante; el correo (RF-007) queda para v1.1 (DEC-L023).
  - Aprobar/Observar/Rechazar: Aprobar a la vista, Observar como accion
    secundaria y Rechazar en el menu "⋯" (es terminal y menos frecuente).
  - **Programar entrega** (RF-068): Logistica/Admin fija
    `scheduled_delivery_date` mientras el REQ esta en atencion
    (VERIFICACION_STOCK, EN_ATENCION, ATENCION_PARCIAL), nunca en el pasado.
    No toca la fecha requerida. Reprogramar se permite; cada cambio se audita
    y aparece en el Tracking. La columna ya existia (V1) pero la entidad la
    tenia de solo lectura.
  - **Confirmaciones** (RF-047/064): ventana corporativa propia
    (`components/ui/Confirmacion.tsx`, `useConfirmar()`), reemplaza
    `window.confirm`. Se confirma toda accion sensible: decidir un REQ,
    anular OC, cerrar RFQ, inactivar producto, restablecer contrasena,
    desactivar usuario, aplicar carga masiva y confirmar recepcion en destino.
    Las irreversibles (anular OC, cerrar RFQ, observar, rechazar) **exigen
    motivo**, validado tambien en el backend.
  - **Auditoria**: `log_cambios` (existia desde V1 sin uso) registra quien,
    cuando, antes/despues y motivo de anular OC, cerrar RFQ y programar
    entrega (`common/Auditoria`), en la misma transaccion que la accion. No
    se agregan columnas de motivo a cada tabla.

- **DEC-L026 — Lote 9: Caja Chica como ruta de compra del REQ (2026-09-28).**
  Aplica DEC-L003 (ruta, no modulo aparte; solo referencia el aplicativo
  externo de Caja Chica, nunca duplica su fondo/rendicion).
  - **Lo comprado ya esta en mano**: al registrar la compra entra al almacen
    central (kardex ENTRADA, `reference_type = CAJA_CHICA`) y queda
    reservado para el item del REQ (`reserva_stock.origin = CAJA_CHICA`,
    con `petty_cash_item_id` para el costo). Asi el despacho, la NI (valor
    con el costo de la compra), el envio, la atencion (RF-079) y el Tracking
    no cambian. Se descarto un flujo de "recepcion de Caja Chica": duplicaba
    la recepcion de OC para algo que ya llego.
  - **Pendiente de compra** = solicitado - stock - RFQ activa - ordenado -
    **comprado por Caja Chica** (no anulado), en `ComprasService` y en la
    consulta unica de "Pendientes de compra".
  - **Regla** (`regla_caja_chica`, ya existia sin filas desde V1): se siembra
    la corporativa VIGENTE con **umbral S/ 100 referencial (PD-05 sigue
    abierto)**; ADMIN la edita (umbral y motivos habilitados).
  - **Motivos** (RF-023..025): BAJO_MONTO (total real <= umbral, misma
    moneda), MISMO_DIA (urgente/critico con fecha requerida hoy o vencida),
    FALLA_PROVEEDOR (RFQ cerrada sin OC) y EXCEPCION_AUTORIZADA (exige quien
    y por que). La sugerencia estima el pendiente con el ultimo costo
    conocido (OC o Caja Chica); sin costo de referencia no sugiere bajo monto.
  - **Comprobante unico** (RF-029/056): RUC (o razon social) + tipo + serie +
    numero sin ceros a la izquierda + importe. El proveedor se guarda como
    texto del comprobante (una ferreteria rara vez esta en el maestro) y se
    enlaza al maestro si el RUC existe.
  - **Regularizacion**: PENDIENTE_REGULARIZACION -> REGULARIZADO (con el
    codigo del movimiento en el aplicativo de Caja Chica) u OBSERVADO con
    motivo. Fuera de v1.0: OC de regularizacion (RF-028) y anular una compra
    ya ingresada (habria que revertir stock y reservas).

- **DEC-L025 — Lote 8b: alta de productos y solicitudes de alta (2026-09-28).**
  - **Codigo SKU generado por el sistema**, con el mismo formato de los 725
    sembrados: tipo (2) + familia (2) + correlativo de 3 digitos dentro de
    la familia (`AC01008`). La familia se bloquea (`for update`) mientras se
    calcula el correlativo. Variante = SKU + `-` + codigo del atributo
    (`EP01027-L`), o `-00` si no tiene atributo. El nombre se guarda en
    mayusculas y sin espacios repetidos, como el maestro origen.
  - **Solo LOGISTICA/ADMIN crean productos** (Catalogo → Nuevo producto).
    Duplicado exacto (mismo nombre en la familia) se bloquea.
  - **RF-004**: el solicitante pide el alta desde el formulario del
    requerimiento ("¿No encuentras el producto?"). Queda en
    `solicitud_maestro_producto` (tabla ya existente) como
    PENDIENTE_APROBACION_LOGISTICA, con aviso de posible duplicado (nombre
    parecido en la familia). Logistica la aprueba (puede corregir nombre,
    familia y unidad; se crea el SKU y queda enlazado en
    `producto.approved_request_id`) o la rechaza con motivo. El solicitante
    ve el estado y el SKU generado en "Mis solicitudes de producto".
  - Sin subfamilias en la UI: se usa la "00 GENERAL" de cada familia, igual
    que el seed (DEC-L012). La unidad `ND` no se ofrece para altas nuevas.
  - Edicion minima: inactivar (sale del buscador de requerimientos) y
    agregar una variante del mismo tipo de atributo.

- **DEC-L024 — Lote 8a: identidad desde RR.HH. y Administracion (2026-09-28).**
  - **Sync de solo lectura** (`modules/identidad/PersonalSyncService`) contra
    la API que ya existia en RRHH-SYSTEM (`GET /api/integraciones/personal-activo`,
    header `X-Service-Token`, DEC-006 de RRHH). Una sola llamada trae todo el
    personal activo (RRHH limita a 30/min). Upsert por DNI; si no existe por
    DNI pero si por correo (alta MANUAL previa), esa fila pasa a RRHH_SYNC
    sin duplicarse (DEC-L002). El CECO del trabajador se crea/actualiza por
    empresa + numero y va a `usuario_ceco`. Usuario nuevo = rol SOLICITANTE;
    el resto de roles los asigna ADMIN y el sync nunca los pisa.
  - **Cese (RF-041/065)**: quien deja de venir como activo se desactiva. Si
    RRHH devuelve una lista vacia **no se desactiva a nadie** (es mas
    probable un error de configuracion que un cese masivo).
  - Se ejecuta con "Sincronizar ahora" (ADMIN) y a diario a las 06:00 Lima
    (`RRHH_SYNC_CRON`). Sin `RRHH_API_URL` queda apagado. La llamada HTTP va
    fuera de la transaccion; la escritura es todo-o-nada.
  - **Acceso vigente por request** (`security/AccesoVigente`): el JWT guarda
    los roles del login; sin esto, un usuario desactivado o con roles
    cambiados seguiria operando hasta 8 h. Ahora el filtro consulta estado y
    roles (una consulta, cache de 60 s por usuario; ADMIN y el sync la
    invalidan).
  - **Acceso inicial sin correo** (no hay SMTP en v1.0): ADMIN genera una
    contrasena temporal que se muestra una sola vez; el usuario debe
    cambiarla al entrar (`usuario.must_change_password`, Flyway V11). Regla:
    minimo 8 caracteres con letras y numeros.
  - **Alta manual acotada**: usuarios y CECOs a mano solo para empresas en
    `identidad_manual_empresa_habilitada` (hoy METRIN y GOYDELSAC). Los de
    RRHH no se activan/desactivan a mano: para quitarles acceso se les
    quitan los roles.
  - La alta de productos/SKU (RF-004) pasa al Lote 8b.

- **DEC-L023 — Alcance de la v1.0 (marcha blanca) (2026-09-28).** Criterio:
  usuarios reales de las 3 empresas hacen en produccion el circuito completo
  sin tocar la BD a mano. Lotes: 8 (identidad RRHH + administracion + alta
  de SKU), 9 (Caja Chica con umbral configurable), 10 (Observar, programar
  entrega, confirmaciones), 11 (despliegue en Render region `ohio` y marcha
  blanca). Decisiones del usuario: correo en v1.1 (en v1.0 bastan los avisos
  en vivo); **Caja Chica en v1.0** con umbral editable aunque PD-05 siga
  abierto; **identidad sincronizada con RRHH desde v1.0**. Fuera de v1.0:
  aprobacion por monto (PD-04), evaluacion de proveedores (TO-BE no
  cerrado), convenios/consolidacion, comparativo contra ultima compra,
  solicitante externo (PD-08), valor congelado y el resto de alertas.

- **DEC-L022 — Lote 7 (parte 2): PDFs corporativos y carga masiva de
  ingresos (2026-09-28).**
  PDFs (RF-038/082/083):
  - Se generan en el backend con PDFBox (ya era dependencia por el OCR), no
    con jsPDF en el navegador como el prototipo: un solo lugar arma el
    documento y aplica los permisos. Paquete `documentos/`: `PdfCorporativo`
    (maqueta A4: cabecera con logo, pie con plantilla/version, quien y
    cuando lo genero, "Pagina i de n"; tablas que saltan de pagina) y
    `DocumentosPdf` (contenido de cada documento).
  - Cada PDF se arma desde el **mismo DTO de su pantalla**, obtenido por el
    servicio del modulo, asi que hereda sus permisos: REQ y Tracking los ve
    quien ve el REQ; RFQ, OC y NI solo LOGISTICA/ADMIN.
  - Documentos: Requerimiento, Solicitud de Cotizacion, Orden de Compra,
    Nota de Inventario y Tracking. **Fuera**: Orden de Pedido (depende de
    convenios, no construidos — DEC-L013) y Guia (en este sistema la guia es
    un documento del proveedor que solo se registra; no se emite).
  - "Versionado" = codigo de plantilla en el pie (`PLT-NI v1`, etc.); si
    cambia la maqueta de un documento se sube su version en `DocumentosPdf`.
    No se guarda copia de cada PDF emitido: se regenera desde los datos.
  - Logo de TDEM (extraido del prototipo V38) solo en documentos de
    TDEMSRL; GOYDEL y METRIN llevan su codigo en texto hasta tener su logo.
  - Helvetica estandar (sin fuentes embebidas): cubre tildes y enes; otros
    caracteres se reemplazan.
  Carga masiva (RF-031, "validar todo antes de afectar stock"):
  - Plantilla Excel con todo lo pendiente de las OC que admiten recepcion
    (una fila por item, con su ID). Logistica llena "Cantidad a ingresar"
    solo en lo que llego; **las filas sin cantidad se omiten** (el prototipo
    las marcaba como error, pero con una plantilla de todo lo pendiente eso
    obligaba a borrar filas).
  - Validar no escribe nada. Confirmar vuelve a validar y registra una
    recepcion por OC (`registration_source = CARGA_MASIVA`, ya previsto en
    V8) en **una sola transaccion**: si una fila falla, no entra ninguna.
    No hace falta tabla de lotes: la trazabilidad queda en `recepcion`.
  - Guia opcional por OC (serie, numero y fecha); una guia por OC, igual que
    en la recepcion manual. Incidencias (dano/faltante/producto errado) no
    van por Excel: se registran en la pantalla de recepcion de la OC.
  Tambien se corrigio un bug latente: las rutas `/almacen/*` del frontend
  (Lote 6) no estaban en `SpaController` ni en `SecurityConfig`; en el
  despliegue de un solo servicio, recargar esas pantallas habria fallado.

- **DEC-L021 — Paleta azul corporativa y login con escena animada (2026-09-25).**
  El usuario pidio un tono "mas acorde": se reemplaza el monocromo calido por
  azul corporativo. Solo cambian los tokens de `globals.css` (grises frios en
  la escala `slate`; `brand` = marino #0b3a6e para acciones principales y azul
  acero #2f6fb3 para enlaces y graficos), mas los rellenos que eran carbon
  (boton primario, barras de empresa, logo, barras de progreso).
  Login: texto minimo ("ERP LOGISTICO" + "TDEM · GOYDEL · METRIN") y una
  escena del rubro electrico (`EscenaEntrega.tsx`): un operario con casco
  lleva una caja desde el almacen, bajo el tendido de alta tension, hasta
  una subestacion; se enciende el farol y aparece el check de entregado.
  SVG dibujado en tiempo real con requestAnimationFrame (sin librerias; los
  pulsos de energia de los cables con SMIL). Se anima aun con
  `prefers-reduced-motion`: el equipo del usuario tiene apagados los efectos
  de animacion de Windows (comun en PCs corporativas) y la escena quedaba
  congelada; el movimiento es chico y sin zoom/parallax. El resto de la app
  si respeta esa preferencia. Se probo antes un carrusel de
  fotos (Skiper UI + Swiper) y se descarto: las fotos no eran del rubro.

- **DEC-L020 — Dashboard operativo en Inicio (2026-09-25).** A pedido del
  usuario, Inicio muestra (a LOGISTICA/ADMIN/APROBADOR) indicadores del
  proceso, de respuesta de proveedores y del ahorro por comparar
  cotizaciones. `GET /api/dashboard?dias=&empresa=` (`DashboardService`)
  resuelve todo en **una sola consulta SQL que devuelve JSON**: con la BD de
  Supabase en us-east-2, cada ida y vuelta desde local cuesta ~150 ms.
  Definiciones:
  - Abiertos: PENDIENTE_APROBACION, OBSERVADO (desde DEC-L027), VERIFICACION_STOCK, EN_ATENCION y
    ATENCION_PARCIAL (hoy, sin filtro de periodo).
  - Aprobacion: `aprobacion.decided_at - created_at` de las aprobadas.
  - Ciclo: creacion del REQ -> ultimo envio recibido en destino, de los REQ
    en ATENCION_COMPLETA.
  - Respuesta de proveedores: RFQ (no BORRADOR) con al menos una cotizacion;
    tiempo = primera `cotizacion.received_at` - `rfq.created_at`. Nueva
    columna `received_at` (Flyway V10); las cotizaciones previas se
    completaron con su fecha de emision o la de la RFQ. Mide cuando
    Logistica registra la respuesta, no cuando el proveedor la envio.
  - Ahorro: por REQ con cotizacion SELECCIONADA y 2+ cotizaciones no
    descartadas en la misma moneda, `mayor - elegida`, sumado por moneda
    (no se convierte PEN/USD).
  - Etapas: trabajo pendiente hoy; un REQ puede estar en varias (items en
    paralelo), por eso no es un embudo exclusivo.
  El solicitante sigue viendo el indice de modulos. `EMPRESAS` pasa a
  `components/empresas.ts` (compartido con Requerimientos).

- **DEC-L019 — Lote 7 (parte 1): Tracking calculado, no registrado (2026-09-25).**
  El Tracking (RF-039/081/083) no tiene tabla propia ni usa `log_eventos`
  (nadie la escribe): `TrackingService` lo reconstruye en cada consulta
  desde `aprobacion`, `reserva_stock`, `rfq`, `orden_abastecimiento`,
  `recepcion`, `nota_inventario` y `envio`, asi nunca se desalinea del flujo
  (la matriz V38 pide "los hitos se calculan desde eventos y entregas; no son
  checkboxes manuales"). Un solo DTO alimenta la pantalla y el Excel (RF-081).
  Reglas:
  - Avance = unidades entregadas en destino (envios RECEPCIONADO/CERRADO) /
    solicitadas; el exceso de un item no compensa el faltante de otro.
  - "Atencion parcial" se marca solo si hubo una entrega que no completo el
    REQ; si todo llego en una sola entrega queda sin marcar ("Atendido en una
    sola entrega").
  - "Aprobado" se toma del estado del REQ (y la fecha/usuario de la
    aprobacion si existe), para no depender de que haya fila en `aprobacion`.
  - Visibilidad: la misma del detalle del REQ (dueno, LOGISTICA/ADMIN,
    APROBADOR si no es borrador). No existe perfil Gerencia/Almacen aun.
  - "Valor del pedido" usa `requerimiento.total_value_net_snapshot`; como
    ningun flujo lo llena todavia, cae a la suma del valor de las NI emitidas.
    `scheduled_delivery_date` y `delivery_address_snapshot` tampoco se
    llenan: se muestran la fecha requerida y "—" hasta que exista el flujo de
    programacion de entrega.
  - Excel con Apache POI en el backend (y no SheetJS en el navegador) porque
    RF-031 (carga masiva de ingresos) tambien necesitara leer Excel.
  Pendiente del Lote 7: PDF del Tracking (RF-083) y de la NI (RF-082), PDFs
  corporativos (RF-038) y carga masiva de ingresos (RF-031).

- **DEC-L018 — Atencion automatica al aprobar (2026-09-24).** El usuario
  rechazo el paso manual "Verificar stock y reservar": tras aprobar un REQ,
  Logistica tenia que volver a confirmar cuanto apartar, lo que se sentia
  como una segunda aprobacion. Ahora, al aprobarse (`AprobacionService.decidir`,
  misma transaccion):
  1. `StockService.reservarDisponible` aparta por item min(disponible,
     solicitado) en el almacen central (RF-009, "Sistema"), con lock de la
     fila del almacen para que dos aprobaciones simultaneas no aparten el
     mismo stock.
  2. `ComprasService.crearRfqsAutomaticas` cotiza el faltante al **proveedor
     habitual** de cada producto (`producto.preferred_supplier_id`, V9): una
     RFQ ENVIADA por proveedor (RF-010). Items sin proveedor habitual quedan
     en "Pendientes de compra" para RFQ manual.
  3. El REQ pasa directo a EN_ATENCION. VERIFICACION_STOCK queda en el CHECK
     solo por historial; se eliminaron sus endpoints y la seccion del detalle.
  El proveedor habitual **se completa solo** con la primera OC emitida del
  producto y es editable en el Catalogo (solo LOGISTICA/ADMIN; el solicitante
  no lo ve). Como la RFQ automatica saca al REQ de "Pendientes de compra",
  Compras muestra ademas "RFQ en curso" (ENVIADA/RESPONDIDA). La pagina Stock
  pasa a mostrar saldos (fisico, reservado, disponible).
  Tambien a pedido del usuario: se quitaron los textos de ayuda largos de las
  pantallas (subtitulos descriptivos, parrafos explicativos, hints).

- **DEC-L017 — Lote 6: Almacen Central (2026-09-24).** Cierra el circuito
  Requerimiento -> Stock/Compra -> Recepcion -> Despacho (NI) -> Envio ->
  Entrega. Alcance: RF-030/032/033 (recepcion de OC, "en validacion
  funcional" en la matriz V38), RF-072..076/078 (despacho, NI, envio),
  RF-077/080 (adjuntos del envio) y RF-079 (atencion automatica). Fuera de
  alcance (Lote 7): carga masiva de ingresos (RF-031), Tracking completo y
  vista de entregas en el detalle del REQ (RF-039/081/083), PDFs (RF-038/082).
  Decisiones del usuario:
  - A stock entra **solo lo conforme**. Dano/faltante/producto errado se
    registra como `incidencia_recepcion` (con accion del proveedor: reponer,
    cambio, nota de credito) y la cantidad **sigue pendiente** en la OC.
  - Confirmar la recepcion en destino es **solo LOGISTICA/ADMIN** (matriz V38).
  - No hay perfil "Almacen" (07_Perfiles_V38): opera LOGISTICA/ADMIN.
  Reglas: lo recibido de una OC queda **reservado** para el item de REQ que
  origino la compra (`reserva_stock.origin = RECEPCION_OC`); solo se despacha
  lo reservado; cada despacho = `salida` + `salida_item` (con
  `request_item_id`) + `nota_inventario` (`NI-<n>-<EMPRESA>`, varias por REQ)
  + movimiento SALIDA en el kardex; las reservas se consumen FIFO y se parten
  si hace falta. Una OC con recepciones ya no se puede anular.
  Correcciones de bugs latentes que este lote habria destapado:
  1. `StockService.decidir` marcaba ATENCION_PARCIAL/COMPLETA al *reservar*.
     Ahora deja EN_ATENCION y el estado de atencion se calcula solo desde
     envios RECEPCIONADOS (`AtencionCalculator`, RF-079).
  2. `ComprasService.pendienteParaCompra` restaba solo reservas RESERVADA: al
     despachar, el item habria vuelto a "pendiente de compra", y lo recibido
     de una OC se habria restado dos veces. Ahora resta las reservas de
     origen VERIFICACION_STOCK (RESERVADA o DESPACHADA); las RECEPCION_OC ya
     cuentan como cantidad ordenada.
  `incidencia_recepcion.issue_type` usa `DANO` sin enye (el diccionario dice
  DANO con enye) para evitar problemas de codificacion; la UI muestra "Dano".

- **DEC-L016 — LOGISTICA en la BD de RRHH-SYSTEM, esquema propio
  (2026-09-24).** El usuario ya paga Supabase Pro por RRHH y no quiere un
  segundo pago (~USD 10/mes por proyecto adicional) mientras LOGISTICA este
  en desarrollo. El proyecto `LOGISTICA-TDEM` solo tenia datos de prueba y se
  retira. Diseno:
  - Esquema `logistica` con rol dedicado `logistica_app` (dueno del esquema,
    sin permisos sobre las tablas de RRHH), creado a mano con
    `db_design_reference/setup/rrhh_db_esquema_logistica.sql`. Obligatorio:
    ambos sistemas tienen tablas `ceco`, `log_cambios`, `log_errores` y
    `log_eventos`.
  - El esquema no se expone en la Data API y no tiene grants a
    `anon`/`authenticated`: tablas sin RLS en `public` quedarian legibles con
    la anon key publica del frontend de RRHH.
  - Migraciones con placeholder `${schema}` en vez de `public.`. Se
    reescribieron V1..V7 (valido: la BD vieja se abandona). **Riesgo real
    corregido**: `V2__disable_rls.sql` borraba TODAS las policies y
    desactivaba RLS de TODO `public`; contra la BD de RRHH habria expuesto sus
    datos. Ahora es no-op, y V1 ya no crea las funciones/policies de Supabase
    Auth (muertas desde DEC-L008). `beforeMigrate.sql` aborta si el esquema
    fuera `public`. Se quito `baseline-on-migrate` (V1 era un snapshot que
    nunca se habia ejecutado con Flyway; ahora corre completo y esta cubierto
    por `MigracionesFlywayTest` sobre Postgres 17 real).
  - Separabilidad: nada de FKs/vistas/queries entre esquemas; la integracion
    con RRHH sigue siendo solo HTTP (DEC-L002). Separar = `pg_dump` del
    esquema + cambiar `DB_*` (runbook en `03_GUIA_OPERATIVA.md`).
  - Riesgos aceptados: un restore de backup de Supabase restaura ambos
    sistemas a la vez (mitigado con `pg_dump` periodico del esquema), y ambos
    comparten conexiones/CPU de la misma instancia (pool de Hikari en 5).
  - Render: fuera de alcance por ahora (cobra por servicio; se decide al
    acercarse la marcha blanca).

- **DEC-L015 — Storage S3-compatible para imagenes/documentos
  (2026-09-24).** Con ~725 SKU (589 productos base) con foto, el catalogo y el
  buscador de requerimientos cargan cientos de miniaturas por sesion. Se
  evaluo Cloudflare R2 (sin cargo por egress, 10 GB gratis), pero exige
  registrar una tarjeta y el usuario prefiere no atar el sistema a su
  tarjeta personal (lo ideal seria una tarjeta corporativa). Google Drive se
  descarto: no sirve como hosting de imagenes para una app web (hotlinking
  bloqueado, limites de trafico, cuentas de servicio sin cuota propia). Se
  usa **Supabase Storage del proyecto de RRHH** (el plan Pro incluye 100 GB
  + 250 GB de egress) via su API S3, con un unico `S3StorageClient` (AWS SDK
  v2) que tambien sirve para R2 cambiando solo variables. Se usan las llaves
  S3 de Storage, no la service_role key (esa daria acceso total a la BD de
  RRHH). Limitacion conocida: las llaves S3 de Supabase valen para TODOS los
  buckets del proyecto, incluidos los de RRHH (`cvs`, `cese-docs`, etc.);
  quien tenga las variables del backend de LOGISTICA podria leerlos. Al
  separar LOGISTICA a su propio proyecto (o migrar a R2) esto desaparece. Las imagenes se sirven directo desde la URL publica del bucket,
  nunca a traves del backend. Nota: las cotizaciones siguen en el mismo
  bucket publico; si se requiere privacidad, moverlas a un bucket privado
  con URLs firmadas.

- **DEC-L014 — OCR de cotizaciones con Tesseract local, no un LLM de pago
  (2026-09-23).** El diseno de referencia preve extraccion asistida al
  adjuntar una cotizacion ("el sistema propone, el usuario valida"). Se
  evaluaron dos caminos: (a) un modelo de vision hosteado (Claude/GPT),
  con salida JSON estructurada directa y costo estimado de ~$2-4 por 1000
  documentos con Haiku 4.5 (ver calculo en la sesion); (b) OCR clasico
  local (Tesseract). El usuario eligio (b): el proyecto va a integrar
  varias APIs mas adelante y prefirio no sumar otra dependencia de pago
  desde ahora, incluso siendo el costo marginal. Trade-off aceptado a
  proposito: Tesseract solo lee texto, no "entiende" el documento — no hay
  forma confiable de mapear automaticamente costos por item de la cotizacion
  a los items de la RFQ (el diseno de referencia tampoco lo hacia). Se
  extraen por regex solo campos de cabecera con patrones predecibles (RUC
  peruano, fecha, moneda, total, plazo en dias); el costo por item sigue
  siendo manual, con el texto OCR crudo visible como referencia. PDFBox
  rasteriza paginas de PDF a imagen (Tesseract no lee PDF nativo); tessdata
  en espanol se embebe como recurso del backend (`spa.traineddata`,
  variante "fast", ~2.2MB) para no depender de una instalacion nativa
  aparte de Tesseract en el servidor (Tess4j + `bytedeco:tesseract-platform`
  traen los binarios nativos). Verificado con una prueba de humo
  (`OcrExtractorTest`) que genera una cotizacion sintetica y confirma que
  las librerias nativas cargan y el texto se reconoce correctamente.

- **DEC-L013 — Alcance de Lote 5 (Proveedores + RFQ + Cotizaciones + Orden
  de Compra), sin Caja Chica (2026-09-23).** El diseno de referencia
  (`db_design_reference/migrations/0012_proveedores.sql`,
  `0018_abastecimiento.sql`, `0019/0020_cotizacion*.sql`,
  `0022_compras.sql`, `0023_caja_chica.sql`, `0024_documentos_compra.sql`)
  es mucho mas grande que lo construido: incluye consolidacion cruzada entre
  requerimientos, convenios de precio fijo, catalogo de precios por
  proveedor, caja chica como ruta alterna y documentos de compra que
  dependen de Almacen (Fase 2). Se le presento al usuario el recorte y
  eligio **Proveedores (CRUD simple) + RFQ + Cotizaciones + Orden de Compra
  unicamente**, dejando Caja Chica para un lote aparte (depende de validar
  el umbral PD-05) con una pantalla simple de alta de proveedores (no solo
  seed SQL) para poder probar el flujo end-to-end en el navegador.
  - **Sin consolidacion cruzada** (RN-002/004): una RFQ nace de items de UN
    solo requerimiento, no agrupa el mismo SKU entre varios requerimientos
    pendientes (`necesidad_abastecimiento`/`consolidacion` no se
    construyen; `rfq_item` referencia `requerimiento_item` directo, sin la
    tabla `rfq_item_origen` del diseno de referencia). Limitacion conocida:
    si dos requerimientos distintos piden el mismo SKU, Logistica manda dos
    RFQ separadas, con posible perdida de poder de negociacion por volumen.
  - **Sin `consulta_proveedor`/`comunicacion_proveedor`**: no hay
    integracion SMTP/WhatsApp (mismo argumento que DEC-L009). Logistica
    contacta proveedores fuera del sistema; el sistema solo registra la RFQ
    y las cotizaciones que vuelven (siempre `source='MANUAL'`, sin pipeline
    OCR).
  - **Sin convenios ni catalogo de precios** (`convenio_proveedor*`,
    `escala_precio_proveedor`, `proveedor_producto`): solo se construyo la
    ruta RFQ, no CONVENIO. Logistica tipea el costo a mano por item
    cotizado.
  - **Sin `orden_item_distribucion`**: este backend es 1 requerimiento = 1
    CECO, asi que `orden_item` lleva `request_item_id` directo.
  - **Una sola cotizacion ganadora por RFQ**: `seleccionarCotizacion`
    descarta automaticamente a las demas cotizaciones de la misma RFQ y la
    cierra. No se puede repartir el award de una RFQ entre dos proveedores.
  - **`orden_abastecimiento.quotation_id` queda `NOT NULL`**, sin las
    columnas `agreement_id`/`petty_cash_ref_id` del diseno de referencia
    (esas rutas no existen todavia) — costo aceptado a proposito: un lote
    futuro de Convenio/Caja Chica va a necesitar un `ALTER TABLE` para
    agregarlas.
  - **Sin `rfq.penalty_rule_id`**: `regla_penalidad` sigue sin filas (PD-03
    sin resolver) y nada en este lote lo lee — mismo criterio que no
    construir `v_costo_referencia_producto` (RN-007/008) "para despues".
  - **Sin documentos de compra** (`guia_remision`/`factura_proveedor*`/
    `plantilla_documento`): dependen de la recepcion fisica de Almacen
    (Fase 2).
  - **`requerimiento.status` no cambia** al emitir una OC: sigue en
    `EN_ATENCION`/`ATENCION_PARCIAL` hasta que Almacen (Fase 2) reciba
    fisicamente. La visibilidad de que "hay una compra en curso" se
    resuelve con un bloque de solo lectura en `RequerimientoDetalle.tsx`
    (lista las RFQ existentes), no con un estado nuevo — evita tocar el
    CHECK de `requerimiento.status`, del que ya dependen 3 lotes.
  - **PD-01 (visibilidad de costo)**: igual que el resto del backend — los
    endpoints de Compras estan gateados a LOGISTICA/ADMIN (mismo
    `exigirLogisticaOAdmin` que Stock/Catalogo), SOLICITANTE/APROBADOR
    nunca llaman a esos endpoints.
  - **PD-04 (rangos de aprobacion por monto)**: la Orden de Compra la emite
    directo LOGISTICA/ADMIN sin un paso de aprobacion monetaria adicional
    (`regla_aprobacion` sigue sin filas) — mismo criterio pragmatico que
    DEC-L009 para Aprobaciones.
  - **"Cantidad pendiente de comprar" por item** (solicitado - reservado -
    en RFQ activa - ya ordenado) se calcula en Java en `ComprasService`,
    nunca una columna — mismo patron que `StockService.saldoDisponible`
    (DEC-L011). Anular una orden o cerrar una RFQ sin OC libera
    automaticamente esa cantidad (se excluye de la suma por status).
  - **6 bugs reales corregidos en revision de codigo** (ver CHANGELOG
    0.7.0): rutas nuevas faltantes en `SpaController`/`SecurityConfig`
    (incluia `/catalogo` del Lote 4, tambien faltante), `OrdenItemDto`
    mostraba el SKU del requerimiento original en vez de lo efectivamente
    ordenado, falta de proteccion ante doble-seleccion de una cotizacion
    (resuelto con `unique(quotation_id)` en `orden_abastecimiento`), items
    de cotizacion duplicados sin validar, totales sin redondeo explicito, y
    una race de estado stale en el efecto de React que carga el detalle de
    compra.
  - No se pudo verificar end-to-end en navegador durante la sesion que lo
    escribio (sin credenciales de BD/Supabase disponibles); backend y
    frontend verificados en verde (compile/tsc/build). La prueba real la
    hace el usuario en su entorno, mismo criterio que el Lote 4 (DEC-L012).

- **DEC-L012 — Alcance de Lote 4 (Catalogo de Productos con imagenes) +
  redisenio de UI a Tailwind v4 (2026-09-23).** El diseno de referencia
  (`docs_base/ERP_Logistico_Corporativo_WEB_V38_FINAL_MAESTRO_SKU`, modulo
  Producto_Imagen) modela `PRODUCTO_IMAGEN` con `DOCUMENTO` generico como
  repositorio logico de archivos (la BD nunca guarda el binario, solo la
  URL en storage externo). Se implemento tal cual: `Documento`/
  `ProductoImagen` (nuevas), `SupabaseStorageClient` sube el binario al
  Storage de Supabase via su API REST (no existe SDK Java oficial) y
  devuelve la URL publica que es lo unico que se persiste.
  - **Alcance acotado a una sola foto principal por producto** (aplica a
    todas sus variantes, `variant_id` null), reutilizando el indice parcial
    de unicidad ya definido en `V1__baseline_lote1.sql`. Galeria de multiples
    fotos por producto/variante queda fuera de este lote.
  - **El catalogo (`VarianteCatalogoDto`/`FamiliaDto`) solo expone Familia**,
    no Tipo de Producto ni Subfamilia. `producto.family_id` es una
    clasificacion real y poblada en los 725 SKU (a diferencia de
    `subfamily_id`, que es un placeholder "00 GENERAL" sin dato de origen,
    ver PD pendiente mas abajo), pero `product_type_id` tambien es real y no
    se mapeo. Limitacion conocida: distintos `family_id` pueden compartir
    nombre bajo tipos de producto distintos (ej. "ELECTRICO" existe en
    Accesorios, Equipos y Materiales), y el filtro de
    `BuscadorProductos`/`CatalogoProductos` dedupe por nombre de familia,
    por lo que hoy no se puede distinguir cual "ELECTRICO" es cual. Si se
    necesita filtrar por Tipo de Producto, hace falta mapear
    `product_type_id` y agregarlo a los DTO/UI — no incluido en este lote.
  - **Redisenio de UI a Tailwind v4**: se retira el CSS propio
    (`globals.css`) y se migra a Tailwind v4 + un design system propio
    (`components/ui/`). Sin cambio de alcance funcional; verificado por
    revision de codigo que cada bloque removido (banners de error/exito,
    estados de carga, guards de doble-submit) tiene un reemplazo
    equivalente. `RequerimientosNuevo.tsx` cambia de UX (de filas de items
    en blanco a un carrito) pero mantiene la misma validacion de negocio
    (items con cantidad > 0 antes de crear el requerimiento en BORRADOR).
  - **3 bugs reales corregidos en revision de codigo** (ver CHANGELOG
    0.6.0): guard de doble-submit roto en `Button.tsx`, path traversal en
    la extension de archivo de `subirImagenProducto`, y `image/svg+xml`
    aceptado como imagen valida (riesgo de XSS). No se pudo verificar el
    lote end-to-end en navegador durante la sesion que lo escribio (sin
    credenciales de BD/Supabase disponibles); la prueba real la hizo el
    usuario en su entorno despues, exitosa.

- **DEC-L011 — Alcance de Lote 3 (Verificacion de Stock): kardex real +
  ajuste manual, saldo calculado en Java (2026-09-21).** El diseno de
  referencia (`db_design_reference/migrations/0011_inventario_almacen.sql`,
  `0016_reserva_stock.sql`) modela un kardex completo (`ALMACEN`,
  `MOVIMIENTO_INVENTARIO`, `RESERVA_STOCK`, `fn_saldo_stock()`), pero no
  existe todavia recepcion fisica contra OC (Almacen completo es Fase 2,
  fuera de alcance — DEC-L004), asi que no habia forma de cargar stock
  inicial para probar el modulo. Se le presento al usuario la alternativa de
  no persistir kardex (verificacion "a ojo", sin base de datos real) y la de
  sembrar saldo ficticio por SQL; eligio construir el kardex real tal cual el
  diseno de referencia, mas un endpoint minimo (`POST /api/stock/ajustes`,
  LOGISTICA/ADMIN) que registra un movimiento `AJUSTE` por producto — no es
  carga masiva ni recepcion contra OC (eso sigue fuera de alcance), es la
  unica via de entrada de stock hasta que Almacen exista. Un almacen
  `CENTRAL` sembrado por empresa (`V5__stock.sql`), sin gestion de
  sub-almacenes en este lote.
  - `fn_saldo_stock()` del diseno de referencia NO se implementa como
    funcion SQL: el saldo disponible (kardex - reservas activas) se calcula
    en Java, en `StockService`, siguiendo el mismo patron que DEC-L008 (toda
    la logica de negocio vive en la capa de servicio de Spring, el resto del
    backend no usa funciones SQL para nada).
  - La reserva de stock (`RESERVA_STOCK`) creada al confirmar la
    verificacion es un registro contable de compromiso; el despacho fisico
    real (`status DESPACHADA`) es modulo Almacen, Fase 2, fuera de alcance.
  - `RequerimientoService`/`AprobacionService` no se tocaron: el modulo
    `stock` es autonomo (calcula su propio saldo, no depende de otros
    modulos de negocio) salvo reutilizar `RealtimeNotifier.decisionTomada()`
    (ya generico) para avisar al solicitante — no se agrego canal WebSocket
    nuevo.

- **DEC-L010 — Notificaciones en tiempo real via WebSockets/STOMP
  (2026-09-18).** Pedido explicito del usuario: la bandeja de Aprobaciones y
  el detalle de un requerimiento debian actualizarse solos (sin refrescar)
  apenas ocurre un evento relevante — el tiempo de respuesta importa. Se le
  presento Server-Sent Events como alternativa mas liviana (el caso es
  estrictamente 1-via, servidor -> navegador) pero el usuario eligio
  WebSockets/STOMP a sabiendas, para dejar el canal bidireccional listo a
  futuro. El clic de aprobar/rechazar (`POST /api/aprobaciones/{id}/decidir`)
  sigue siendo HTTP normal; WebSockets solo avisa a los DEMAS clientes
  conectados.
  - `WebSocketConfig` (endpoint `/ws`, broker simple `/topic` + `/queue`),
    autenticado en el handshake HTTP contra la misma cookie `auth_token`
    (`WebSocketAuthHandshakeInterceptor` + `JwtService` ya existentes) — no
    hay sesion de Spring Security en `/ws/**` (`WebSecurityCustomizer` lo
    ignora por completo). `StompChannelInterceptor` autoriza el SUBSCRIBE a
    `/topic/aprobaciones` solo a APROBADOR/ADMIN. `RealtimeNotifier`
    concentra los 2 eventos: `nuevaAprobacionPendiente` (broadcast) y
    `decisionTomada` (dirigido al dueno via `convertAndSendToUser`).
  - **Sin `.withSockJS()`**: esa combinacion con Tomcat 11.0.24/Spring
    Framework 7.0.9 (version de este proyecto) produce un
    `StackOverflowError` recursivo real (confirmado en pruebas). WebSocket
    nativo alcanza para navegadores modernos; si mas adelante hace falta
    soportar redes que bloqueen WS, reevaluar el fallback.
  - **Bug real encontrado y corregido en el camino**: `SpaController`
    (pensado para servir el SPA empaquetado en produccion, ver Dockerfile)
    tenia un regex catch-all (`/{path:...}/**`) que tambien matcheaba
    `/ws` y su propio destino de forward (`/index.html`), causando un
    forward recursivo infinito -> el mismo `StackOverflowError`. Se
    reemplazo por una lista explicita de rutas del SPA (login, inicio,
    requerimientos, requerimientos/nuevo, requerimientos/{id},
    aprobaciones) en vez de un regex abierto.

- **DEC-L009 — Lote 2: modulo Aprobaciones, sin verificacion de stock
  (2026-09-18).** El flujo completo de un requerimiento incluye aprobacion
  (rol APROBADOR) y verificacion de stock/ruta de abastecimiento (rol
  LOGISTICA) como pasos separados. Se construyo solo el primero en este
  lote porque el segundo requiere ALMACEN/MOVIMIENTO_INVENTARIO, hoy vacios
  (la recepcion fisica es Modulo Almacen, Fase 2, fuera de alcance) — sin
  datos de prueba sembrados, "stock disponible" mostraria honestamente 0
  para todo. Queda para un Lote 3 aparte.
  - Tabla `aprobacion` (`V4__aprobaciones.sql`, basada en
    `db_design_reference/migrations/0017_workflow_aprobacion.sql`), sin la
    tabla `NOTIFICACION` (envio de correo fuera de alcance, no hay SMTP
    configurado).
  - **Simplificacion**: `aprobacion.approval_rule_id` queda siempre `null`.
    `regla_aprobacion` no tiene filas sembradas (PD-04 sigue pendiente:
    rangos monetarios) y el requerimiento todavia no captura valor/moneda,
    asi que no hay como resolver una regla por monto. Cualquier
    `APROBADOR`/`ADMIN` puede decidir sobre cualquier requerimiento
    `PENDIENTE_APROBACION`.
  - Solo decisiones `APROBADO`/`RECHAZADO` (no `OBSERVADO`: no existe un
    estado equivalente en `requerimiento.status` todavia).
  - Autorizacion ampliada en `RequerimientoService.obtener()`: un
    `APROBADOR` ahora puede ver el detalle de un requerimiento que ya no
    esta en `BORRADOR` (antes de este lote, un APROBADOR no podia ver
    absolutamente nada — ni siquiera lo que le tocaba decidir).

- **DEC-L008 — Migracion de Next.js+Supabase a Spring Boot + React
  (2026-09-18).** Pedido explicito del usuario (no motivado por una
  limitacion tecnica del stack anterior, que ya estaba funcionando probado
  end-to-end). Reemplaza DEC-L001/DEC-L005 en la parte de "que framework
  usar" (el resto de esas decisiones, forma del flujo y descarte de
  Fleetbase, siguen vigentes). Decisiones tomadas con el usuario:
  1. **Base de datos**: se reusa el mismo proyecto Postgres de Supabase
     (`LOGISTICA-TDEM`), pero como Postgres puro via JDBC/JPA. **RLS
     desactivado** (`V2__disable_rls.sql`): las policies leian
     `auth.uid()`/`current_rol()`, un GUC que solo Supabase (PostgREST/
     GoTrue) inyecta a partir del JWT — una conexion JDBC directa no pasa
     por ese mecanismo. Toda la autorizacion por rol/ownership se
     reimplemento en la capa de servicio de Spring (ver
     `RequerimientoService`), incluida la regla de DEC-L007.
  2. **Autenticacion**: Spring Security + JWT propio en cookie httpOnly,
     reemplazando Supabase Auth. Se agrego `usuario.password_hash`
     (`V3__usuario_password_hash.sql`) porque las credenciales vivian en
     `auth.users` de Supabase, tabla que no migra.
  3. **Estructura**: monorepo (`backend/` Spring Boot + Maven, `frontend/`
     React + Vite + TS) dentro de `LOGISTICA-SYSTEM`. El codigo Next.js se
     elimino del repo (queda recuperable en el historial de git, commit
     `98e1635`). `supabase/migrations/` se renombro a
     `db_design_reference/migrations/` (ya no se usa el CLI de Supabase;
     las 33 migraciones originales de Jonathan quedan como referencia de
     diseno para los lotes 2+ aun no construidos).
  Verificado con el mismo criterio que el Lote 1 en Next.js: flujo completo
  en navegador (Playwright headless) login SOLICITANTE -> crear
  requerimiento con SKU real -> enviar a aprobacion -> logout -> login
  LOGISTICA -> verlo en bandeja, mas el caso de permiso cruzado de DEC-L007
  (un tercero no puede ver/enviar el requerimiento de otro).

- **DEC-L001 — Sin `server.ts`/socket.io en Fase 1.** El flujo Requerimiento
  -> Aprobacion -> Verificacion de stock -> Ruta -> Cotizacion -> Orden de
  Compra es secuencial por rol, no requiere push en vivo compartido entre
  varios usuarios viendo la misma pantalla a la vez (a diferencia de la
  bandeja de reclutamiento de RRHH-SYSTEM). Se usa Next.js estandar
  (`next build`/`next start`) sobre Render como Web Service. Si en Fase 2
  (Almacen, con tracking de picking en vivo) se necesita tiempo real, evaluar
  primero Supabase Realtime nativo antes de replicar un servidor custom.

- **DEC-L002 — Identidad: RRHH_SYNC vs MANUAL.** `USUARIO` es la tabla de
  identidad/autorizacion del ERP (login, rol, CECO visible) — NO es un
  mantenedor de personal. Para TDEM (y GOYDEL cuando tenga padron) se llena
  por sincronizacion de SOLO LECTURA contra una API nueva de RRHH-SYSTEM
  (`/api/integraciones/personal-activo`, protegida por header
  `X-Service-Token`), marcando `fuente='RRHH_SYNC'`. Mientras RRHH no cubra
  METRIN (ni GOYDEL), se permite alta manual acotada (`fuente='MANUAL'`,
  modulo `identidad-manual`), restringida a las empresas listadas en
  `CONFIG.IDENTIDAD.EMPRESAS_MANUAL_HABILITADAS`. El upsert por DNI hace que
  una fila `MANUAL` se convierta en `RRHH_SYNC` automaticamente, sin
  duplicarse, en cuanto RRHH incorpore esa empresa.

- **DEC-L003 — Caja Chica es una ruta, no un modulo aparte.** Dentro de un
  mismo REQUERIMIENTO, Logistica decide la ruta de abastecimiento
  (STOCK / CONVENIO / CAJA_CHICA / RFQ) segun disponibilidad, convenio
  vigente, urgencia y umbral configurable (`REGLA_CAJA_CHICA`). El modulo
  `caja-chica` solo referencia el aplicativo externo de Caja Chica
  (`CAJA_CHICA_REF`), nunca duplica su fondo/rendicion.

- **DEC-L004 — Alcance de Fase 1.** Requerimientos + Compras (incluida la
  ruta Caja Chica). Quedan fuera de esta fase, documentado solo como
  pendiente: Almacen (recepcion fisica, carga masiva, incidencias, salidas,
  Nota de Inventario, envios/tracking) y evaluacion/reevaluacion de
  Proveedores (Jonathan aun no tiene el TO-BE cerrado, 40% de avance).

- **DEC-L006 — `producto_variante.attribute_type` ampliado a `CLASE`/`CLASE + TALLA`.**
  Al aplicar el seed del catalogo contra la BD real (2026-09-17) se detecto que
  el Excel origen (`13_Catalogo_SKU`, columna `ATRIBUTO`) trae 3 valores
  distintos: `TALLA` (167 filas), `CLASE + TALLA` (8 filas) y `CLASE` (1
  fila) — el CHECK original de `0008_producto.sql` solo contemplaba
  `TALLA/COLOR/MODELO/OTRO`. Se amplio el constraint para incluir `CLASE` y
  `CLASE + TALLA` tal cual vienen del origen, en vez de remapearlos a `OTRO`
  (evita perdida de informacion). Pendiente de confirmar con Jonathan si las
  8 filas `CLASE + TALLA` deberian modelarse como dos variantes separadas
  (una fila por atributo) en vez de un tipo compuesto.

- **DEC-L007 — Bugs encontrados y corregidos en la prueba end-to-end del Lote 1 (2026-09-17).**
  Al probar el flujo real en navegador (login SOLICITANTE -> crear
  requerimiento -> enviar a aprobacion -> verlo en bandeja LOGISTICA) aparecieron
  2 bugs que el typecheck/build no detectan (dependen de datos/RLS reales):
  1. `src/services/auth.service.ts`: la consulta de roles usaba el alias
     `rol:rol_id(code)`, pero la columna real de `usuario_rol` es `role_id`
     (ver `0004_seguridad.sql`). Al no revisar el `error` de esa consulta, el
     embed fallaba en silencio y TODO usuario quedaba sin roles (bloqueaba
     cualquier verificacion de permiso). Corregido a `rol:role_id(code)`.
  2. `supabase/migrations/0033_rls_modulo_requerimientos.sql`: el `WITH CHECK`
     de `requerimiento_update` exigia `status = 'BORRADOR'` sobre la fila
     NUEVA, lo que hacia imposible para el dueno (SOLICITANTE) la transicion
     BORRADOR -> PENDIENTE_APROBACION (el propio "enviar a aprobacion" nunca
     podia completarse). Corregido: el dueno puede dejar la fila en
     `BORRADOR` o `PENDIENTE_APROBACION`; cualquier otro estado requiere
     LOGISTICA/ADMIN. Aplicado tanto en el archivo fuente como con un ALTER
     directo sobre la BD ya desplegada.

- **DEC-L005 — Fleetbase descartado.** Se evaluo adaptar el open-source
  Fleetbase (github.com/fleetbase/fleetbase): PHP/Laravel + Ember.js, licencia
  AGPL-3.0 (o USD 25k/año comercial), enfocado en despacho/fleet/ultima
  milla. No cubre requisiciones, RFQ, proveedores, almacen ni caja chica, y no
  comparte nada del stack elegido (Next.js + Supabase + Render). Se construye
  a medida replicando el patron ya probado en RRHH-SYSTEM.

## Pendientes de decision de negocio (con Jonathan/Gerencia)

Implementados como filas CONFIGURABLES en las tablas `REGLA_*` (nunca
hardcodeadas), a validar antes de marchar en produccion:

- PD-01 — Visibilidad de costo de compra para el rol COMPRAS/LOGISTICA.
- PD-02 — Umbral/obligatoriedad de guia de remision.
- PD-03 — Porcentaje de penalidad y cuando aplica.
- PD-04 — Rangos de aprobacion monetaria y excepciones por tipo de compra/proveedor.
- PD-05 — Umbral de alerta/aprobacion de Caja Chica (no usar el historico de
  S/ 4,900 como regla general).
- PD-06 — Familias/productos de alta rotacion habilitados para orden
  automatica via convenio.
- PD-07 — Vida util de EPP (pendiente con SSOMA).
- PD-08 — Si el solicitante externo tendra portal propio o solo tracking.
- Pendiente adicional (RESUELTO por inspeccion directa del Excel, confirmar
  igual con Jonathan): la hoja `13_Catalogo_SKU` solo tiene 2 niveles de
  clasificacion antes del producto (`COD_FAMILIA`/`FAMILIA_FINAL` y
  `COD_SUBFAMILIA`/`SUBFAMILIA_FINAL`), no 3. Se mapearon como
  `TIPO_PRODUCTO` y `FAMILIA_PRODUCTO` respectivamente (verificado: EP con
  subfamilias 01-04 = exactamente las 4 categorias EPP oficiales del
  informe). `SUBFAMILIA_PRODUCTO` (nivel 3 del diccionario, NOT NULL) no
  tiene dato de origen: se creo una subfamilia `00 GENERAL` de paso por cada
  familia (`scripts/seed_catalogo_sku.py`). Ajustar cuando Jonathan defina
  subfamilias reales. **Aclarado en DEC-L034**: en el Excel esos 2 niveles
  se llaman Familia y Subfamilia, y asi se muestran en la UI.
- El maestro origen NO trae UOM (confirmado en el Informe V38 Final, seccion
  6). Se sembro `unidad_medida.code = 'ND'` como placeholder explicito
  (`0007_maestros_clasificacion.sql`) y los 725 productos se cargan con esa
  unidad hasta completar el dato real.
