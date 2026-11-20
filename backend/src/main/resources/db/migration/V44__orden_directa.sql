-- DEC-L080: orden de compra directa, sin solicitud de cotizacion.
-- Logistica la emite con lineas de productos del catalogo y/o servicios (texto
-- libre, orden_item sin producto). Sale aprobada; CECO y requerimiento son
-- referencias opcionales. Una orden solo de servicios no pasa por el almacen:
-- se cierra al registrar su pago (estado CERRADA).

alter table ${schema}.orden_abastecimiento
  drop constraint if exists orden_abastecimiento_order_type_check;
alter table ${schema}.orden_abastecimiento
  add constraint orden_abastecimiento_order_type_check
  check (order_type in ('OC_NORMAL', 'OC_DIRECTA', 'PEDIDO_CONVENIO', 'OC_REG_CAJA_CHICA'));

alter table ${schema}.orden_abastecimiento alter column quotation_id drop not null;
alter table ${schema}.orden_abastecimiento
  add constraint ck_orden_cotizacion check (order_type <> 'OC_NORMAL' or quotation_id is not null);

alter table ${schema}.orden_abastecimiento
  add column ceco_id       uuid references ${schema}.ceco (ceco_id),
  add column request_id    uuid references ${schema}.requerimiento (request_id),
  add column services_only boolean not null default false;

alter table ${schema}.orden_abastecimiento drop constraint orden_abastecimiento_status_check;
alter table ${schema}.orden_abastecimiento add constraint orden_abastecimiento_status_check
  check (status in (
    'POR_APROBAR', 'EMITIDA', 'EN_ATENCION', 'RECEPCION_PARCIAL', 'RECEPCION_COMPLETA',
    'PEND_VERIFICACION', 'VERIFICADA', 'CERRADA', 'ANULADA'
  ));

create index idx_orden_abastecimiento_request on ${schema}.orden_abastecimiento (request_id)
  where request_id is not null;

comment on column ${schema}.orden_item.product_id is
  'Null = linea de servicio (texto libre) de una OC directa (DEC-L080): no entra al almacen.';
