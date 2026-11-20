-- DEC-L081: empresa del proveedor (en cual de las tres se registro), para filtrar
-- la pantalla de Proveedores. No restringe compras: cualquier empresa le puede
-- comprar a cualquier proveedor habilitado.
--
-- Carga inicial (decision del usuario): los repetidos en los modulos de TDEM y
-- GOYDEL quedan en TDEM; solo los que venian unicamente de GOYDEL son de GOYDEL.

alter table ${schema}.proveedor
  add column company_id uuid references ${schema}.empresa (company_id);

update ${schema}.proveedor p
   set company_id = e.company_id
  from ${schema}.empresa e
 where e.code = case
         when p.status_reason like 'Migrado desde el modulo de proveedores de GOYDEL%' then 'GOYDELSAC'
         else 'TDEMSRL'
       end;

create index idx_proveedor_empresa on ${schema}.proveedor (company_id);
