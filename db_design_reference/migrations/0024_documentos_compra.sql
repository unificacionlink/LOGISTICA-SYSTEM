-- 0024_documentos_compra.sql
-- GUIA_REMISION, FACTURA_PROVEEDOR, FACTURA_ITEM, PLANTILLA_DOCUMENTO (grupo
-- DOCUMENTOS). receipt_id/shipment_id referencian RECEPCION/ENVIO (Fase 2) --
-- sin FK por ahora.

create table if not exists public.guia_remision (
  guide_id uuid primary key default gen_random_uuid(),
  order_id   uuid references public.orden_abastecimiento(order_id),
  receipt_id uuid,   -- FK a recepcion, Fase 2
  shipment_id uuid,  -- FK a envio, Fase 2
  issuer_type varchar(20) not null check (issuer_type in ('PROVEEDOR','EMPRESA')),
  issuer_ruc  varchar(11) not null,
  series varchar(10) not null,
  number varchar(20) not null,
  issue_date date not null,
  transfer_reason varchar(60),
  origin      varchar(220),
  destination varchar(220),
  document_id uuid references public.documento(document_id),
  validation_status varchar(30) not null default 'PENDIENTE'
    check (validation_status in ('PENDIENTE','VALIDADA','OBSERVADA')),
  unique (issuer_ruc, series, number)
);

comment on table public.guia_remision is
  'Guia asociada al traslado; el emisor depende de quien realiza el transporte.';

create table if not exists public.factura_proveedor (
  invoice_id uuid primary key default gen_random_uuid(),
  supplier_id uuid not null references public.proveedor(supplier_id),
  series varchar(10) not null,
  number varchar(20) not null,
  issue_date date not null,
  currency_code varchar(3) not null references public.moneda(currency_code),
  exchange_rate numeric(12,6),
  subtotal   numeric(14,2) not null,
  tax_amount numeric(14,2) not null,
  total      numeric(14,2) not null,
  xml_document_id uuid references public.documento(document_id),
  pdf_document_id uuid references public.documento(document_id),
  validation_status varchar(30) not null default 'PENDIENTE'
    check (validation_status in ('PENDIENTE','VALIDADA','OBSERVADA')),
  unique (supplier_id, series, number)
);

comment on table public.factura_proveedor is 'Factura electronica del proveedor; XML + PDF.';

create table if not exists public.factura_item (
  invoice_item_id uuid primary key default gen_random_uuid(),
  invoice_id uuid not null references public.factura_proveedor(invoice_id),
  order_item_id uuid references public.orden_item(order_item_id),
  product_id uuid references public.producto(product_id),
  description varchar(250) not null,
  qty_invoiced numeric(14,3) not null,
  unit_cost    numeric(14,4) not null,
  line_total   numeric(14,2) not null
);

comment on table public.factura_item is 'Detalle facturado por producto/servicio.';

create table if not exists public.plantilla_documento (
  template_id uuid primary key default gen_random_uuid(),
  document_type varchar(40) not null
    check (document_type in ('REQUERIMIENTO','RFQ','ORDEN_COMPRA','ORDEN_PEDIDO','GUIA','TRACKING')),
  issuer_company_id uuid not null references public.empresa(company_id),
  template_version varchar(20) not null default '1.0',
  logo_document_id uuid references public.documento(document_id),
  header_text text,
  footer_text text,
  fields_config_json jsonb,
  active boolean not null default true,
  effective_from date not null default current_date
);

comment on table public.plantilla_documento is
  'Plantillas versionadas para generar PDFs corporativos con encabezado/logo de cada empresa.';

-- FK diferida desde 0023_caja_chica.sql: caja_chica_ref.invoice_id -> aqui.
alter table public.caja_chica_ref
  add constraint fk_caja_chica_ref_invoice
  foreign key (invoice_id) references public.factura_proveedor(invoice_id);

create index if not exists idx_factura_proveedor_supplier on public.factura_proveedor (supplier_id);
create index if not exists idx_factura_item_invoice on public.factura_item (invoice_id);
