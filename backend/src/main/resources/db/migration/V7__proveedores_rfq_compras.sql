-- V7__proveedores_rfq_compras.sql
-- Lote 5: Proveedores + RFQ + Cotizaciones + Orden de Compra. Basada en
-- db_design_reference/migrations/0012_proveedores.sql +
-- 0019/0020_cotizacion*.sql + 0022_compras.sql. Sin RLS (DEC-L008).
--
-- Recortes respecto al diseno de referencia (ver DEC-L013):
--  * proveedor_empresa/proveedor_producto/convenio_proveedor*/
--    escala_precio_proveedor: fuera de alcance (solo ruta RFQ, sin convenios
--    ni catalogos de precio).
--  * consulta_proveedor(_item)/comunicacion_proveedor: sin canal SMTP/
--    WhatsApp integrado (mismo argumento que DEC-L009); Logistica contacta
--    proveedores fuera del sistema.
--  * rfq_item_origen: eliminada. En este lote una RFQ nace de items de UN
--    solo requerimiento (sin consolidacion cruzada RN-002/004), asi que
--    rfq_item referencia directo a requerimiento_item.
--  * cotizacion_item: sin technical_status/technical_observation (no hay
--    workflow de revision tecnica en este lote).
--  * orden_item_distribucion: eliminada. Este backend es 1 requerimiento =
--    1 CECO, asi que orden_item lleva request_item_id directo.
--  * orden_abastecimiento.agreement_id/petty_cash_ref_id: no se crean
--    (rutas CONVENIO/CAJA_CHICA fuera de alcance); quotation_id queda
--    NOT NULL en vez del CHECK de "exactamente una FK" del diseno original.
--  * rfq.penalty_rule_id: no se mapea (regla_penalidad sigue sin filas,
--    PD-03 sin resolver), mismo criterio que no construir
--    v_costo_referencia_producto (RN-007/008) "para despues".
--
-- moneda y el CHECK de documento.document_type (ya incluye 'COTIZACION' y
-- 'OC') existen desde V1/V6 — no se tocan.

create table ${schema}.proveedor (
  supplier_id  uuid primary key default gen_random_uuid(),
  ruc          varchar(11) not null unique,
  legal_name   varchar(220) not null,
  contact_name varchar(150),
  email        varchar(180),
  whatsapp_phone varchar(30),
  supplier_type varchar(30) not null default 'NO_CRITICO'
    check (supplier_type in ('CRITICO','NO_CRITICO')),
  preferred_delivery_mode varchar(30)
    check (preferred_delivery_mode in ('AGENCIA','ALMACEN','RECOJO')),
  active       boolean not null default true
);

comment on table ${schema}.proveedor is
  'Maestro de proveedores. proveedor_empresa/proveedor_producto/convenio_* '
  '(evaluacion, habilitacion por empresa, convenios de precio fijo) fuera de '
  'alcance en este lote (DEC-L013).';

create table ${schema}.rfq (
  rfq_id   uuid primary key default gen_random_uuid(),
  rfq_code varchar(50) not null unique,
  company_id  uuid not null references ${schema}.empresa(company_id),
  request_id  uuid not null references ${schema}.requerimiento(request_id),
  supplier_id uuid not null references ${schema}.proveedor(supplier_id),
  issue_date  date not null default current_date,
  payment_condition varchar(40) check (payment_condition in ('CONTADO','ANTICIPO','CREDITO_30')),
  delivery_mode      varchar(30) check (delivery_mode in ('AGENCIA','ALMACEN_PROVEEDOR','ALMACEN_CLIENTE')),
  delivery_location  varchar(220),
  requested_lead_days integer,
  currency_requested  varchar(3) references ${schema}.moneda(currency_code),
  status varchar(30) not null default 'ENVIADA' check (status in ('BORRADOR','ENVIADA','RESPONDIDA','CERRADA')),
  created_by uuid not null references ${schema}.usuario(user_id),
  created_at timestamptz not null default now()
);

comment on table ${schema}.rfq is
  'Una RFQ nace de items de UN requerimiento (request_id directo, sin '
  'consolidacion cruzada RN-002/004 — necesidad_abastecimiento/consolidacion '
  'no se construyen en este lote).';

create index idx_rfq_request on ${schema}.rfq (request_id);

create table ${schema}.rfq_item (
  rfq_item_id uuid primary key default gen_random_uuid(),
  rfq_id     uuid not null references ${schema}.rfq(rfq_id),
  request_item_id uuid not null references ${schema}.requerimiento_item(request_item_id),
  product_id uuid not null references ${schema}.producto(product_id),
  variant_id uuid references ${schema}.producto_variante(variant_id),
  uom_id     uuid not null references ${schema}.unidad_medida(uom_id),
  qty        numeric(14,3) not null check (qty > 0),
  technical_specification text
);

comment on table ${schema}.rfq_item is
  'product_id/variant_id/uom_id se copian del requerimiento_item al crear la '
  'RFQ (snapshot), mismo patron que requerimiento_item.unit_value_snapshot.';

create index idx_rfq_item_request_item on ${schema}.rfq_item (request_item_id);

create table ${schema}.cotizacion (
  quotation_id uuid primary key default gen_random_uuid(),
  rfq_id      uuid not null references ${schema}.rfq(rfq_id),
  supplier_id uuid not null references ${schema}.proveedor(supplier_id),
  supplier_quote_number varchar(80),
  issue_date       date,
  expiration_date  date,
  currency_code varchar(3) not null references ${schema}.moneda(currency_code),
  subtotal   numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  total      numeric(14,2) not null default 0,
  payment_condition varchar(40),
  delivery_mode      varchar(30),
  lead_days integer,
  source    varchar(20) not null default 'MANUAL' check (source in ('OCR','MANUAL')),
  document_id uuid references ${schema}.documento(document_id),
  status varchar(30) not null default 'RECIBIDA'
    check (status in ('RECIBIDA','VALIDADA','DESCARTADA','SELECCIONADA'))
);

comment on table ${schema}.cotizacion is
  'rfq_id NOT NULL (a diferencia del diseno de referencia): no existe ruta '
  'de cotizacion sin RFQ en este lote. source siempre MANUAL, no hay '
  'pipeline OCR.';

create index idx_cotizacion_rfq on ${schema}.cotizacion (rfq_id);

create table ${schema}.cotizacion_item (
  quotation_item_id uuid primary key default gen_random_uuid(),
  quotation_id uuid not null references ${schema}.cotizacion(quotation_id),
  rfq_item_id  uuid not null references ${schema}.rfq_item(rfq_item_id),
  product_id   uuid not null references ${schema}.producto(product_id),
  variant_id   uuid references ${schema}.producto_variante(variant_id),
  qty          numeric(14,3) not null check (qty > 0),
  unit_cost    numeric(14,4) not null,
  line_total   numeric(14,2) not null,
  offered_brand varchar(100),
  offered_model varchar(120)
);

comment on table ${schema}.cotizacion_item is
  'rfq_item_id NOT NULL. Sin technical_status/technical_observation (sin '
  'workflow de revision tecnica en este lote).';

create index idx_cotizacion_item_quotation on ${schema}.cotizacion_item (quotation_id);

create table ${schema}.orden_abastecimiento (
  order_id   uuid primary key default gen_random_uuid(),
  order_code varchar(50) not null,
  company_id  uuid not null references ${schema}.empresa(company_id),
  supplier_id uuid not null references ${schema}.proveedor(supplier_id),
  order_type  varchar(35) not null default 'OC_NORMAL'
    check (order_type in ('OC_NORMAL','PEDIDO_CONVENIO','OC_REG_CAJA_CHICA')),
  quotation_id uuid not null unique references ${schema}.cotizacion(quotation_id),
  issue_date date not null default current_date,
  payment_condition varchar(40) not null,
  delivery_mode      varchar(30) not null,
  delivery_location  varchar(220),
  currency_code varchar(3) not null references ${schema}.moneda(currency_code),
  exchange_rate numeric(12,6),
  subtotal   numeric(14,2) not null default 0,
  tax_amount numeric(14,2) not null default 0,
  total      numeric(14,2) not null default 0,
  status varchar(40) not null default 'EMITIDA'
    check (status in (
      'EMITIDA','EN_ATENCION','RECEPCION_PARCIAL','RECEPCION_COMPLETA',
      'PEND_VERIFICACION','VERIFICADA','ANULADA'
    )),
  created_by uuid not null references ${schema}.usuario(user_id),
  created_at timestamptz not null default now(),
  unique (company_id, order_code)
);

comment on table ${schema}.orden_abastecimiento is
  'agreement_id/petty_cash_ref_id NO se crean (rutas CONVENIO/CAJA_CHICA '
  'fuera de alcance): quotation_id queda NOT NULL en vez del CHECK de '
  '"exactamente una FK" del diseno de referencia. quotation_id es UNIQUE '
  'a proposito: una cotizacion solo puede emitir una orden (evita crear dos '
  'OC si "Seleccionar" se dispara dos veces en carrera). Este lote solo '
  'emite EMITIDA/ANULADA; el resto del enum de status queda listo para '
  'Almacen (Fase 2, recepcion fisica).';

create index idx_orden_abastecimiento_company_status on ${schema}.orden_abastecimiento (company_id, status);

create table ${schema}.orden_item (
  order_item_id uuid primary key default gen_random_uuid(),
  order_id   uuid not null references ${schema}.orden_abastecimiento(order_id),
  request_item_id uuid not null references ${schema}.requerimiento_item(request_item_id),
  product_id uuid references ${schema}.producto(product_id),
  variant_id uuid references ${schema}.producto_variante(variant_id),
  description varchar(250) not null,
  uom_id     uuid references ${schema}.unidad_medida(uom_id),
  qty_ordered numeric(14,3) not null check (qty_ordered > 0),
  unit_cost   numeric(14,4) not null,
  line_subtotal numeric(14,2) not null,
  tax_amount    numeric(14,2) not null default 0,
  line_total    numeric(14,2) not null
);

comment on table ${schema}.orden_item is
  'request_item_id NOT NULL (reemplaza orden_item_distribucion: 1 '
  'requerimiento = 1 CECO en este backend, no hace falta repartir por '
  'CECO/proyecto). Sin lineas de servicio libres (product_id null) en este lote.';

create index idx_orden_item_order on ${schema}.orden_item (order_id);
create index idx_orden_item_request_item on ${schema}.orden_item (request_item_id);
