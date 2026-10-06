-- 0020_cotizacion.sql
-- COTIZACION, COTIZACION_ITEM (grupo COTIZACIONES). RN-007/008: se guarda el
-- costo cotizado tal cual; "ultimo costo de compra" y "costo promedio
-- ponderado" son derivados (vistas/consultas), nunca columnas editables aqui.

create table if not exists public.cotizacion (
  quotation_id uuid primary key default gen_random_uuid(),
  rfq_id      uuid references public.rfq(rfq_id),
  supplier_id uuid not null references public.proveedor(supplier_id),
  supplier_quote_number varchar(80),
  issue_date       date,
  expiration_date  date,
  currency_code varchar(3) not null references public.moneda(currency_code),
  subtotal   numeric(14,2),
  tax_amount numeric(14,2),
  total      numeric(14,2) not null,
  payment_condition varchar(40),
  delivery_mode      varchar(30),
  lead_days integer,
  source    varchar(20) not null default 'MANUAL' check (source in ('OCR','MANUAL')),
  document_id uuid references public.documento(document_id),
  status varchar(30) not null default 'RECIBIDA'
    check (status in ('RECIBIDA','VALIDADA','DESCARTADA','SELECCIONADA'))
);

comment on table public.cotizacion is 'Cabecera de cotizacion recibida por OCR o manual.';

create table if not exists public.cotizacion_item (
  quotation_item_id uuid primary key default gen_random_uuid(),
  quotation_id uuid not null references public.cotizacion(quotation_id),
  rfq_item_id  uuid references public.rfq_item(rfq_item_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  qty          numeric(14,3) not null,
  unit_cost    numeric(14,4) not null,  -- costo de compra cotizado
  line_total   numeric(14,2) not null,
  offered_brand varchar(100),
  offered_model varchar(120),
  technical_status varchar(30) not null default 'PENDIENTE'
    check (technical_status in ('PENDIENTE','CONFORME','NO_CONFORME','NO_APLICA')),
  technical_observation text
);

comment on table public.cotizacion_item is 'Detalle de precio y evaluacion tecnica por item cotizado.';

-- RN-007/008: ultimo costo de compra vs costo promedio ponderado, derivados
-- de ORDEN_ITEM validado (se crea en 0022_compras.sql). Vistas definidas ahi
-- una vez que orden_abastecimiento/orden_item existen.
