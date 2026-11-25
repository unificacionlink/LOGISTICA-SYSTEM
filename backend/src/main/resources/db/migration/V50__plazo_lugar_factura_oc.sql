-- DEC-L089: plazo y lugar de entrega estructurados en la OC, factura en el pago
-- final y documentos relacionados de la OC (observaciones 7 y 8 de Logistica).

-- Plazo: dias + tipo (habiles o calendario). delivery_term se sigue llenando con
-- el texto ("5 dias habiles") para el PDF F-LOG-09 y las OC anteriores.
alter table ${schema}.orden_abastecimiento
  add column delivery_days integer check (delivery_days is null or delivery_days between 1 and 365),
  add column delivery_day_type varchar(10) check (delivery_day_type is null or delivery_day_type in ('HABILES','CALENDARIO')),
  add column delivery_point_id uuid references ${schema}.punto_entrega(delivery_point_id),
  add column invoice_number varchar(30),
  add column invoice_document_id uuid references ${schema}.documento(document_id),
  add constraint orden_plazo_completo_check check ((delivery_days is null) = (delivery_day_type is null));

comment on column ${schema}.orden_abastecimiento.delivery_point_id is
  'DEC-L089: lugar de entrega elegido de punto_entrega; delivery_location guarda su texto al emitir.';
comment on column ${schema}.orden_abastecimiento.invoice_number is
  'DEC-L089: factura del proveedor (serie-numero), obligatoria al registrar el pago final.';

-- La lista de direcciones arranca con el almacen central de TDEM (sin usuario: lo crea la migracion).
alter table ${schema}.punto_entrega alter column created_by drop not null;

insert into ${schema}.punto_entrega (company_id, point_type, name, address, active)
select e.company_id, 'ALMACEN', 'Almacén central TDEM', coalesce(nullif(e.address, ''), 'Por definir'), true
  from ${schema}.empresa e
 where e.code = 'TDEMSRL'
   and not exists (select 1 from ${schema}.punto_entrega p where p.point_type = 'ALMACEN' and p.company_id = e.company_id);

create index idx_orden_punto_entrega on ${schema}.orden_abastecimiento(delivery_point_id)
  where delivery_point_id is not null;
