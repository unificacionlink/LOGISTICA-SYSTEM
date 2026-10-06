-- 0031_seed_permisos.sql
-- Catalogo completo de PERMISO segun la hoja 07_Permisos de la Matriz de
-- Funcionalidades V38 Final, y su asignacion a ROL_PERMISO.
--
-- IMPORTANTE (ver docs/04_DECISIONES.md): la hoja 07_Permisos define 8 roles
-- objetivo (SOLICITANTE, LOGISTICA, COMPRAS, ALMACEN, PAGOS, APROBADOR,
-- GERENCIA, ADMIN_GERENCIA), pero el prototipo V38 navegable (hoja
-- 07_Perfiles_V38) solo implementa 3 (SOLICITANTE, LOGISTICA, ADMIN) y esta
-- migracion (0004_seguridad.sql) sembro 4 (se agrego APROBADOR). Esta
-- migracion siembra el catalogo COMPLETO de permisos (dato de referencia,
-- sin riesgo) y RECONCILIA la asignacion a los 4 roles implementados con la
-- siguiente equivalencia -- pendiente de validar con Jonathan/Gerencia:
--   COMPRAS       -> LOGISTICA  (no existe como rol separado en esta fase)
--   ALMACEN       -> LOGISTICA  (idem; el modulo Almacen fisico es Fase 2)
--   GERENCIA      -> APROBADOR  (mismo uso: aprobar/observar por regla)
--   ADMIN_GERENCIA -> ADMIN
--   PAGOS         -> (sin rol propio en este ERP: RN dice que Pagos es un
--                      aplicativo externo que MANTIENE el dominio; Compras/
--                      Logistica solo CONSULTA gestion_pago, nunca actualiza)

insert into public.permiso (code, module, description) values
  ('REQUERIMIENTO_CREAR', 'Requerimientos', 'Crea requerimiento de su empresa/CECO.'),
  ('REQUERIMIENTO_TRACKING_VER', 'Requerimientos', 'Ve el tracking de requerimientos.'),
  ('REQUERIMIENTO_APROBAR', 'Aprobaciones', 'Aprueba requerimiento segun regla.'),
  ('REQUERIMIENTO_OBSERVAR', 'Aprobaciones', 'Observa requerimiento con motivo obligatorio.'),
  ('COSTO_VENTA_VER', 'Costos', 'Ve el costo de venta aplicable.'),
  ('COSTO_COMPRA_VER', 'Costos', 'Ve el costo de compra (PD-01: pendiente para rol Compras).'),
  ('MARGEN_VER', 'Costos', 'Ve el margen aplicado.'),
  ('STOCK_VERIFICAR', 'Abastecimiento', 'Verifica stock y faltantes.'),
  ('CONSOLIDAR', 'Abastecimiento', 'Consolida necesidades de abastecimiento.'),
  ('RUTA_ABASTECIMIENTO_DECIDIR', 'Abastecimiento', 'Confirma la ruta STOCK/CONVENIO/RFQ/CAJA_CHICA.'),
  ('CONSULTA_PROVEEDOR_GESTIONAR', 'Cotizaciones', 'Consulta disponibilidad a proveedor.'),
  ('RFQ_GESTIONAR', 'Cotizaciones', 'Genera/envia RFQ.'),
  ('COTIZACION_REGISTRAR', 'Cotizaciones', 'Registra cotizacion manual.'),
  ('COTIZACION_OCR_PROCESAR', 'Cotizaciones', 'Procesa cotizacion por imagen/PDF (OCR).'),
  ('COTIZACION_OCR_CORREGIR', 'Cotizaciones', 'Corrige campos detectados por OCR antes de guardar.'),
  ('COMPRA_APROBAR', 'Compras', 'Aprueba compra/convenio/excepcion.'),
  ('PDF_DOCUMENTOS_COMPRAS_DESCARGAR', 'Documentos', 'Descarga RFQ/OC/OP/guia/tracking.'),
  ('PDF_REQUERIMIENTO_DESCARGAR', 'Documentos', 'Descarga documentos de su propio requerimiento.'),
  ('NOTIFICACION_VER', 'Notificaciones', 'Consulta trazabilidad de notificaciones.'),
  ('TRACKING_EXPORTAR', 'Requerimientos', 'Exporta tracking dentro de su alcance.'),
  ('PRODUCTO_SOLICITAR_ALTA', 'Productos', 'Solicita alta de producto/SKU nuevo (no lo crea).'),
  ('PRODUCTO_APROBAR_ALTA', 'Productos', 'Aprueba el alta/modificacion del maestro de producto.'),
  ('PRODUCTO_IMAGEN_PROPONER', 'Productos', 'Adjunta imagen en una solicitud de producto.'),
  ('PRODUCTO_IMAGEN_GESTIONAR', 'Productos', 'Valida/reemplaza/define la imagen principal del producto.'),
  ('CAJA_CHICA_REGULARIZAR', 'CajaChica', 'Registra/revisa regularizacion de Caja Chica.'),
  ('CAJA_CHICA_VER', 'CajaChica', 'Consulta y controla regularizaciones.'),
  ('CAJA_CHICA_RECEPCION', 'CajaChica', 'Recibe productos fisicos regularizados (Fase 2: Almacen).'),
  ('REGLA_CAJA_CHICA_GESTIONAR', 'Configuracion', 'Gestiona el lineamiento/umbral de Caja Chica.'),
  ('PAGO_ACTUALIZAR', 'Pagos', 'Mantiene el estado del pago (aplicativo externo de Pagos).'),
  ('RECEPCION_REGISTRAR', 'Almacen', 'Registra recepcion (Fase 2).'),
  ('RECEPCION_CARGA_MASIVA', 'Almacen', 'Carga masiva de recepciones (Fase 2).'),
  ('INCIDENCIA_RECEPCION_GESTIONAR', 'Almacen', 'Gestiona incidencias de recepcion (Fase 2).'),
  ('NOTA_INVENTARIO_GENERAR', 'Almacen', 'Genera Nota de Inventario desde una salida (Fase 2).'),
  ('NOTA_INVENTARIO_VER', 'Almacen', 'Consulta Notas de Inventario (Fase 2).'),
  ('ENVIO_GESTIONAR', 'Almacen', 'Gestiona envios/tracking fisico (Fase 2).'),
  ('ENVIO_RECEPCION_CONFIRMAR', 'Almacen', 'Confirma recepcion en destino (Fase 2).')
on conflict (code) do nothing;

-- SOLICITANTE
insert into public.rol_permiso (role_id, permission_id)
select r.role_id, p.permission_id
from public.rol r, public.permiso p
where r.code = 'SOLICITANTE'
  and p.code in (
    'REQUERIMIENTO_CREAR','REQUERIMIENTO_TRACKING_VER','COSTO_VENTA_VER',
    'PRODUCTO_SOLICITAR_ALTA','PRODUCTO_IMAGEN_PROPONER',
    'PDF_REQUERIMIENTO_DESCARGAR','TRACKING_EXPORTAR'
  )
on conflict (role_id, permission_id) do nothing;

-- LOGISTICA (incluye lo asignado a COMPRAS y ALMACEN en el origen -- ver nota arriba)
insert into public.rol_permiso (role_id, permission_id)
select r.role_id, p.permission_id
from public.rol r, public.permiso p
where r.code = 'LOGISTICA'
  and p.code in (
    'STOCK_VERIFICAR','CONSOLIDAR','RUTA_ABASTECIMIENTO_DECIDIR',
    'CONSULTA_PROVEEDOR_GESTIONAR','RFQ_GESTIONAR','COTIZACION_REGISTRAR',
    'COTIZACION_OCR_PROCESAR','COTIZACION_OCR_CORREGIR','COSTO_COMPRA_VER',
    'PDF_DOCUMENTOS_COMPRAS_DESCARGAR','NOTIFICACION_VER','TRACKING_EXPORTAR',
    'PRODUCTO_APROBAR_ALTA','PRODUCTO_IMAGEN_GESTIONAR','PRODUCTO_SOLICITAR_ALTA',
    'CAJA_CHICA_REGULARIZAR',
    -- Fase 2 (Almacen), sembrado como referencia; sin modulo funcional aun.
    'RECEPCION_REGISTRAR','RECEPCION_CARGA_MASIVA','INCIDENCIA_RECEPCION_GESTIONAR',
    'NOTA_INVENTARIO_VER','ENVIO_GESTIONAR'
  )
on conflict (role_id, permission_id) do nothing;

-- APROBADOR (incluye lo asignado a GERENCIA en el origen)
insert into public.rol_permiso (role_id, permission_id)
select r.role_id, p.permission_id
from public.rol r, public.permiso p
where r.code = 'APROBADOR'
  and p.code in (
    'REQUERIMIENTO_APROBAR','REQUERIMIENTO_OBSERVAR','COMPRA_APROBAR',
    'CAJA_CHICA_VER','REGLA_CAJA_CHICA_GESTIONAR'
  )
on conflict (role_id, permission_id) do nothing;

-- ADMIN (incluye lo asignado a ADMIN_GERENCIA en el origen + todo lo de LOGISTICA/APROBADOR)
insert into public.rol_permiso (role_id, permission_id)
select r.role_id, p.permission_id
from public.rol r
cross join public.permiso p
where r.code = 'ADMIN'
  and p.code not in (
    'PAGO_ACTUALIZAR',       -- Pagos es un aplicativo externo; ni ADMIN lo actualiza desde aqui
    'PRODUCTO_APROBAR_ALTA'  -- Explicito en el origen: ADMIN_GERENCIA = NO, "no se salta el flujo funcional de Logistica"
  )
on conflict (role_id, permission_id) do nothing;

comment on table public.rol_permiso is
  'Permisos asignados a cada rol. ADMIN recibe casi todo el catalogo salvo PAGO_ACTUALIZAR '
  '(aplicativo externo) y PRODUCTO_APROBAR_ALTA (RN explicita: ADMIN no se salta el flujo de Logistica).';
