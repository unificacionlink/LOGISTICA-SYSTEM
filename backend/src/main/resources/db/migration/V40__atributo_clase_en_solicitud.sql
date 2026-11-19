-- DEC-L075: la sugerencia de producto nuevo admite el atributo CLASE (codigo C + valor),
-- como las variantes del catalogo (EP04002-C0). producto_variante ya lo admitia (V1).
alter table ${schema}.solicitud_maestro_producto
  drop constraint if exists solicitud_maestro_producto_proposed_attribute_type_check;
alter table ${schema}.solicitud_maestro_producto
  add constraint solicitud_maestro_producto_proposed_attribute_type_check
  check (proposed_attribute_type in ('TALLA','CLASE','COLOR','MODELO','OTRO'));
