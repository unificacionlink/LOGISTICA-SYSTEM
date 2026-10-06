-- V9__proveedor_habitual_producto.sql
-- DEC-L018: al aprobarse un requerimiento el sistema crea sola la RFQ del
-- faltante dirigida al proveedor habitual de cada producto. Se completa solo
-- con la primera OC emitida del producto y es editable desde el Catalogo.
alter table ${schema}.producto
  add column preferred_supplier_id uuid references ${schema}.proveedor(supplier_id);

comment on column ${schema}.producto.preferred_supplier_id is
  'Proveedor habitual: destino de la RFQ automatica por faltante (DEC-L018).';

create index idx_producto_preferred_supplier on ${schema}.producto (preferred_supplier_id)
  where preferred_supplier_id is not null;
