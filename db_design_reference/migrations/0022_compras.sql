-- 0022_compras.sql
-- ORDEN_ABASTECIMIENTO, ORDEN_ITEM, ORDEN_ITEM_DISTRIBUCION (grupo COMPRAS).
-- Unifica OC normal, Pedido por convenio y regularizacion de Caja Chica.
-- petty_cash_ref_id referencia CAJA_CHICA_REF (0023, posterior; hay ademas una
-- referencia circular: CAJA_CHICA_REF.regularization_order_id apunta de vuelta
-- a esta tabla) -> ambas FKs se agregan diferidas en 0029_fk_deferred.sql.

create table if not exists public.orden_abastecimiento (
  order_id   uuid primary key default gen_random_uuid(),
  order_code varchar(50) not null,
  company_id  uuid not null references public.empresa(company_id),
  supplier_id uuid not null references public.proveedor(supplier_id),
  order_type  varchar(35) not null check (order_type in ('OC_NORMAL','PEDIDO_CONVENIO','OC_REG_CAJA_CHICA')),
  quotation_id uuid references public.cotizacion(quotation_id),
  agreement_id uuid references public.convenio_proveedor(agreement_id),
  petty_cash_ref_id uuid,  -- FK a caja_chica_ref, diferida
  issue_date date not null default current_date,
  actual_purchase_date date,  -- fecha real si fue regularizacion
  payment_condition varchar(40) not null,
  delivery_mode      varchar(30) not null,
  delivery_location  varchar(220),
  currency_code varchar(3) not null references public.moneda(currency_code),
  exchange_rate numeric(12,6),
  subtotal   numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  total      numeric(14,2) not null default 0,
  status varchar(40) not null default 'EMITIDA'
    check (status in (
      'EMITIDA','EN_ATENCION','RECEPCION_PARCIAL','RECEPCION_COMPLETA',
      'PEND_VERIFICACION','VERIFICADA','ANULADA'
    )),
  created_by uuid not null references public.usuario(user_id),
  created_at timestamptz not null default now(),

  unique (company_id, order_code),
  check (
    (order_type = 'OC_NORMAL' and quotation_id is not null)
    or (order_type = 'PEDIDO_CONVENIO' and agreement_id is not null)
    or (order_type = 'OC_REG_CAJA_CHICA' and petty_cash_ref_id is not null)
  )
);

comment on table public.orden_abastecimiento is
  'Unifica OC normal, Orden de Pedido por convenio y regularizacion de Caja Chica (order_type).';

create table if not exists public.orden_item (
  order_item_id uuid primary key default gen_random_uuid(),
  order_id   uuid not null references public.orden_abastecimiento(order_id),
  product_id uuid references public.producto(product_id),  -- null si es servicio
  variant_id uuid references public.producto_variante(variant_id),
  description varchar(250) not null,
  uom_id     uuid references public.unidad_medida(uom_id),
  qty_ordered numeric(14,3) not null check (qty_ordered > 0),
  unit_cost   numeric(14,4) not null,  -- costo de compra unitario (RN-007)
  line_subtotal numeric(14,2) not null,
  tax_amount    numeric(14,2),
  line_total    numeric(14,2) not null
);

comment on table public.orden_item is 'Detalle de bienes/servicios de la orden.';

create table if not exists public.orden_item_distribucion (
  order_item_distribution_id uuid primary key default gen_random_uuid(),
  order_item_id uuid not null references public.orden_item(order_item_id),
  ceco_id    uuid not null references public.ceco(ceco_id),
  project_id uuid references public.proyecto(project_id),
  request_item_id uuid references public.requerimiento_item(request_item_id),
  qty_allocated    numeric(14,3) not null,
  amount_allocated numeric(14,2) not null
);

comment on table public.orden_item_distribucion is 'Distribucion de cantidad/monto de cada item por CECO/proyecto.';

create index if not exists idx_orden_abastecimiento_company_status on public.orden_abastecimiento (company_id, status);
create index if not exists idx_orden_item_order on public.orden_item (order_id);

-- RN-007/008: "ultimo costo de compra" y "costo promedio ponderado historico",
-- SIEMPRE derivados de ordenes validadas (nunca editables a mano).
create or replace view public.v_costo_referencia_producto as
select
  oi.product_id,
  oi.variant_id,
  oa.company_id,
  (array_agg(oi.unit_cost order by oa.issue_date desc, oa.created_at desc))[1] as last_purchase_unit_cost,
  (array_agg(oa.order_id order by oa.issue_date desc, oa.created_at desc))[1]  as last_purchase_order_id,
  (array_agg(oa.issue_date order by oa.issue_date desc, oa.created_at desc))[1] as last_purchase_date,
  (array_agg(oa.supplier_id order by oa.issue_date desc, oa.created_at desc))[1] as last_purchase_supplier_id,
  sum(oi.unit_cost * oi.qty_ordered) / nullif(sum(oi.qty_ordered), 0) as weighted_avg_unit_cost
from public.orden_item oi
join public.orden_abastecimiento oa on oa.order_id = oi.order_id
where oa.status not in ('ANULADA')
group by oi.product_id, oi.variant_id, oa.company_id;

comment on view public.v_costo_referencia_producto is
  'Ultimo costo de compra (derivado de la orden valida mas reciente) y costo promedio ponderado '
  'historico, por producto/variante/empresa. RN-007/008: se muestran por separado y nunca se editan a mano.';
