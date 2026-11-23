# Changelog

## 0.66.1 — 2026-11-23

- Entrega directa (DEC-L084): "obra" pasa a "proyecto" en pantallas, PDF y avisos; la acción de la Bandeja `RECIBIR_EN_OBRA` pasa a `RECIBIR_EN_PROYECTO`.
- Administración › CECOs: filtro por empresa (Todas / TDEM / GOYDEL / METRIN, con cuántos CECOs tiene cada una).

## 0.66.0 — 2026-11-23

Entrega directa del proveedor al CECO (DEC-L084, ALM-04 de la V38.7).

- `V46__entrega_directa_ceco.sql`: `orden_abastecimiento.direct_delivery`; `recepcion.warehouse_id` opcional + `destination_ceco_id`, `conformity_url`, `document_id` (check: almacén o CECO); `recepcion_item.request_item_id` y `need_id`.
- `DestinosEntrega` (reparto de cada línea por necesidad/CECO) y `EntregaDirectaService`: `GET/POST /api/almacen/ordenes/{id}/entrega-directa` (multipart `datos` + `file`) y `GET /api/almacen/requerimientos/{id}/entregas-directas`. Guía obligatoria, constancia, incidencias; recalcula la atención y avisa al dueño (`AvisosPlataforma.entregaDirecta`); auditado `ENTREGA_DIRECTA`.
- Marca en `CondicionesOrdenRequest.entregaDirecta` y `CrearOrdenDirectaRequest.entregaDirecta`; `OrdenDto.entregaDirecta`; el PDF F-LOG-09 dice "Entrega directa en obra". `RecepcionService` rechaza las OC de entrega directa.
- Lo entregado directo cuenta en `AtencionCalculator`, `BandejaService` (+ acción `RECIBIR_EN_OBRA`), `ResumenSolicitanteService`, `PresupuestoService` (cumplimiento), `DespachoService`, `TrackingService` (`EntregaDto.orderId`), `DashboardService` y `DemandaService`.
- Frontend: `components/almacen/EntregaDirecta.tsx` (formulario por CECO, historial y tarjeta del REQ), opción en Condiciones de la OC y en la OC directa, "Recibir en obra" en Almacén y en la Bandeja, filas de entrega directa en el Tracking.
- Tests: `EntregaDirectaTest` (reparto por CECO, sobrante, validaciones, permisos del solicitante, guía única, incidencia, atención parcial/completa, sin kardex, Tracking; OC directa con CECO); `MigracionesFlywayTest` cuenta 46.

## 0.65.0 — 2026-11-23

Dashboard con pestañas Proceso, Demanda y Gasto (DEC-L083). Cierra el Lote 19.

- `FiltrosDashboard`: rol, período, empresa y familia (`t:`/`f:`) comunes a las pestañas.
- `DashboardService` (Proceso): `vencidos` y `cumplimiento` (a tiempo, desde stock / con compra); la entrega cuenta la caja chica del proyecto regularizada.
- `DemandaService` + `GET /api/dashboard/demanda` (familia, mes, productos, estado, matriz ABC, últimas 100 líneas) y `GET /api/dashboard/demanda/excel` (`DemandaExcel`).
- `GastoService` + `GET /api/dashboard/gasto`: OC aprobadas sin IGV por moneda, empresa, mes, CECO (reparto por necesidades del consolidado), familia y proveedor.
- Frontend: `Dashboard` con pestañas (`?tab=`) y filtros comunes; `components/dashboard/` (`PestanaProceso`, `PestanaDemanda`, `PestanaGasto`, `comunes`, `formato`). Etiquetas de estado del REQ en `components/formato.ts` (`ETIQUETA_ESTADO`). El comparativo de cotizaciones rotula la columna como Consolidado.
- Tests: `AlmacenFlujoTest` (a tiempo con compra, demanda, ABC 12 u. × S/ 5 = clase A, filtros de familia, Excel, gasto al CECO por el consolidado, permisos).

## 0.64.0 — 2026-11-20

Empresa del proveedor y elección de proveedores al pedir cotización (DEC-L081, DEC-L082).

- `V45__proveedor_empresa.sql`: `proveedor.company_id` (repetidos TDEM/GOYDEL → TDEM).
- `ProveedorDto`/`GuardarProveedorRequest.empresaCode`; la lista de Compras trae la empresa en la misma consulta (`@EntityGraph`). Panel de Proveedores con `empresaCode`.
- `AbastecimientoService.candidatos` + `POST /api/compras/consolidados/candidatos`; `prepararSolicitudes(..., supplierIds, ...)` restringe a los elegidos.
- Frontend: filtro por empresa en Proveedores (indicadores incluidos) y campo Empresa en la ficha; panel "Pedir cotización" (`PanelPedirCotizacion`) para marcar proveedores; paso del recorrido actualizado.
- Datos (2026-11-20): 22 contactos que eran números de contrato de alquiler vehicular pasan a "qué ofrece"; 77 proveedores clasificados en familias del catálogo y 55 críticos. Tras el deploy: `enlaces_producto.sql` (6 238 asociaciones proveedor-producto).
- Tests: `ProveedoresTest` (empresa por defecto y edición), `ConsolidacionComprasTest.logisticaEligeAQuienPedirCotizacion`; `MigracionesFlywayTest` cuenta 45.

## 0.63.0 — 2026-11-20

Orden de compra directa (DEC-L080).

- `V44__orden_directa.sql`: `OC_DIRECTA` en `order_type`, `quotation_id` nulo (check para `OC_NORMAL`), `ceco_id`, `request_id`, `services_only` y estado `CERRADA`.
- `OrdenDirectaService` + `POST /api/compras/ordenes/directa`: productos (variante activa) y servicios (texto), sale `EMITIDA` y aprobada, valida empresa/proveedor habilitado/moneda/CECO/REQ por código; auditado (`EMITIR_DIRECTA`).
- Las consultas de OC usan `left join fetch o.cotizacion` (si no, la OC directa no aparecía en las listas). `OrigenCompras` agrega el requerimiento de referencia de la cabecera.
- Recepción: solo líneas con producto; un producto sin consolidado ni ítem de REQ entra como stock libre. Orden solo de servicios: no se libera, no aparece por recibir y se cierra al pagarse.
- Corrección: el lugar de entrega de las condiciones de la OC admitía 250 caracteres y la columna 220.
- Frontend: `ComprasOrdenNueva` (`/compras/ordenes/nueva`), botón "Orden directa" en Órdenes de compra, ciclo de la OC sin liberación para servicios, estado "Cerrada".
- Tests: `OrdenDirectaTest` (producto + servicio, solo servicios, validaciones); `MigracionesFlywayTest` cuenta 44.

## 0.62.0 — 2026-11-20

Orden de compra con el formato F-LOG-09 (DEC-L079).

- `V43__orden_formato_flog09.sql`: `empresa.address/phone/logo_key/signature_manager_key/signature_buyer_key` (+ check del RUC, datos de TDEM); `orden_abastecimiento.tax_mode/delivery_term/additional_specs` (previas `NO_AFECTO`).
- `ImpuestoOrden` (MAS_IGV, IGV_INCLUIDO, NO_AFECTO): VALOR TOTAL, IGV y PRECIO TOTAL. La emisión aplica `MAS_IGV` y toma el plazo de la cotización.
- `OrdenCompraService.editarCondiciones` + `PUT /api/compras/ordenes/{id}/condiciones`: solo Logística/Admin y solo `POR_APROBAR`; auditado.
- PDF: `documentos/PdfOrdenCompra` (maqueta F-LOG-09) armado en `DocumentosService.orden` con datos de la empresa, contacto y cuentas del proveedor, logo y firmas del bucket privado (solo con la OC aprobada).
- `EmpresaAdminService` + `/api/admin/empresas` (listar, editar, subir/quitar/ver logo y firmas; PNG/JPG hasta 2 MB; auditado). Solo ADMIN.
- Frontend: tarjeta `CondicionesOrden` en el detalle de la OC (editable mientras espera aprobación) y pestaña Administración › Empresas (`PestanaEmpresas`).
- Tests: `ConsolidacionComprasTest` (IGV, condiciones, PDF por aprobar/aprobada, datos de empresa), `PdfOrdenCompraTest` (varias páginas, anulada); `ProveedoresTest` suma con IGV; `MigracionesFlywayTest` cuenta 43.

## 0.61.0 — 2026-11-20

Cuentas bancarias del proveedor (DEC-L078).

- `V42__proveedor_cuenta_bancaria.sql`: tabla `proveedor_cuenta_bancaria` con índice único por proveedor + banco + número.
- `CuentaBancariaService` (listar, crear, editar, quitar; una principal por proveedor; auditoría con número y CCI enmascarados) y endpoints en `ProveedorController`. Solo Logística y Admin.
- Frontend: pestaña "Cuentas bancarias" en la ficha del proveedor (`components/proveedores/PestanaCuentas`), con panel lateral para agregar o editar.
- Test `ProveedoresTest.cuentasBancariasSoloLogisticaYAuditoriaEnmascarada`; `MigracionesFlywayTest` cuenta 42.

## 0.60.0 — 2026-11-20

PYC trabaja sobre todos los CECOs de las 3 empresas (DEC-L077).

- `PresupuestoService`: sin filtro de ámbito para PYC en periodos, presupuestos y CECOs configurables; `exigirCecoEnAmbito` solo exige CECO activo para PYC; redactar y ver en cualquier CECO. Decidir sigue en el ámbito real.
- `PrecioPlazoService.empresas`: PYC ve y registra precios en las 3 empresas.
- Administración: la pestaña PYC muestra "Todos los CECOs"; la ficha indica que PYC ve los presupuestos de todos los CECOs y ya no marca "Sin CECOs" por eso.
- Tests: `PresupuestosTest` (periodo en un CECO de otra empresa sin asignación) y `PreciosPlazosTest` (3 empresas).

## 0.59.2 — 2026-11-20

Administración › Usuarios: cada usuario en una sola pestaña.

- `PestanaUsuarios`: las pestañas agrupan por **rol principal** (Administrador → Logística → Gerencia → PYC → Aprobador → Solicitante), así quien tiene varios roles no se repite en cada una y los conteos suman el total. Debajo del correo se indican sus otros roles ("+ Aprobador · Gerencia" o "+ todos los roles"); en "Todos", quien tiene los 6 roles muestra "Todos los roles".

## 0.59.1 — 2026-11-20

Ficha del usuario más clara sobre qué aprueba cada quien (sin cambiar la regla, DEC-L030).

- `CecosUsuario`: con Gerencia o PYC, junto a "CECOs de su ámbito" se indica para qué sirven según sus roles (pide · aprueba como Gerencia · presupuestos).
- `CecosAprobados`: la sección pasa a "Aprobador asignado de"; si está vacía y el usuario tiene Gerencia, aclara que igual aprueba los CECOs de su ámbito.

## 0.59.0 — 2026-11-19

Resumen del solicitante y avisos dentro de la plataforma (DEC-L076).

- `V41__notificacion.sql`: tabla `notificacion` (usuario, requerimiento, tipo, título, detalle, leída) con índices por usuario y no leídas.
- `modules/notificaciones/AvisosPlataforma` + `NotificacionController` (`GET /api/notificaciones`, `POST /{id}/leida`, `POST /leidas`). Avisos de aprobado, observado, rechazado, entrega parcial, atendido completo, despachado y recepción registrada, desde `AprobacionService`, `RecepcionDestinoService`, `DespachoService` y `CajaChicaService`; envío en vivo tras el commit (`RealtimeNotifier.notificacion`).
- `AvisosCorreo`: sin los correos al solicitante (`decision`, `despachoEnviado`, `recepcionRegistrada`).
- `ResumenSolicitanteService` + `GET /api/requerimientos/mi-resumen`.
- Frontend: campana `components/Notificaciones` en el menú y en la barra móvil; `Toast` muestra el aviso nuevo; `components/requerimientos/MiResumen` en el Inicio del solicitante; `RealtimeProvider` escucha `/user/queue/notificaciones` e invalida la campana, el resumen y la bandeja.
- Tests: `CorreosTest` y `RecepcionDestinoTest` comprueban los avisos (y que ya no salen esos correos) y el resumen; `MigracionesFlywayTest` cuenta 41.

## 0.58.0 — 2026-11-19

Nomenclatura de variantes por tipo de atributo (DEC-L075).

- `modules/maestro/NomenclaturaVariante`: código = prefijo del tipo + valor limpio (Talla sin prefijo, Clase `C`, Color `CL`, Modelo `M`, Otro `OT`), máximo 20 caracteres. `MaestroService` la usa al crear productos, agregar variantes y aprobar solicitudes; la solicitud la valida al enviarse.
- `CLASE` admitido como tipo de atributo. `V40__atributo_clase_en_solicitud.sql` amplía el check de `solicitud_maestro_producto.proposed_attribute_type`.
- Frontend: tipo "Clase" y vista previa del código de la variante en Solicitar alta de producto y en Nuevo producto (`atributos.codigoAtributo`).
- Tests: `NomenclaturaVarianteTest`, caso de colores en `MaestroProductosTest`; `MigracionesFlywayTest` cuenta 40 migraciones.

## 0.57.0 — 2026-11-19

Guardados más rápidos: altas sin SELECT previo, inserciones en lote y aprobación por conjuntos (DEC-L074).

- `repository/RepositorioBase` + `RepositorioBaseImpl` + `config/JpaConfig`: `insertar`/`insertarTodos` con `persist`; los 39 repositorios heredan de la base. Las altas de Almacén, Compras, Requerimientos y Stock usan `insertar`.
- `application.yml`: `hibernate.jdbc.batch_size: 50`, `order_inserts`, `order_updates`; Hikari `reWriteBatchedInserts=true`; `DB_POOL_SIZE` por defecto 10. Comentarios de BD/Storage actualizados a DEC-L072.
- `StockService.reservarDisponible`: saldos de todos los SKU del REQ en una consulta y reservas en lote.
- `AbastecimientoService.generarNecesidades`/`agregarAConsolidados`: consolidados por lote (alta `on conflict`, un `for update` ordenado, totales con `batchUpdate`) y necesidades con `insertarTodos`.
- `AuthService.me`: una consulta, roles desde `AccesoVigente`, sin transacción.
- `FichaProveedorService.ficha`: reutiliza la selección ya leída para las advertencias.
- Test `StockCentralTest.aprobarDiezItemsReservaYConsolidaPorLotes`.

## 0.56.0 — 2026-11-19

Pantallas más rápidas y fluidas: memoria de la pestaña y guardado sin bloqueo visible (DEC-L073).

- `api/cache.ts`: store por clave con versión y suscriptores (`revalidar`, `fijar`, `invalidar` por familia, `precargarDatos`, tope de 60 claves). `enCache`, `invalidar`, `luegoInvalidar` y `vaciarCache` conservan su uso.
- `api/useDatos.ts`: hook `useDatos(clave, pedir, { fresco, conservar })` → `{ datos, cargando, actualizando, previo, error, recargar, mutar }`. Muestra lo último al instante y vuelve a pedir por detrás.
- Migradas: bandeja y tablero de Requerimientos, panel lateral, ficha del REQ y su tracking (mismas claves: el panel y la ficha comparten datos), Aprobaciones, Dashboard (conserva lo anterior al cambiar filtro), Compras (sus 6 listas), Órdenes, Almacén (4 pestañas), Stock, Proveedores y las pestañas Documentos, Compras y Desempeño de la ficha, Presupuestos, Administración y padrón.
- Tras guardar ya no se vuelve a los esqueletos (ficha y panel del REQ): se invalida y se actualiza por detrás.
- `CapaCarga`: bloqueo transparente al guardar; tarjeta solo pasado 1 s y sin `backdrop-blur`.
- `ProtectedLayout`: `Suspense` fijo con la `key` adentro; precarga del código de cualquier enlace interno (`precargarPagina` reconoce las rutas de detalle). Entrada de pantalla de 220 a 120 ms.
- `RealtimeProvider`: los avisos invalidan las listas afectadas; valor del contexto memorizado.
- Catálogo: los precios ya no se piden dos veces al abrir. `useDeferredValue` en los filtros locales de Stock, Catálogo, Proveedores, Padrón y Usuarios.

## 0.55.1 — 2026-11-19

Logística pasa a su propio proyecto Supabase, `LOGISTICA-TDEM` (DEC-L072). Sin cambios de código.

- BD: esquema `logistica` y rol `logistica_app` en `uwaenymwvixuocwbavnz` (pooler `aws-0-us-east-2`, puerto 5432), copiados con `pg_dump`/`pg_restore` desde RR-HH (85 tablas, Flyway V39).
- Storage: buckets `logistica-productos` (público) y `logistica-privado` (privado) con llaves S3 propias; 19 archivos copiados y `documento.file_url` reescrito a la URL pública nueva.
- Render: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` y `STORAGE_PUBLIC_URL` apuntan al proyecto nuevo.
- RR-HH: `logistica_app` en `nologin`; su retiro definitivo queda para dentro de 2 semanas (`docs/06_PLAN_MUDANZA_SUPABASE.md`).

## 0.55.0 — 2026-11-18

Módulo de Proveedores integral, Lote 19a (DEC-L069, DEC-L070, DEC-L071).

- `V37__proveedor_ciclo_de_vida.sql`: ficha ampliada (nombre comercial, contacto y cargo, teléfono, web, dirección, ciudad, lo que ofrece, condición de pago, días de crédito, plazo de entrega, garantía), `status` (BORRADOR / PENDIENTE_REVISION / HABILITADO / OBSERVADO / RECHAZADO, los existentes quedan HABILITADO) con motivo y fechas, check `not active or status = 'HABILITADO'`, `proveedor_categoria` e índice de `log_cambios` por entidad.
- `V38__evaluacion_proveedor.sql`: `plantilla_evaluacion` / `plantilla_criterio` (F-LOG-03 v7 y F-LOG-07 v2 sembradas con sus pesos y respuestas) y `evaluacion_proveedor` / `evaluacion_criterio` con la copia de cada criterio.
- `V39__documentos_proveedor.sql`: `requisito_documento_proveedor` (6 sembrados), `proveedor_documento` con emisión, vencimiento y anulación, y tipo `DOC_PROVEEDOR`.
- `storage`: `StorageClient.subirPrivado` / `leerPrivado` sobre el bucket privado `STORAGE_PRIVATE_BUCKET` (por defecto `logistica-privado`).
- `modules/proveedores`: `ProveedorService` (alta como borrador, edición auditada, categorías y productos), `CicloProveedorService` (enviar, decidir con advertencias, reabrir, activar/desactivar), `FichaProveedorService` (panel con indicadores en una consulta, ficha, advertencias, historial desde `log_cambios`), `EvaluacionProveedorService`, `PlantillaEvaluacionService`, `DocumentoProveedorService`, `HistorialComercialService` (compras y desempeño leídos de Compras/Almacén), `CalificacionProveedor` y `VigenciaEvaluacion`. `GET /api/proveedores` no cambia (solo activos).
- Frontend: Proveedores con indicadores que filtran (Registrados, Habilitados, En revisión, Por reevaluar), buscador por RUC, razón social, producto o categoría y filtros por estado; ficha `/proveedores/:id` con pestañas General, Documentos, Selección, Evaluaciones, Compras y Desempeño; paneles laterales para editar, categorías, productos, decidir, evaluar y configurar. Primitivas nuevas `ui/PanelLateral` y `ui/Pestanas`. `/proveedores/:id/editar` abre la ficha con el editor.
- `ProveedoresTest` (ciclo completo, permisos, migrados habilitados, nota F-LOG-03, copia de pesos, anulación, vigencias, documentos privados, historial comercial, categorías y productos).

## 0.54.0 — 2026-11-18

Prioridad con tiempo de atención y fecha tentativa automática (DEC-L068).

- `V36__prioridad_sla.sql`: prioridades `URGENTE` / `APROVISIONAMIENTO` / `IMPLEMENTACION` (Crítica y Urgente → Urgente, Normal → Aprovisionamiento, por defecto Aprovisionamiento) y `requerimiento.submitted_at` (relleno con la primera aprobación de cada REQ enviado).
- `modules/requerimientos/SlaAtencion`: plazos (2, 10 y 20 días hábiles), días hábiles sin fines de semana ni feriados nacionales de Perú (Pascua por Meeus/Jones/Butcher) y etiquetas.
- `RequerimientoService`: `crear`, `actualizar` y `enviar` ignoran la fecha del cliente y guardan hoy + plazo (sin adelantarse al mes de un borrador de presupuesto); `enviar` fija `submitted_at` antes del control presupuestal. `GET /api/requerimientos/sla` da los plazos y la fecha tentativa de hoy.
- `RequerimientoDto` suma `submittedAt` y `sla {diasHabiles, transcurridos}`.
- Orden de bandeja y de reparto de recepciones, correos, PDF y Excel con las prioridades nuevas.
- Frontend: la prioridad muestra su plazo; "Fecha requerida" pasa a **Atención tentativa** de solo lectura con la fecha de solicitud y el botón **Tiempo de atención** (`components/requerimientos/TiempoAtencion`); el panel del REQ muestra "Día hábil X de N" o "Fuera de plazo". `components/requerimientos/prioridades.ts` concentra etiquetas, colores y plazos.
- `SlaAtencionTest` (fines de semana, feriados, Semana Santa) y `ObservarProgramarTest` (fecha pasada del cliente ignorada, envío y SLA).

## 0.53.0 — 2026-11-17

Administración › Usuarios ordenada por rol.

- Pestañas por rol con conteo (Todos, Solicitantes, Aprobadores, Gerencia, PYC / Presupuestos, Logística, Administradores y Sin rol si hay alguno); la columna cambia según el rol: CECOs para pedir, CECOs que aprueba o roles.
- Filtros de empresa y estado de acceso; inactivos al final.
- La ficha se edita en un panel lateral (`components/admin/PanelUsuario`): roles como botones, CECOs para pedir y que aprueba, acceso (contraseña, activar) y datos, con el resultado de cada cambio en el mismo panel.
- `components/admin/PestanaUsuarios` y `NuevoUsuario` salen de `pages/Administracion.tsx` (de 789 a 308 líneas).

## 0.52.0 — 2026-11-17

Tablero Kanban de Requerimientos para Logística (DEC-L067).

- `BandejaService.tablero` y `GET /api/requerimientos/tablero?empresa&q` (LOGISTICA/ADMIN): columnas por etapa con total y tarjetas (hasta 60; Atendido, las 20 más recientes).
- La consulta de la bandeja suma el paso de la NI sin enviar (`PICKING`/`EMBALAJE`/`ENVIO`) y las unidades observadas en destino; cada fila trae `tablero {etapa, preparacion, observado}`.
- Frontend: selector Lista / Tablero en Requerimientos (`?modo=tablero`); `components/requerimientos/TableroRequerimientos` con columnas desplazables, tarjetas con prioridad, CECO, solicitante, avance, motivos (acción propia, espera, diferencias) y vencimiento; clic abre el panel del requerimiento.
- `BandejaTest` cubre la etapa en cada paso del flujo, totales por columna y el acceso solo de Logística.

## 0.51.0 — 2026-11-17

Correos automáticos (DEC-L066, RF-007, RF-063, ADM-03 de la V38.7).

- `V35__correo_saliente.sql`: bandeja de salida con evento, destinatario, asunto, HTML, estado (PENDIENTE/ENVIADO/ERROR/OMITIDO), intentos, próximo intento, último error, id del proveedor y `dedupe_key`.
- Nuevo módulo `modules/notificaciones`: `AvisosCorreo` (encola en la transacción de la acción), `PlantillasCorreo` (HTML en tablas con texto escapado), `EnvioCorreo` + `ResendEnvioCorreo` (API HTTPS de Resend con `Idempotency-Key`), `DespachadorCorreos` (cada 30 s, `skip locked`, reintentos 1/5/15/60 min y ERROR al 5.º intento en el registro de errores) y `RecordatorioAprobaciones` (8:00 lunes a sábado).
- Disparadores: `RequerimientoService.enviar`, `AprobacionService.decidir`, `DespachoService.registrarEnvio` y `RecepcionDestinoService.registrar`.
- `config/UrlPublica`: base de los enlaces, compartida con el enlace del proveedor.
- Variables nuevas: `RESEND_API_KEY`, `CORREO_FROM`, `CORREO_FROM_NAME`, `CORREO_INTERVALO_MS`, `CORREO_RECORDATORIO_CRON`.
- Frontend: tras iniciar sesión se vuelve a la ruta pedida (enlace del correo), solo rutas internas.
- `CorreosTest` (destinatarios, escape, rollback, envío, reintentos y ERROR, recordatorio sin repetir); `RecepcionDestinoTest` cubre los correos de despacho y recepción; `MigracionesFlywayTest` pasa a 35 migraciones.

## 0.50.0 — 2026-11-17

Padrón: estado Cesado y eliminación definitiva por ADMIN (DEC-L065).

- `PersonalAdminDto` trae `estado` (ACTIVO / INACTIVO / CESADO) y `eliminable`.
- `PadronPersonalService.eliminar` y `POST /api/admin/personal/{id}/eliminar` (`MotivoRequest`): solo ADMIN, con motivo, sin cuenta vinculada y, si viene de RR.HH., solo cesada; auditoría `ELIMINAR` con los datos y el motivo.
- Frontend: filtro Cesados, insignia "Cesado desde…" (RR.HH.) junto a "Inactivo desde…" (ADMIN) y acción Eliminar con motivo en el menú de la fila.
- `PersonalManualTest` (eliminar con motivo, sin cuenta, solo ADMIN, auditoría) e `IdentidadRrhhTest` (estados y no se elimina a quien sigue vigente en RR.HH. o tiene cuenta).

## 0.49.0 — 2026-11-17

Padrón de personal como CRUD sin borrado (DEC-L064).

- `V34__personal_inactivacion.sql`: `personal.inactivated_at` y `inactivated_by` (check `active = inactivated_at is null`); los inactivos previos toman `updated_at`.
- Nuevo `PadronPersonalService` (solo ADMIN): `POST /api/admin/personal` (alta en empresas manuales), `PUT /api/admin/personal/{id}` (edición de personas MANUAL, refleja la cuenta MANUAL) y `PATCH /api/admin/personal/{id}/activo` (inactivar/reactivar, con auditoría `CREAR`/`EDITAR`/`INACTIVAR`/`REACTIVAR` y la cuenta vinculada). `GET /api/admin/personal` trae también a los inactivos con fecha y quién los inactivó.
- `PersonalSyncService`: no reactiva a quien inactivó un ADMIN ni su cuenta, y deja la fecha del cese. `PersonalManualService`: la carga Excel registra fecha y ADMIN al inactivar.
- Frontend: Administración › Personal con filtro Activos / sin cuenta / con cuenta / Inactivos, "Agregar persona" (`components/admin/FormPersona`), menú por fila Editar datos · Inactivar · Reactivar con confirmación; la carga por Excel pasa al menú "⋯".
- `PersonalManualTest.crudDelPadronSinBorrar` e `IdentidadRrhhTest` (sync respeta la inactivación del ADMIN, cese de RR.HH. no se reactiva a mano); `MigracionesFlywayTest` pasa a 34 migraciones.

## 0.48.0 — 2026-11-16

Lote 18b: recepción en destino con diferencias (DEC-L063, ALM-03 de la V38.7).

- `V33__recepcion_destino_diferencias.sql`: `salida_item.dest_received/accepted/observed/returned_qty` (check recibido = aceptado + observado + devuelto); envío `RECEPCION_PARCIAL`/`RECEPCIONADO_OBSERVADO` y `conformity_status`; NI `RECEPCION_PARCIAL`; tablas `recepcion_destino(_item)` y `resolucion_destino`; `nota_inventario.packing_evidence_url`. Lo recepcionado antes queda aceptado y conforme.
- Nuevos `RecepcionDestinoService` y `DiferenciasDestinoService`; endpoints `POST /api/almacen/envios/{id}/recepcion` (multipart: `datos` + NI firmada opcional), `POST /api/almacen/envios/{id}/resolucion`, `GET /api/almacen/diferencias` y `POST /api/almacen/devoluciones/{id}/recibir`. Se quita `POST /envios/{id}/confirmar-recepcion`.
- Operan LOGISTICA, GERENCIA (en su ámbito) y ADMIN; `DespachoService.obtenerNota` abre a GERENCIA y trae recepciones, resoluciones y lo aceptado/observado/devuelto por producto.
- Lo entregado pasa a ser lo aceptado en destino en `AtencionCalculator`, Bandeja, Tracking, Dashboard y cumplimiento de presupuesto; la Bandeja y el Dashboard cuentan `RECEPCION_PARCIAL` como pendiente de recibir.
- Embalaje con enlace de evidencia (`POST /notas/{id}/embalaje` en JSON `{bultos, evidencia}`).
- Stock: columna Cuarentena (devuelto desde destino, no disponible).
- Frontend: `components/almacen/RecepcionDestinoPanel` en el detalle de la NI (cantidades por producto, resultado, nota firmada o enlace, observado por resolver con subsanar o devolver, historial); pestaña Almacén › Diferencias (`components/almacen/DiferenciasAlmacen`); embalaje con enlace.
- `RecepcionDestinoTest` (parcial, no conforme, ámbito de GERENCIA, token, subsanar, devolver, cuarentena); `AlmacenFlujoTest` recibe con la API nueva; `MigracionLote16Test` cubre V33; `MigracionesFlywayTest` pasa a 33 migraciones.

## 0.47.0 — 2026-11-16

Lote 18a: picking y embalaje antes del envío (DEC-L062, ALM-02 de la V38.7).

- `V32__ni_picking_embalaje.sql`: `nota_inventario.picked_at/by`, `packed_at/by`, `packing_document_id` y check de embalar solo lo piqueado; documento `EVIDENCIA_EMBALAJE`; las NI existentes quedan piqueadas y embaladas.
- Nuevo `PreparacionNotaService` y endpoints `POST /api/almacen/notas/{id}/picking` y `POST /api/almacen/notas/{id}/embalaje` (multipart: `bultos` + `file`), con lock de la NI y auditoría `PICKING`/`EMBALAJE`. `DespachoService.registrarEnvio` exige la NI embalada; el detalle trae `evidenciaEmbalaje`.
- `ArchivosAlmacen`: validación de formato y subida al Storage compartida por adjuntos del envío y evidencia del embalaje; el archivo se borra si la transacción se revierte.
- Frontend: `components/almacen/PreparacionNota` en el detalle de la NI (Picking · Embalaje · Envío con `aria-current="step"`, una acción por paso, formulario de bultos + foto/PDF con etiquetas asociadas y error anunciado); el formulario de envío aparece recién con la NI embalada; Almacén › Notas muestra "Por piquear / Por embalar / Por enviar". `CampoNumero` acepta `ref`.
- `AlmacenFlujoTest` cubre el orden, validaciones, corrección del embalaje y bloqueo tras el envío; `MigracionLote16Test` cubre V32 sobre NI previas; `MigracionesFlywayTest` pasa a 32 migraciones.

## 0.46.0 — 2026-11-16

Lote 17: orden de compra con aprobación, pago y liberación (DEC-L061).

- `V31__oc_aprobacion_pago_liberacion.sql`: estado `POR_APROBAR` (default), `approved_at/by`, `payment_status` (PENDIENTE/ANTICIPO/PAGADO) con `payment_proof_document_id`, `paid_at/by`, `released_at/by`, `release_note`; checks de constancia y de liberar solo lo aprobado; documento `CONSTANCIA_PAGO`; las OC existentes quedan aprobadas y liberadas.
- `AbastecimientoService.emitirOrden` deja la OC por aprobar con su `aprobacion` (`COMPRA`) y avisa por `/topic/ordenes-compra`.
- Nuevo `OrdenCompraService` y endpoints `GET /api/compras/ordenes/por-aprobar`, `POST /api/compras/ordenes/{id}/decision`, `/pago` y `/liberar`. Deciden GERENCIA/LOGISTICA (ADMIN de respaldo), nunca quien emitió; rechazar anula y devuelve los consolidados a cotización (`ComprasService.anularInterna`). Anular se bloquea con pago o anticipo registrado.
- `FlujoOrden`: recepción manual y carga masiva solo de OC liberadas; `OrdenDto` trae aprobación, pago, liberación, emisor y `bloqueoRecepcion`.
- `AprobacionService.decidir` rechaza aprobaciones que no son de requerimiento. GERENCIA puede leer el detalle y el PDF de una OC (el PDF muestra el pago).
- Bandeja: acciones `APROBAR_OC` y `LIBERAR_OC`; dashboard cuenta las OC por aprobar como en curso; tracking con aprobación, rechazo, pago y liberación.
- Frontend: `components/compras/CicloOrden` en el detalle de la OC (franja Aprobación · Pago · Liberación y una acción principal por paso; pago con constancia; anular pasa al menú "⋯"); columna Pago y "Por liberar" en Órdenes de compra; sección "Órdenes de compra" en Aprobaciones (ahora también para Logística); Almacén › Recibir y Recepción muestran el motivo de bloqueo; aviso en vivo de OC nueva.
- `OrdenCompraAprobacionTest` (flujo completo y rechazo); `MigracionLote16Test` cubre V31 sobre OC previas y `MigracionesFlywayTest` pasa a 31 migraciones; `BandejaTest` y `ConsolidacionComprasTest` aprueban y liberan antes de recibir.

## 0.45.0 — 2026-11-13

Registro de la plataforma para el administrador (DEC-L060).

- `V30__registro_plataforma.sql`: `registro_actividad`, `registro_error` e índices por fecha (también en `log_cambios` y `movimiento_inventario`).
- Backend `modules/registro`: `RegistroPlataforma` (cola y escritura por lotes, purga diaria), `RegistroActividadFilter` (POST/PUT/PATCH/DELETE bajo `/api`), `RegistroService` y `RegistroController` (`/api/admin/registro/{resumen,actividad,errores,cambios,movimientos}` solo ADMIN; `POST /api/registro/errores` para la pantalla).
- `GlobalExceptionHandler` registra errores del servidor y el motivo de los rechazos; `AuthController` marca el ingreso (correo intentado, nunca la clave); `LimiteSolicitudes.ip` pasa a público para reutilizarlo.
- Frontend: pestaña Administración › Registro (`components/admin/PestanaRegistro`) con Operaciones, Ingresos, Cambios, Errores y Movimientos, indicadores de 24 h, periodo, búsqueda, filtro por usuario desde el detalle y "Cargar más".
- `registro/reporteErrores` y `components/PantallaError` (envuelve la app en `main.tsx`): los errores de la pantalla se reportan y una pantalla que falla muestra "Recargar" en vez de quedar en blanco.
- `RegistroPlataformaTest` (ingreso fallido y correcto, operación, error de pantalla, solo ADMIN) y `MigracionesFlywayTest` a 30 migraciones.

## 0.44.0 — 2026-11-13

El solicitante ya no ve cuánto stock hay, solo si hay (DEC-L059). Y la solicitud de alta de producto vuelve a guardarse.

- Corrección: "¿No encuentras el producto? Solicita su alta" era un `<form>` dentro del `<form>` del requerimiento; "Enviar a Logística" también disparaba el envío del requerimiento (se abría "Confirmar requerimiento" o salía "Agrega al menos un producto…") y la solicitud nunca llegaba a Catálogo › Solicitudes de alta (en producción no había ninguna). Ahora es un grupo sin `<form>`, con botón propio, Enter que envía solo la solicitud y validación a mano; `SelectorFamilia`/`SelectorUnidad` aceptan `requerido={false}` para no bloquear con su `required` nativo el envío del requerimiento.
- `GET /api/requerimientos/stock-disponible`: la cantidad solo para LOGISTICA/ADMIN; el resto recibe `1` = con stock. `StockCentralTest` cubre ambos casos.
- `StockDisponible` (buscador y "Tu pedido"): "Con stock" / "Sin stock" para quien no es Logística; se oculta "N se comprarán".
- `ConfirmarRequerimiento`: prop `conCantidades`; sin ella, indicadores Productos / Con stock / Caja chica y cada línea con "Con stock" / "Sin stock".
- Tutorial del solicitante: "Verás si hay stock en el almacén".

## 0.43.0 — 2026-11-13

Compras · Consolidados en dos columnas, con el detalle de la fila al pasar el mouse (DEC-L058).

- Nuevo `components/ui/FilaConDetalle`: fila de tabla cuya tarjeta de detalle sale junto al cursor desde cualquier parte de la fila (sin clics, con tope de renglones, se cierra al salir o hacer scroll). Reemplaza a `DetalleAlPasar` (v0.42.0, por celda), que se elimina.
- "Listos para orden de compra" (izquierda, 2/5) y "Consolidados" (derecha, 3/5) lado a lado desde `lg`; en pantallas angostas uno debajo del otro. "Listos para orden de compra" se muestra siempre, con estado vacío.
- Listos para OC: Proveedor (con empresa y n.° de productos) · Total · Emitir OC.
- Consolidados: Producto (enlace a su ficha, con código) · Total · Cotizado · Estado. Origen, ofertas, adjudicación y proveedores pasan a la tarjeta; se quitan las columnas Origen y Proveedores y el enlace "Ver".

## 0.42.0 — 2026-11-13

Compras · Consolidados más ordenado (DEC-L058).

- Nuevo `components/ui/DetalleAlPasar`: resumen en la celda y tarjeta flotante con el detalle (mouse, foco o toque).
- "Listos para orden de compra" pasa a tabla: proveedor, empresa, productos (detalle: N° de cotización, condición de pago y cada línea con cantidad e importe), total y "Emitir OC".
- "Consolidados" con título propio y acciones arriba, luego las pestañas de empresa. Filas en una línea: producto con su código al lado; origen como CECO o "N requerimientos" (detalle: cada CECO, REQ y cantidad); cotizado (detalle: ofertas, adjudicación y proveedores habilitados). Se quita la columna Proveedores y las sublíneas de "ofertas" y proveedor adjudicado.

## 0.41.3 — 2026-11-13

Una sola línea en Solicitudes de cotización (DEC-L057).

- Lista de Solicitudes: el código de la RFQ y la empresa van en la misma línea (antes, uno debajo del otro).
- `PageHeader`: el subtítulo va en una línea y se recorta con "…" (con `title`), en vez de partirse al llegar a 65 caracteres; en la RFQ que cotiza varios requerimientos ya no baja de línea.
- Detalle de la RFQ: el contacto del proveedor y el producto al registrar la cotización se recortan con "…".

## 0.41.2 — 2026-11-13

- Detalle de la RFQ: se quita "Abrir correo" (borrador `mailto:` armado en la pantalla) y su texto `mensajeSolicitud`. La solicitud se hace llegar al proveedor con el PDF con QR y el enlace para cotizar en línea (DEC-L052); queda "Confirmar envío" con el canal Correo / Otro para registrarla como enviada.

## 0.41.1 — 2026-11-13

Una sola línea en todas las pantallas (DEC-L057).

- `globals.css`: `white-space: nowrap` en `.inline-flex:has(> svg)`, `[role="tab"]`, `[role="menuitem"]` y `.cabecera-tabla th`.
- `Button`: `whitespace-nowrap` y `shrink-0`.
- `.tabla` en Cumplimiento (2 tablas), Detalle de presupuesto y Comparativo.
- `EditorCotizacion`: el nombre del producto en una línea con "…" (antes `line-clamp-2`).
- El sistema no envía mensajes: el tutorial, la ayuda de "Pedir cotización" y Novedades 0.25.0 ya no dicen "enviada por correo o WhatsApp".
- WhatsApp no va en esta versión: el detalle de la RFQ quita "Abrir WhatsApp" y el canal WhatsApp (queda "Abrir correo", que abre un borrador en el correo del usuario, y los canales Correo / Otro). El backend sigue aceptando `WHATSAPP` para lo ya registrado.

## 0.41.0 — 2026-11-13

Recorrido guiado para usuarios nuevos (DEC-L055).

- **V29**: tabla `usuario_tutorial` (usuario + recorrido, COMPLETADO u
  OMITIDO).
- **Auth**: `UsuarioDto.tutorialesVistos`; `PUT /api/auth/tutoriales/{codigo}`
  (`TutorialRequest`, `AuthService.marcarTutorial`, upsert del propio
  usuario).
- **Frontend**: `components/tutorial/recorridos.ts` (recorridos `solicitante`
  y `logistica`, `iniciarRecorrido`) y `RecorridoGuiado.tsx` (capa con foco,
  globo, teclado, tarjeta de bienvenida en el primer ingreso). Anclas
  `data-tutorial` en el menú, Requerimientos, Nuevo requerimiento, Compras y
  Almacén. "Ver tutorial" en el pie del menú y "Tutorial · …" en la búsqueda
  rápida. `.sidebar[data-recorrido]` despliega el menú durante un paso.
- **Pruebas**: `TutorialUsuarioTest`; `MigracionesFlywayTest`: 29.
- **Confirmar requerimiento**: "Solicitar" (o "Revisar y enviar" al editar)
  abre `ConfirmarRequerimiento`: resumen (empresa, CECO, prioridad,
  propósito, fechas), indicadores de productos / del almacén / por comprar /
  caja chica (estimados con el stock libre) y la lista de productos; la
  casilla "Enviar a aprobación ahora" (marcada) guarda y envía en un paso
  (`crear`/`actualizar` + `enviar`). La excepción presupuestal (DEC-L038) se
  justifica en la misma ventana; si ya quedó guardado, cerrar lleva a su
  detalle (no se crea otro).
- **Densidad 85% en computadora (DEC-L056)**: `html { font-size: 85% }` desde
  64rem; 59 clases con `px` fijos (texto, padding, posiciones del menú) pasan
  a `rem`; riel del menú lateral en `rem`.

## 0.40.0 — 2026-11-13

Enlace del proveedor: unidad, foto y pantalla ordenada (DEC-L052).

- `CotizacionPublicaDto.Linea` trae `unidadNombre` (null si la unidad es
  `ND`) e `imagenUrl` (foto principal del producto, una sola consulta:
  `ProductoImagenRepository.findPrincipalesActivasDeProductos`).
- `CotizarProveedor`: encabezado de documento (proveedor, emision, responder
  hasta, lugar de entrega), condiciones primero, tabla de productos con N°,
  foto (`Miniatura grande`), cantidad con su unidad ("10 millares"), precio
  "por millar, sin IGV", subtotal y total; "Unidad por definir" en ambar si
  es `ND`. Enter pasa al siguiente precio. En el celular cada producto se
  apila.
- Pruebas: `EnlaceCotizacionTest` verifica el nombre de la unidad y que `ND`
  no se muestre como unidad.

## 0.39.0 — 2026-11-12

Caja chica del proyecto por producto y cantidad (DEC-L054).

- **V28**: `requerimiento_item.petty_cash_qty` (0..qty_requested; los REQ que
  ya la sugerian para todo quedan con todo por caja chica).
  `requerimiento.petty_cash_suggested` queda como "algun producto con
  unidades por caja chica".
- `ItemRequerimientoRequest.cantidadCajaChica` y
  `RequerimientoItemDto.cantidadCajaChica`; no puede superar lo pedido.
- Al aprobar: `StockService.reservarDisponible` aparta solo la parte normal;
  `generarNecesidades` crea una necesidad PENDIENTE_RUTA `SOLICITANTE` por las
  unidades de caja chica y la parte normal sigue stock / reglas / consolidado.
- **Dividir** (`POST /api/compras/necesidades/{id}/dividir`): separa unidades
  de una necesidad por decidir o en caja chica sin compras en otra con el
  mismo motivo; auditado (`DIVIDIR`). Lo comprado por caja chica se reparte
  entre las partes del item al listar.
- Frontend: "Por caja chica del proyecto [n]" en cada producto del borrador,
  nota si hay alguno, etiqueta "Caja chica: n" en el detalle y "Dividir…" en
  Necesidades.
- Pruebas: `CajaChicaTest.unaParteDelProductoPorCajaChicaYElRestoNormal`;
  `MigracionesFlywayTest`: 28.

## 0.38.1 — 2026-11-12

Caja chica sugerida con stock en el almacen (DEC-L054).

- Si el solicitante sugiere la caja chica, la aprobacion **no aparta stock**
  (`StockService.reservarDisponible`): todo lo pedido llega a Necesidades.
- Nueva ruta `STOCK` en `decidirRuta` (no es estado): reserva del almacen
  central lo disponible (`StockService.reservarParaItem`); si cubre todo, la
  necesidad se elimina; si no, lo que falta sigue por decidir. Auditado
  (`NECESIDAD_COMPRA` / `USAR_STOCK`).
- `NecesidadDto.stockDisponible` en una consulta (`StockService.disponibles`);
  Necesidades muestra "Almacen: N disp. · Usar".
- Prueba `CajaChicaTest.conStockLogisticaDecideSiUsaElAlmacen`.

## 0.38.0 — 2026-11-12

Caja chica del proyecto (DEC-L054).

- **V27**: `requerimiento.petty_cash_suggested/petty_cash_suggestion_note`,
  motivo `SOLICITANTE` en `necesidad_compra.suggested_reason` y
  `caja_chica_ref.route_reason_code`, `caja_chica_ref.destination`
  (`PROYECTO`/`ALMACEN`; las existentes quedan `ALMACEN`).
- **Requerimientos**: `CrearRequerimientoRequest.cajaChicaSugerida/cajaChicaNota`;
  `RequerimientoDto` los devuelve. `CajaChicaService.sugeridor` da
  `SOLICITANTE` si el REQ lo sugiere.
- **Caja chica**: `GET /api/caja-chica/requerimientos/{id}` (formulario:
  autorizado y pendiente por item + compras), `POST .../comprobante`,
  `POST .../{id}` (registrar, dueno o Logistica), `PUT /api/caja-chica/{id}`
  (corregir observada). Sin kardex ni reserva; comprobante obligatorio;
  auditado. `regularizar` recalcula la atencion (`AtencionCalculator` publico
  suma lo regularizado del proyecto).
- **Necesidades**: `NecesidadDto.notaSolicitante`; `decidirRuta` a
  CAJA_CHICA avisa al solicitante (`RealtimeNotifier.cajaChica`).
- **Bandeja**: el dueno ve `REGISTRAR_CAJA_CHICA` y `CORREGIR_CAJA_CHICA`
  (en "Pendientes mios"); Logistica `REGULARIZAR_CAJA_CHICA` y espera la
  compra del proyecto. Lo regularizado cuenta como entregado.
- **Tracking**: "Comprado con caja chica del proyecto" y "Comprobante
  verificado".
- **Frontend**: casilla de sugerencia en el nuevo REQ, pantalla
  `/requerimientos/:id/caja-chica` (registrar y corregir), aviso en el
  detalle del REQ, nota del solicitante en Necesidades, "Comprobante correcto"
  en Compras > Caja chica, toasts en vivo.
- **Pruebas**: `CajaChicaTest` reescrito (sugerir, autorizar, registrar sin
  stock, duplicado, observar, corregir, regularizar parcial y completa,
  tracking, auditoria). `MigracionesFlywayTest`: 27.

## 0.37.1 — 2026-11-12

Reemplazar o descartar una cotizacion que ya gano productos.

- **Reemplazo** (`ComparativoService.registrar`): lo adjudicado a la cotizacion
  reemplazada pasa a la linea del mismo consolidado en la nueva; si la nueva no
  lo cotiza, queda en cotizacion. Ya no responde "Tiene productos adjudicados".
  Una cotizacion con OC (`SELECCIONADA`) no se reemplaza.
- **Descartar** (`ComprasService.descartarCotizacion`): libera sus adjudicados
  sin OC (vuelven a `EN_COTIZACION`, auditado `DESADJUDICAR`) en vez de
  rechazar. El reenvio por enlace del proveedor sigue rechazandose si su
  cotizacion ya gano productos.
- Frontend: la confirmacion de descartar avisa cuantos productos quedan sin
  adjudicar; la RFQ ahora tambien confirma antes de descartar.

## 0.37.0 — 2026-11-12

Equivalencias por proveedor y revision asistida (DEC-L053).

- **V26**: tabla `proveedor_equivalencia` (unica por proveedor + descripcion
  normalizada) y `cotizacion_item.supplier_code/supplier_description`.
- **Parser**: `lineasConPrecio`, `clave`, `unitarioDeLinea`, `ruc`;
  `ItemBuscado` con equivalencias; cada sugerencia trae `indiceLinea` y
  `origen` (SKU > EQUIVALENCIA > NOMBRE). `OcrCotizacionDto.lineas`.
- **Comparativo**: `leer` acepta `supplierId` y busca con lo aprendido;
  `OcrComparativoDto.coincidencias` y `lineas`. `registrar` guarda la linea
  confirmada en `cotizacion_item` y hace upsert de la equivalencia (auditado
  si cambia de producto).
- **Proveedores**: `GET /api/proveedores/{id}/equivalencias` y `DELETE
  /api/proveedores/equivalencias/{id}` (`EquivalenciaService`).
- **OC**: `OrdenItemDto.referenciaProveedor` (una consulta) en pantalla y PDF.
- **Frontend**: `LineaDocumento` en `EditorCotizacion` (Cambiar / Elegir
  linea, chip "Reconocido"), `unitarioDeLinea` en `compras/formato.ts`,
  `EquivalenciasProveedor` plegable en la ficha del proveedor.
- **Pruebas**: `CotizacionOcrParserTest` (codigo y descripcion aprendidos,
  tabla partida, prioridad del SKU, clave) y `ComparativoCotizacionesTest`
  (aprende, reconoce, reemplaza y audita, Ref. proveedor en la OC, quitar).

## 0.36.0 — 2026-11-12

Enlace del proveedor para cotizar en linea (DEC-L052).

- **V25**: tabla `rfq_enlace` (hash SHA-256 del token, vence, anulado,
  abierto, respondido) y `cotizacion.source` admite `ENLACE`.
- **Backend**: `EnlaceCotizacionService` (generar, estado, anular, URL para el
  PDF; publico: obtener y enviar), `EnlacePublicoController`
  (`/api/publico/cotizacion/{token}`, GET y POST multipart `datos` + `archivo`),
  `LimiteSolicitudes` (429 por IP en `/api/publico/**`), `GET/POST/DELETE
  /api/compras/rfq/{id}/enlace`. `ComprasService`: `registrarCotizacionInterna`,
  `descartarCotizacionInterna` y `guardarDocumento` sin rol para el enlace.
  `CotizacionDto` trae `source`. PDF de la RFQ con QR (`?enlace=<token>`,
  zxing core 3.5.3). `app.public-url` (`APP_PUBLIC_URL`, por defecto el primer
  origen de CORS) y `app.publico.limite-por-minuto`.
- **Frontend**: pagina publica `CotizarProveedor` (`/cotizar/:token`, fuera
  del layout), `EnlaceProveedor` en el detalle de la RFQ (generar, copiar,
  PDF con enlace, estado, generar otro, anular), el enlace va en el mensaje de
  correo/WhatsApp, chip "Por enlace" en la RFQ y el comparativo, aviso en
  tiempo real. `http.delete`.
- **Pruebas**: `EnlaceCotizacionTest` (hash, PDF, sin datos internos, envio y
  reemplazo, anular/regenerar/vencer/cerrar, ruta sin sesion y tope por IP).

## 0.35.0 — 2026-11-12

- **Nuevo requerimiento**: `StockDisponible` recibe `enPedido` y muestra lo
  que queda (disponible - cantidad en el pedido), en el buscador y en cada
  linea del pedido; ambar con "Stock 0" si el pedido se lleva todo. Solo
  visual: la reserva sigue haciendose al aprobar (DEC-L050).

## 0.34.0 — 2026-11-11

Rendimiento (DEC-L051).

- **Cache de `/assets`**: los archivos con hash se guardan un ano en el
  navegador (`CacheAssetsConfig`); antes se volvian a bajar en cada visita.
  `/favicon.svg` pasa a `permitAll()` (en el login devolvia 401).
- **Carga por pantalla**: `paginas.ts` con `React.lazy` por ruta, `Suspense`
  con esqueleto en `ProtectedLayout` y precarga al pasar por el menu. JS
  inicial 731 KB -> 327 KB (brotli 150 -> 87 KB). Novedades bajo demanda
  (`VentanaNovedades`, version por `define` en `vite.config.ts`).
- **Renders**: `FilaRequerimiento` memorizada con `onAbrir(id)` estable (el
  buscador y el panel ya no redibujan toda la bandeja); `FilaBorrador` en la
  edicion del presupuesto (escribir una cantidad redibuja solo su fila).
- **Llamadas**: cache en la pestana para catalogo, clasificacion, proveedores
  y monedas (`api/cache.ts`), vaciada al entrar/salir. Stock aparte y fresco:
  `GET /api/requerimientos/stock-disponible`; `VarianteCatalogoDto` ya no trae
  `stockDisponible`. Compras recarga solo las listas que cambia cada accion.
- **Consultas**: sin N+1 en recepcion de OC, estado de atencion, generacion de
  necesidades, detalle de RFQ, registro de Caja Chica, preparar solicitudes,
  comparativo, anular OC (`bloquearVarios`), proveedores por producto y CECOs
  del aprobador.
- **V24**: indices en `rfq_item(rfq_id)`, `cotizacion_item(rfq_item_id)`,
  `salida_item(issue_id)`, `recepcion_item(receipt_id)`,
  `requerimiento(ceco_id, required_date)`, `rfq(company_id, supplier_id)`,
  `cotizacion(supplier_id)`, `orden_abastecimiento(supplier_id)` y
  `usuario_ceco(ceco_id)`.

## 0.33.1 — 2026-11-09

- **Fotos de producto livianas**: las miniaturas (40px) se piden al servicio
  de transformacion de Supabase a 80x80 en WebP (`components/imagenes.ts`,
  `urlImagen`) con `loading="lazy"`; el visor, a 1344px. Las 5 fotos actuales
  pasan de 4,9 MB a 7 KB en la lista, con cache publica de 1 ano (las
  originales venian con `no-cache`). Si la transformacion falla, se usa la
  original.
- **Fuentes**: sin Geist 700 (no se usa) y Newsreader con tamano optico fijo
  36 (titulos): 180 KB -> 108 KB. La hoja de Google Fonts ya no bloquea el
  primer dibujo (preload + `display=swap`).
- **STOMP** se descarga solo con sesion (import dinamico en
  `RealtimeProvider`): 22,6 KB menos en el login.
- Favicon con la marca (241 B; era el logo de la plantilla de Vite, 9,5 KB) y
  se elimina `public/icons.svg`, sobrante de la plantilla y sin uso.

## 0.33.0 — 2026-11-09

- **Stock visible al pedir** (DEC-L050): el catalogo del formulario de
  requerimiento trae `stockDisponible` (fisico - reservado del almacen
  central, `StockService.disponiblesCentral`). El buscador de productos y el
  pedido muestran "Stock N" o "Sin stock", y el pedido avisa cuantas unidades
  se compraran si se pide mas de lo disponible. Prueba en `StockCentralTest`.

## 0.32.1 — 2026-11-09

- **Fix**: el menu "⋯" (`MenuAcciones`) y las listas de los buscadores
  quedaban cortados cuando estaban al fondo de una tarjeta o tabla (por su
  `overflow`). Nuevo `components/ui/Flotante`: portal a `body` con
  `position: fixed`, sigue al ancla en scroll/resize, se abre hacia arriba si
  abajo no cabe y va sobre los dialogos (z-70). Lo usan `MenuAcciones`,
  `ProveedorCombobox`, `ProductoCombobox`, `presupuestos/BuscadorSku`,
  `admin/CecosUsuario` y `admin/CecosAprobados`.

## 0.32.0 — 2026-11-09

- **Requerimientos desde el presupuesto** (DEC-L049): al aprobar un
  presupuesto se crea un REQ en BORRADOR por mes con cantidades, a nombre del
  solicitante del presupuesto (`BorradoresPresupuestoService`). Re-aprobar
  rehace los que siguen en borrador. Migracion V23 (`requerimiento.budget_id`,
  `budget_version_id`, `budget_month` + indice unico). Productos con
  tallas/colores quedan sin variante: la edicion los muestra para elegirla y
  enviar lo exige. El detalle del presupuesto lista los REQ generados.
- **Excel del presupuesto**: `GET /api/presupuestos/{id}/excel`
  (`PresupuestoExcel`): metadatos y SKU x 6 meses con total, precio e importe.
  Boton Excel en la tarjeta Partidas.
- **Cantidad cotizada en consolidados**: `ConsolidadoDto.cantidadCotizada`
  (max de `cotizacion_item.qty` no descartadas); columna "Cotizado" en
  Compras › Consolidados (en todos los estados, con el numero de ofertas) y
  columna "Cant. cotizada" en el detalle del consolidado.
- `RequerimientoService.generarRequestCode` usa la fecha de Lima.
- Pruebas: `PresupuestosTest.aprobarGeneraBorradoresPorMesYExcel`,
  `ConsolidacionComprasTest` (cantidad cotizada), `MigracionesFlywayTest` (23).

## 0.31.1 — 2026-11-06

- **Fix**: los campos numericos aceptaban decimales en cantidades y textos de
  largo ilimitado (incluido "e" y signos). Nuevo `components/ui/CampoNumero`
  (con `utils/numeros.limpiarNumero`): cantidades, bultos, dias y anio solo
  enteros; precios/costos hasta 4 decimales, montos 2, peso 3, tipo de cambio
  3; tope de digitos en todos. Reemplaza los 22 `input type="number"`.
- Backend: `@Digits` en los DTO de cantidades (enteras, hasta 6 digitos;
  requerimiento, caja chica y cotizacion desde 1) y montos; inventario por
  Excel y recepcion masiva rechazan cantidades con decimales. Pruebas:
  `ValidacionCantidadesTest` y `StockCentralTest`.

## 0.31.0 — 2026-11-06

- **Bandeja operativa de Requerimientos** (DEC-L048, referencia ERPNext
  Material Request): vistas Requieren atencion / Compras / Despacho /
  Seguimiento / Cerrados (quien pide: Pendientes mios / En proceso /
  Cerrados), siguiente accion por fila, barra de avance (entregado, en
  camino, listo, en compra), busqueda y paginacion en el servidor y panel
  lateral con el avance por producto, compras, entregas e historial.
- API: `GET /api/requerimientos/bandeja` y `GET /api/requerimientos/{id}/avance`
  (`BandejaService`, solo lectura). Pruebas: `BandejaTest`.
- Almacen › Despacho: las cantidades por item salen en 3 consultas por REQ
  (antes 3 por item).
- Frontend sin avisos de lint: utilidades separadas de los componentes
  (`components/formato.ts`, `compras/formato.ts`, `familia.ts`,
  `maestro/atributos.ts`), proveedores de contexto en su propio archivo
  (`auth/AuthProvider.tsx`, `ui/ConfirmacionProvider.tsx`,
  `realtime/useRealtime.ts`) y cargas iniciales sin `setState` sincrono en
  efectos.

## 0.30.0 — 2026-11-06

- **Comparativo de cotizaciones** (DEC-L047): en Compras > Consolidados se
  eligen productos de una empresa y "Comparar cotizaciones" abre
  `/compras/comparativo`. Hasta 3 cotizaciones (PDF o foto) se leen por OCR
  en paralelo: proveedor por RUC y precio unitario por nombre de producto.
  Se revisan en un panel, se comparan lado a lado con el mejor precio
  marcado (tipo de cambio si hay soles y dolares), se adjudica todo de una
  vez y se emiten las OC ahi mismo.
- API: `GET /api/compras/comparativo`, `POST /comparativo/ocr`,
  `/comparativo/cotizaciones` y `/comparativo/adjudicar`.
  `CotizacionOcrParser.parsearItems` busca por nombre cuando el proveedor no
  usa nuestros codigos. Pruebas: `ComparativoCotizacionesTest`.
- **Bandeja de Requerimientos** (DEC-L046): selector de empresa con conteos,
  vistas Abiertos/Atendidos/Rechazados/Todos, grupos por etapa, orden por
  urgencia, vencimientos visibles, filtros Vencidos/Urgentes y busqueda por
  producto. El listado suma `itemsCount` y `resumenItems`.
- Compras > Consolidados: una empresa a la vez; "Preparar solicitudes" pasa
  a "Pedir cotizacion".
- **Novedades** (DEC-L040): sale del menu (no es un modulo) y pasa a un pie
  discreto "vX · Novedades" al final de cada pantalla; las versiones ya no
  muestran fecha (`VersionApp.fecha` eliminado).

## 0.29.0 — 2026-11-05

- **Stock central del grupo** (DEC-L045, Flyway V22): un solo almacen (el de
  TDEM) para TDEM, GOYDEL y METRIN en reservas, recepciones, despachos y caja
  chica. Los centrales vacios de GOYDEL y METRIN se desactivan.
- **Inventario por Excel**: plantilla con todos los SKU; se escribe el stock
  contado y el minimo; las diferencias se ajustan como una carga `INV-n`.
  Validar / aplicar todo o nada, auditado.
- **Stock minimo y alertas** por SKU; **kardex** por SKU con documento,
  motivo y usuario.
- Pantalla de Stock nueva: todo el catalogo, filtros con conteos (con stock,
  bajo minimo, sin stock), familia, buscador y panel lateral por SKU.
- **Ajuste manual** con ingreso/salida y motivo obligatorio, sin empresa.
- API: `GET /api/stock/saldos` ya no recibe empresa; nuevos
  `/variantes/{id}/kardex`, `/variantes/{id}/minimo` y `/carga/*`.
  Pruebas: `StockCentralTest`.

## 0.28.2 — 2026-11-05

- **Fotos reemplazadas se borran** (DEC-L044): al cambiar la foto de un
  producto se eliminan el registro anterior y su archivo del Storage, despues
  de confirmar en la BD. `StorageClient` suma `borrar` y `listar`.
- **Limpieza nocturna de fotos** (`LimpiezaFotos`, 03:30 Lima): borra fotos
  inactivas que queden en la BD y archivos de fotos de producto sin uso (mas
  de 24 h). Pruebas en `MaestroProductosTest`.

## 0.28.1 — 2026-11-05

- **Fix**: cambiar la foto de un producto que ya tenia una fallaba con
  "Ocurrio un conflicto al guardar" (`uq_producto_imagen_principal`).
  Hibernate ejecuta los INSERT antes que los UPDATE: la foto nueva entraba
  con la anterior aun activa. Se hace `flush` tras desactivar la anterior.
  Afectaba a "Cambiar foto", la camara y la carga masiva con reemplazo.
  Prueba nueva en `MaestroProductosTest`.
- **Fix (celular)**: en el Catalogo, el precio y los botones se montaban sobre
  el nombre del producto. En pantallas angostas pasan a una segunda linea.

## 0.28.0 — 2026-11-05

- **Carga masiva de fotos** en Catalogo de productos (menu "⋯" de la
  cabecera, LOGISTICA/ADMIN, DEC-L043). Se arrastra la carpeta o se eligen
  las fotos; cada archivo se reconoce por su nombre (SKU base o codigo de
  variante, misma convencion que `scripts/cargar_imagenes.py`). Revision
  previa: lista, sin producto, repetida, ya tiene foto (opcion "Reemplazar
  fotos existentes"). El navegador reduce cada foto (lado mayor 1400 px, JPEG
  85 %) y sube 4 a la vez por el endpoint existente; progreso, detener y
  reintentar fallidas. `http.postFormEnLote` sube sin la capa bloqueante.
- **Camara en el celular**: boton junto a "Subir foto" que abre la camara
  trasera (`capture="environment"`). La subida individual tambien reduce la
  foto antes de enviarla.

## 0.27.0 — 2026-11-05

- **Lenguaje visual v2** (DEC-L042), tomando como referencia Linear, Stripe
  Dashboard y Attio. Se aplica desde las piezas comunes, asi llega a todas
  las pantallas:
  - Encabezados de tabla y rotulos en oracion (sin versalitas), clase
    compartida `cabecera-tabla` en vez de la cadena repetida en 20 archivos.
  - Estados (`Badge`): punto de color + texto, esquinas suaves y borde
    interior, sin mayusculas.
  - Botones de alto fijo (32/36 px) alineados con los campos; sombras
    tenidas del azul de la marca (`shadow-superficie`, `shadow-boton`).
  - Campos con foco azul de marca y estado deshabilitado; `Alert` con
    `role` accesible; titulos de pagina mas compactos.
  - Requerimientos: columnas por empresa con cabecera clara y punto de color
    (antes una barra azul oscura).
- **Busqueda rapida (Ctrl+K)**: ir a cualquier pantalla, pestana o accion
  permitida para el rol, desde el teclado o el boton "Buscar" del menu. El
  menu vive en `components/navegacion.ts` y lo comparten ambos.

## 0.26.0 — 2026-11-05

- **Padron de personal** (DEC-L041, Flyway V21): tabla `personal` por
  empresa, separada de las cuentas. Administracion → **Personal** con una
  lista por empresa, buscador y filtro con/sin cuenta.
- **Sync RR.HH.** trae a todo el personal activo (con o sin correo) y ya no
  crea cuentas; vincula las existentes. Escritura por lotes.
- **Dar acceso**: correo + roles desde la ficha de la persona; crea la cuenta
  con su CECO y devuelve la contrasena temporal. Auditado.
- **Carga por Excel** de GOYDEL/METRIN movida a la pestana Personal; llena el
  padron, correo opcional, sin columna "Rol inicial".

## 0.25.1 — 2026-11-05

- **Fix (produccion)**: el frontend publicado intentaba conectarse a
  `localhost:8080` porque el build de Render no tiene `VITE_API_URL`
  (`.env.local` no se versiona) y el login fallaba con "No se pudo conectar
  con el servidor". Sin esa variable, en produccion se usa el mismo dominio
  de la pagina para la API y los avisos en vivo (`wss://`). La URL base vive
  solo en `api/http.ts`.

## 0.25.0 — 2026-11-05

- **Lote 16 — Necesidad, consolidación y RFQ multiproducto** (DEC-L039,
  Flyway V20). Reemplaza la RFQ por requerimiento y la RFQ automática al
  proveedor habitual.
- **Al aprobar**, el faltante de cada ítem es una necesidad de compra que
  entra sola al consolidado de su SKU, sumando requerimientos de distintos
  CECOs. Si la regla de Caja Chica aplica (mismo día, bajo monto), espera
  que Logística elija la ruta.
- **Compras** con pestañas Necesidades, Consolidados, Solicitudes y Caja
  chica. Cada consolidado muestra el CECO y el REQ de origen de cada parte.
- **Preparar solicitudes**: una RFQ por empresa y proveedor con varios
  productos, a todos los proveedores habilitados. Envío por correo o
  WhatsApp con confirmación.
- **Comparativo y adjudicación por producto** (proveedores distintos por
  SKU) y **OC por proveedor** con todo lo adjudicado a su cotización.
- **Recepción**: lo recibido se reparte entre los requerimientos del
  consolidado (primero los críticos y urgentes).
- **Anular un consolidado** (proveedor falló) devuelve sus faltantes a
  decidir ruta con sugerencia de Caja Chica.
- **Catálogo**: "Proveedores" en el menú del producto (varios por SKU)
  reemplaza al proveedor habitual.
- Caja Chica por requerimiento en `/compras/requerimientos/{id}/caja-chica`
  (reemplaza "Gestionar compra").
- Dashboard: comparativo y ahorro por consolidado.
- **Novedades** (DEC-L040): botón en el menú con la versión actual y los
  cambios de cada versión en lenguaje de usuario (`frontend/src/novedades.ts`),
  con un punto mientras no se vea la última.
- Tests: `ConsolidacionComprasTest` (reemplaza `AtencionAutomaticaTest`) y
  `MigracionLote16Test` (V20 sobre datos previos).

## 0.24.0 — 2026-11-02

- **Lote 15 — Control presupuestal y cumplimiento** (DEC-L038, Flyway V19).
- **Control por periodo**: PYC lo activa en el menú de cada periodo
  presupuestal (columna "Control").
- **Al enviar un requerimiento** con control activo se valida cada SKU
  contra el presupuesto aprobado del CECO y semestre. Si excede, no está
  presupuestado o no hay presupuesto aprobado, se pide una justificación y
  queda como excepción. El detalle del REQ muestra el resultado.
- **Excepciones presupuestales** en el detalle del presupuesto (PYC,
  Gerencia, Logística, Admin).
- **Pestaña Cumplimiento**: programado, real (recepcionado en destino),
  pendiente, saldo y disponible por SKU y por familia, a precio de venta o
  de compra. Excel de cumplimiento exclusivo de PYC.
- API: `PUT /api/presupuestos/periodos/{id}/control`,
  `GET /api/presupuestos/{id}/cumplimiento` (+ `/excel`);
  `POST /api/requerimientos/{id}/enviar` acepta `justificacionPresupuestal`.
- Test: `ControlPresupuestalTest`.

## 0.23.1 — 2026-11-02

- **Aprobador del CECO desde Usuarios** (DEC-L037): la ficha de un
  Aprobador o Gerencia tiene "CECOs que aprueba", con aviso si el CECO
  tenia otro aprobador. La pestaña CECOs solo lo muestra.
  API `PUT /api/admin/usuarios/{id}/cecos-aprobados` (reemplaza
  `PUT /api/admin/cecos/{id}/aprobador`).
- Fix: dejar un CECO sin aprobador ya no falla.
- PYC y Gerencia ven "CECOs de su ámbito" en su ficha.

## 0.23.0 — 2026-11-02

- **Lote 14 — Presupuestos** (DEC-L036, Flyway V18). Nueva pantalla
  Presupuestos (Solicitudes): listado por año y estado, con "Por decidir"
  para el aprobador.
- **Periodos presupuestales** por CECO y semestre: PYC los habilita y
  cierra (menú de la pantalla).
- **Presupuesto SKU x 6 meses**: programación editable en tabla con
  buscador de SKU y precio de venta vigente; emitir, aprobar u observar,
  modificar (revisión) y anular con motivo.
- **Precios congelados al aprobar** (venta + última compra en PEN) en
  versiones; la revisión sigue rigiendo con la última aprobada.
- API `/api/presupuestos` (periodos, listado, detalle, catálogo, líneas,
  emitir, decidir, modificar, anular).
- Test: `PresupuestosTest`.

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
