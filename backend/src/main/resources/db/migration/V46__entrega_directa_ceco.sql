-- ALM-04 de la V38.7: entrega directa del proveedor al CECO (DEC-L084).
-- La OC marcada direct_delivery no pasa por el almacen central: cada recepcion
-- se registra en el CECO de destino (sin kardex) y sus lineas se reparten por
-- item de requerimiento / necesidad de compra.

alter table ${schema}.orden_abastecimiento
  add column direct_delivery boolean not null default false;

comment on column ${schema}.orden_abastecimiento.direct_delivery is
  'DEC-L084: el proveedor entrega en los CECO de destino; la OC no se recibe en el almacen.';

-- Recepcion en almacen (warehouse_id) o en un CECO (destination_ceco_id), nunca ambas.
alter table ${schema}.recepcion
  alter column warehouse_id drop not null,
  add column destination_ceco_id uuid references ${schema}.ceco(ceco_id),
  add column conformity_url varchar(500),
  add column document_id uuid references ${schema}.documento(document_id),
  add constraint recepcion_lugar_check check ((warehouse_id is null) <> (destination_ceco_id is null));

-- Linea de una entrega directa: a que item de requerimiento y necesidad va.
-- La OC directa sin requerimiento deja ambos en null (va al CECO de la OC).
alter table ${schema}.recepcion_item
  add column request_item_id uuid references ${schema}.requerimiento_item(request_item_id),
  add column need_id uuid references ${schema}.necesidad_compra(need_id);

create index idx_recepcion_item_request_item on ${schema}.recepcion_item(request_item_id)
  where request_item_id is not null;
create index idx_recepcion_item_need on ${schema}.recepcion_item(need_id)
  where need_id is not null;
create index idx_recepcion_destino_ceco on ${schema}.recepcion(destination_ceco_id)
  where destination_ceco_id is not null;
