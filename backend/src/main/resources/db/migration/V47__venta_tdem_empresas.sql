-- DEC-L086: TDEM es la empresa central. Compra para TDEM, GOYDEL y METRIN
-- (consolidados, RFQ y OC a su nombre), emite las Notas de Inventario y vende
-- a GOYDEL y METRIN lo que entrega en sus proyectos, al precio de venta de cada
-- empresa (precio_venta_producto, DEC-L035). El precio se congela al despachar
-- o al recibir la entrega directa: un cambio de precio posterior no altera lo
-- ya vendido. null = la empresa no tiene precio de venta vigente del producto.

alter table ${schema}.salida_item
  add column sale_unit_price numeric(14,4) check (sale_unit_price is null or sale_unit_price > 0);

comment on column ${schema}.salida_item.sale_unit_price is
  'DEC-L086: precio de venta de TDEM a la empresa del CECO de destino; null si el destino es TDEM o no hay precio.';

alter table ${schema}.nota_inventario
  add column sale_company_id uuid references ${schema}.empresa(company_id),
  add column total_sale_value numeric(14,2);

comment on column ${schema}.nota_inventario.sale_company_id is
  'DEC-L086: empresa a la que TDEM vende lo despachado (GOYDEL o METRIN); null si el destino es de TDEM.';
comment on column ${schema}.nota_inventario.total_sale_value is
  'DEC-L086: suma de cantidad x precio de venta de las lineas con precio; null si ninguna lo tiene.';

alter table ${schema}.recepcion_item
  add column sale_unit_price numeric(14,4) check (sale_unit_price is null or sale_unit_price > 0);

comment on column ${schema}.recepcion_item.sale_unit_price is
  'DEC-L086: en la entrega directa a un CECO de GOYDEL o METRIN, precio de venta de TDEM a esa empresa.';

create index idx_nota_inventario_venta on ${schema}.nota_inventario(sale_company_id, issued_at)
  where sale_company_id is not null;
