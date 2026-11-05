-- V20 — Lote 16: necesidad de compra, consolidacion entre requerimientos y
-- RFQ multiproducto por proveedor (DEC-L039). Reemplaza DEC-L013 (sin
-- consolidacion) y la RFQ automatica al proveedor habitual (DEC-L018).
--
-- Basado en db_design_reference/migrations/0018_abastecimiento.sql
-- (necesidad_abastecimiento, consolidacion) y en la V38.7 (purchaseNeeds,
-- consolidations, rfqBundles), sin RLS. La necesidad apunta directo a su
-- consolidado (un faltante entra entero a un solo consolidado), por eso no se
-- crea consolidacion_item.
--
-- Flujo: al aprobar, el faltante de cada item crea una necesidad. Si la regla
-- de Caja Chica no aplica entra sola al consolidado ABIERTO de su empresa +
-- SKU; si aplica espera que Logistica confirme la ruta. Logistica prepara las
-- RFQ (una por empresa + proveedor, con varios productos), registra las
-- cotizaciones, adjudica cada consolidado a una linea cotizada y emite la OC
-- por cotizacion. Lo recibido se reparte entre las necesidades del consolidado.

-- ------------------------------------------------------------------ cobertura
-- Proveedores habilitados por producto (varios por SKU). Reemplaza al
-- proveedor habitual unico (V9): la RFQ va a todos los habilitados.
create table ${schema}.proveedor_producto (
  product_id  uuid not null references ${schema}.producto(product_id),
  supplier_id uuid not null references ${schema}.proveedor(supplier_id),
  created_by  uuid references ${schema}.usuario(user_id),
  created_at  timestamptz not null default now(),
  primary key (product_id, supplier_id)
);

create index idx_proveedor_producto_supplier on ${schema}.proveedor_producto (supplier_id);

insert into ${schema}.proveedor_producto (product_id, supplier_id)
select product_id, preferred_supplier_id from ${schema}.producto where preferred_supplier_id is not null;

insert into ${schema}.proveedor_producto (product_id, supplier_id)
select distinct oi.product_id, o.supplier_id
  from ${schema}.orden_item oi join ${schema}.orden_abastecimiento o on o.order_id = oi.order_id
 where oi.product_id is not null and o.status <> 'ANULADA'
on conflict do nothing;

alter table ${schema}.producto drop column preferred_supplier_id;

-- ------------------------------------------------------------------ consolidado
create table ${schema}.consolidacion (
  consolidation_id   uuid primary key default gen_random_uuid(),
  consolidation_code varchar(50) not null unique,
  company_id uuid not null references ${schema}.empresa(company_id),
  product_id uuid not null references ${schema}.producto(product_id),
  variant_id uuid references ${schema}.producto_variante(variant_id),
  uom_id     uuid not null references ${schema}.unidad_medida(uom_id),
  total_qty  numeric(14,3) not null default 0 check (total_qty >= 0),
  -- ABIERTA: recibe necesidades. EN_COTIZACION: ya tiene RFQ (cerrado a nuevas
  -- necesidades). ADJUDICADA: linea cotizada elegida. ORDENADA: con OC.
  status varchar(20) not null default 'ABIERTA'
    check (status in ('ABIERTA','EN_COTIZACION','ADJUDICADA','ORDENADA','ANULADA')),
  awarded_quotation_item_id uuid references ${schema}.cotizacion_item(quotation_item_id),
  awarded_by uuid references ${schema}.usuario(user_id),
  awarded_at timestamptz,
  cancel_reason text,
  created_at timestamptz not null default now(),
  check (status not in ('ADJUDICADA','ORDENADA') or awarded_quotation_item_id is not null),
  check (status <> 'ANULADA' or cancel_reason is not null)
);

comment on table ${schema}.consolidacion is
  'Agrupa las necesidades de compra de varios requerimientos por empresa + SKU + unidad (RN-004, DEC-L039).';

-- Un solo consolidado abierto por empresa + producto + variante + unidad.
create unique index uq_consolidacion_abierta on ${schema}.consolidacion
  (company_id, product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid), uom_id)
  where status = 'ABIERTA';
create index idx_consolidacion_status on ${schema}.consolidacion (company_id, status);

-- ------------------------------------------------------------------ necesidad
create table ${schema}.necesidad_compra (
  need_id         uuid primary key default gen_random_uuid(),
  request_item_id uuid not null references ${schema}.requerimiento_item(request_item_id),
  company_id uuid not null references ${schema}.empresa(company_id),
  product_id uuid not null references ${schema}.producto(product_id),
  variant_id uuid references ${schema}.producto_variante(variant_id),
  uom_id     uuid not null references ${schema}.unidad_medida(uom_id),
  qty        numeric(14,3) not null check (qty > 0),
  -- PENDIENTE_RUTA: la regla de Caja Chica aplica, Logistica decide.
  -- COMPRA: en un consolidado. CAJA_CHICA: se compra por Caja Chica.
  -- ANULADA: historica (su consolidado se anulo y el faltante volvio a ruta).
  status varchar(20) not null
    check (status in ('PENDIENTE_RUTA','COMPRA','CAJA_CHICA','ANULADA')),
  suggested_reason varchar(40)
    check (suggested_reason in ('BAJO_MONTO','MISMO_DIA','FALLA_PROVEEDOR')),
  consolidation_id uuid references ${schema}.consolidacion(consolidation_id),
  route_decided_by uuid references ${schema}.usuario(user_id),
  route_decided_at timestamptz,
  created_at timestamptz not null default now(),
  check (status <> 'COMPRA' or consolidation_id is not null)
);

comment on table ${schema}.necesidad_compra is
  'Faltante de un item de requerimiento tras reservar stock (RN-002, DEC-L039).';

create index idx_necesidad_compra_item on ${schema}.necesidad_compra (request_item_id);
create index idx_necesidad_compra_consolidacion on ${schema}.necesidad_compra (consolidation_id);
create index idx_necesidad_compra_status on ${schema}.necesidad_compra (company_id, status);

-- ------------------------------------------------------------------ RFQ / OC
-- La RFQ pasa a ser por empresa + proveedor con varios productos; BORRADOR =
-- preparada, por enviar. Sus lineas apuntan al consolidado, no al REQ.
alter table ${schema}.rfq
  alter column request_id drop not null,
  add column sent_at timestamptz,
  add column sent_by uuid references ${schema}.usuario(user_id),
  add column sent_channel varchar(20) check (sent_channel in ('CORREO','WHATSAPP','OTRO'));

update ${schema}.rfq set sent_at = created_at where status <> 'BORRADOR';

alter table ${schema}.rfq_item
  alter column request_item_id drop not null,
  add column consolidation_id uuid references ${schema}.consolidacion(consolidation_id);
create index idx_rfq_item_consolidacion on ${schema}.rfq_item (consolidation_id);

alter table ${schema}.orden_item
  alter column request_item_id drop not null,
  add column consolidation_id uuid references ${schema}.consolidacion(consolidation_id);
create index idx_orden_item_consolidacion on ${schema}.orden_item (consolidation_id);

-- Una cotizacion puede emitir mas de una OC (adjudicacion por producto en
-- momentos distintos); la carrera la evita el bloqueo del consolidado.
alter table ${schema}.orden_abastecimiento drop constraint orden_abastecimiento_quotation_id_key;
create index idx_orden_abastecimiento_quotation on ${schema}.orden_abastecimiento (quotation_id);

-- Reparto de lo recibido y compras por Caja Chica por necesidad.
alter table ${schema}.reserva_stock add column need_id uuid references ${schema}.necesidad_compra(need_id);
alter table ${schema}.caja_chica_item add column need_id uuid references ${schema}.necesidad_compra(need_id);
create index idx_reserva_stock_need on ${schema}.reserva_stock (need_id) where need_id is not null;

-- ------------------------------------------------------------------ datos previos
-- Cada linea de RFQ anterior (de un solo REQ) pasa a ser un consolidado con
-- una necesidad, para que todo el flujo lea un solo modelo.
create table ${schema}._v20_legado as
select ri.rfq_item_id, ri.request_item_id, ri.product_id, ri.variant_id, ri.uom_id, ri.qty,
       q.rfq_id, q.company_id, q.created_at, q.status as rfq_status,
       gen_random_uuid() as consolidation_id,
       gen_random_uuid() as need_id,
       (select ci.quotation_item_id
          from ${schema}.cotizacion_item ci join ${schema}.cotizacion c on c.quotation_id = ci.quotation_id
         where ci.rfq_item_id = ri.rfq_item_id and c.status = 'SELECCIONADA'
         limit 1) as awarded,
       exists (select 1
                 from ${schema}.orden_item oi
                 join ${schema}.orden_abastecimiento o on o.order_id = oi.order_id
                 join ${schema}.cotizacion c on c.quotation_id = o.quotation_id
                where c.rfq_id = q.rfq_id and oi.request_item_id = ri.request_item_id
                  and o.status <> 'ANULADA') as ordenada
  from ${schema}.rfq_item ri join ${schema}.rfq q on q.rfq_id = ri.rfq_id
 where ri.request_item_id is not null;

alter table ${schema}._v20_legado add column status varchar(20);
update ${schema}._v20_legado set status = case
    when ordenada and awarded is not null then 'ORDENADA'
    when rfq_status = 'CERRADA' then 'ANULADA'
    else 'EN_COTIZACION' end;

insert into ${schema}.consolidacion (consolidation_id, consolidation_code, company_id, product_id, variant_id,
  uom_id, total_qty, status, awarded_quotation_item_id, awarded_at, cancel_reason, created_at)
select l.consolidation_id,
       'CON-' || e.code || '-' || to_char(l.created_at at time zone 'America/Lima', 'YYYYMMDD') || '-'
         || upper(substr(replace(l.consolidation_id::text, '-', ''), 1, 6)),
       l.company_id, l.product_id, l.variant_id, l.uom_id, l.qty, l.status,
       case when l.status = 'ORDENADA' then l.awarded end,
       case when l.status = 'ORDENADA' then l.created_at end,
       case when l.status = 'ANULADA' then 'RFQ cerrada sin orden de compra (datos previos al Lote 16).' end,
       l.created_at
  from ${schema}._v20_legado l join ${schema}.empresa e on e.company_id = l.company_id;

insert into ${schema}.necesidad_compra (need_id, request_item_id, company_id, product_id, variant_id, uom_id,
  qty, status, consolidation_id, created_at)
select need_id, request_item_id, company_id, product_id, variant_id, uom_id, qty,
       case when status = 'ANULADA' then 'ANULADA' else 'COMPRA' end, consolidation_id, created_at
  from ${schema}._v20_legado;

update ${schema}.rfq_item ri set consolidation_id = l.consolidation_id
  from ${schema}._v20_legado l where l.rfq_item_id = ri.rfq_item_id;

update ${schema}.orden_item oi set consolidation_id = l.consolidation_id
  from ${schema}.orden_abastecimiento o, ${schema}.cotizacion c, ${schema}._v20_legado l
 where o.order_id = oi.order_id and c.quotation_id = o.quotation_id
   and l.rfq_id = c.rfq_id and l.request_item_id = oi.request_item_id;

-- Lo ya recibido queda asignado a su necesidad (no se vuelve a repartir).
update ${schema}.reserva_stock rs set need_id = n.need_id
  from ${schema}.recepcion_item rci, ${schema}.orden_item oi, ${schema}.necesidad_compra n
 where rs.origin = 'RECEPCION_OC' and rci.receipt_item_id = rs.receipt_item_id
   and oi.order_item_id = rci.order_item_id and n.consolidation_id = oi.consolidation_id
   and n.request_item_id = rs.request_item_id;

drop table ${schema}._v20_legado;

-- Faltantes de REQ en atencion que aun no estaban en ninguna RFQ: quedan como
-- necesidades por decidir ruta.
insert into ${schema}.necesidad_compra (request_item_id, company_id, product_id, variant_id, uom_id, qty, status)
select i.request_item_id, r.company_id, i.product_id, i.variant_id, i.uom_id, x.pendiente, 'PENDIENTE_RUTA'
  from ${schema}.requerimiento_item i
  join ${schema}.requerimiento r on r.request_id = i.request_id
  cross join lateral (select i.qty_requested
      - coalesce((select sum(rs.qty_reserved) from ${schema}.reserva_stock rs
                   where rs.request_item_id = i.request_item_id and rs.origin = 'VERIFICACION_STOCK'
                     and rs.status in ('RESERVADA','DESPACHADA')), 0)
      - coalesce((select sum(n.qty) from ${schema}.necesidad_compra n
                   where n.request_item_id = i.request_item_id and n.status <> 'ANULADA'), 0)
      - coalesce((select sum(c.quantity) from ${schema}.caja_chica_item c
                   join ${schema}.caja_chica_ref f on f.petty_cash_ref_id = c.petty_cash_ref_id
                   where c.request_item_id = i.request_item_id and f.regularization_status <> 'ANULADO'), 0)
      as pendiente) x
 where r.status in ('EN_ATENCION','ATENCION_PARCIAL') and x.pendiente > 0;
