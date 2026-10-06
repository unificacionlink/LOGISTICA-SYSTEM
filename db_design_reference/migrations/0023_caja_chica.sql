-- 0023_caja_chica.sql
-- CAJA_CHICA_REF, CAJA_CHICA_ITEM (grupo CONTABILIDAD). Solo REFERENCIA al
-- aplicativo externo de Caja Chica -- nunca duplica su fondo/rendicion
-- (Excel 18_Caja_Chica_V35). warehouse_receipt_id / receipt_item_id referencian
-- RECEPCION/RECEPCION_ITEM (Fase 2, fuera de alcance) -- sin FK por ahora.

create table if not exists public.caja_chica_ref (
  petty_cash_ref_id uuid primary key default gen_random_uuid(),
  petty_cash_code varchar(50) not null unique,  -- correlativo interno ERP
  external_movement_id varchar(80) unique,       -- id del aplicativo de Caja Chica cuando ya sincronizado
  company_id  uuid not null references public.empresa(company_id),
  request_id  uuid not null references public.requerimiento(request_id),
  purchase_date date not null,
  supplier_id uuid references public.proveedor(supplier_id),
  invoice_id  uuid,  -- FK a factura_proveedor, se agrega en 0024 (misma migracion que crea esa tabla)
  expense_type varchar(50) not null check (expense_type in ('MATERIAL','PEAJE','COCHERA','ENCOMIENDA','OTRO')),
  amount numeric(14,2) not null,
  currency_code varchar(3) not null references public.moneda(currency_code),

  document_type varchar(30) not null check (document_type in ('FACTURA','BOLETA','RECIBO','TICKET','OTRO')),
  document_series varchar(20),
  document_number varchar(40) not null,
  document_id uuid references public.documento(document_id),
  duplicate_control_key varchar(250) not null unique,  -- RUC+comprobante+serie+numero+importe

  requires_order boolean not null default false,
  regularization_order_id uuid references public.orden_abastecimiento(order_id),
  requires_warehouse_receipt boolean not null default false,
  warehouse_receipt_id uuid,  -- FK a recepcion, Fase 2

  purchase_user_id uuid references public.usuario(user_id),
  regularized_by   uuid references public.usuario(user_id),
  regularization_status varchar(30) not null default 'PENDIENTE_REGULARIZACION'
    check (regularization_status in (
      'PENDIENTE_REGULARIZACION','EN_REVISION','REGULARIZADO','PENDIENTE_RECEPCION',
      'CERRADO','OBSERVADO','ANULADO'
    )),
  regularization_reason text,
  observed_reason text,  -- obligatorio cuando regularization_status = OBSERVADO
  regularized_at timestamptz,
  closed_at      timestamptz,

  -- V36 (Excel 19_Ruta_Req_CajaChica_V36).
  route_reason_code varchar(40) not null
    check (route_reason_code in ('MISMO_DIA','FALLA_PROVEEDOR','BAJO_MONTO','EXCEPCION_AUTORIZADA')),
  route_rule_id uuid references public.regla_caja_chica(petty_cash_rule_id),

  check (regularization_status <> 'OBSERVADO' or observed_reason is not null)
);

comment on table public.caja_chica_ref is
  'Referencia al aplicativo externo de Caja Chica; no duplica su fondo/rendicion.';

create table if not exists public.caja_chica_item (
  petty_cash_item_id uuid primary key default gen_random_uuid(),
  petty_cash_ref_id uuid not null references public.caja_chica_ref(petty_cash_ref_id),
  line_number integer not null,
  item_type varchar(30) not null check (item_type in ('PRODUCTO','SERVICIO_GASTO')),
  product_id uuid references public.producto(product_id),
  variant_id uuid references public.producto_variante(variant_id),
  description text not null,
  quantity numeric(14,3) not null,
  uom_id uuid references public.unidad_medida(uom_id),
  unit_cost numeric(14,4) not null,
  line_total numeric(14,2) not null,
  ceco_id    uuid not null references public.ceco(ceco_id),
  project_id uuid references public.proyecto(project_id),
  requires_warehouse_receipt boolean not null default false,
  receipt_item_id uuid,  -- FK a recepcion_item, Fase 2
  status varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','REGULARIZADO','PENDIENTE_RECEPCION','RECIBIDO','CERRADO')),

  request_item_id uuid references public.requerimiento_item(request_item_id),
  supply_need_id  uuid references public.necesidad_abastecimiento(supply_need_id),

  check (item_type <> 'PRODUCTO' or product_id is not null),
  check (item_type <> 'PRODUCTO' or request_item_id is not null)
);

comment on table public.caja_chica_item is
  'Detalle de productos/servicios adquiridos por Caja Chica, vinculado a SKU/CECO/requerimiento cuando aplica.';

create index if not exists idx_caja_chica_ref_status on public.caja_chica_ref (company_id, regularization_status);
create index if not exists idx_caja_chica_item_ref on public.caja_chica_item (petty_cash_ref_id);

-- FK circular con orden_abastecimiento (esta creo la de regularization_order_id
-- arriba de forma directa porque orden_abastecimiento ya existe desde 0022; la
-- que falta es la inversa, orden_abastecimiento.petty_cash_ref_id -> aqui).
alter table public.orden_abastecimiento
  add constraint fk_orden_abastecimiento_petty_cash
  foreign key (petty_cash_ref_id) references public.caja_chica_ref(petty_cash_ref_id);
