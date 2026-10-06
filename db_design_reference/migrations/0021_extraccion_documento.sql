-- 0021_extraccion_documento.sql
-- EXTRACCION_DOCUMENTO, EXTRACCION_CAMPO, EXTRACCION_COTIZACION_ITEM (grupo
-- DOCUMENTOS/COTIZACIONES). Tablas creadas para conservar el diseno de
-- Jonathan; el SERVICIO de extraccion OCR/XML queda diferido: en esta fase la
-- cotizacion se registra manualmente (cotizacion.source = 'MANUAL').

create table if not exists public.extraccion_documento (
  extraction_id uuid primary key default gen_random_uuid(),
  document_id uuid not null references public.documento(document_id),
  document_type varchar(40) not null check (document_type in ('COTIZACION','FACTURA','GUIA','OTRO')),
  extraction_method varchar(20) not null check (extraction_method in ('XML','PDF_TEXT','OCR')),
  status varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','PROCESANDO','REVISAR','VALIDADO','ERROR')),
  overall_confidence numeric(5,2),
  raw_text text,
  pages_processed integer,
  parser_version varchar(40),
  processed_at timestamptz,
  processed_by uuid references public.usuario(user_id)
);

comment on table public.extraccion_documento is
  'Control del procesamiento de documentos por XML/texto PDF/OCR asistido. Servicio diferido en esta fase.';

create table if not exists public.extraccion_campo (
  extraction_field_id uuid primary key default gen_random_uuid(),
  extraction_id uuid not null references public.extraccion_documento(extraction_id),
  field_name varchar(80) not null,
  detected_value text,
  final_value text,
  confidence numeric(5,2),
  was_manually_corrected boolean not null default false,
  validation_status varchar(20) not null default 'REVISAR'
    check (validation_status in ('OK','REVISAR','NO_DETECTADO','INVALIDO')),
  validation_message text,
  updated_by uuid references public.usuario(user_id)
);

comment on table public.extraccion_campo is 'Campos detectados por extraccion automatica y valor final validado.';

create table if not exists public.extraccion_cotizacion_item (
  extraction_quote_item_id uuid primary key default gen_random_uuid(),
  extraction_id uuid not null references public.extraccion_documento(extraction_id),
  line_number integer not null,
  detected_supplier_code varchar(80),
  detected_description text,
  detected_qty numeric(14,3),
  detected_unit_cost numeric(14,4),
  detected_line_total numeric(14,2),
  matched_product_id uuid references public.producto(product_id),
  matched_variant_id uuid references public.producto_variante(variant_id),
  matched_rfq_item_id uuid references public.rfq_item(rfq_item_id),
  match_confidence numeric(5,2),
  manual_review_required boolean not null default true,
  final_status varchar(30) not null default 'PENDIENTE'
    check (final_status in ('PENDIENTE','VINCULADO','CORREGIDO','OMITIDO'))
);

comment on table public.extraccion_cotizacion_item is
  'Lineas de producto detectadas en cotizaciones y conciliadas contra SKU/producto/variante del ERP.';
