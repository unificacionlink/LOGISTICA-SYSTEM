-- 0010_configuracion_reglas.sql
-- MONEDA, TIPO_CAMBIO, REGLA_APROBACION, REGLA_DOCUMENTARIA, REGLA_PENALIDAD,
-- REGLA_CAJA_CHICA, CATALOGO_ESTADO (grupo CONFIGURACION).
--
-- Estas reglas son la implementacion de las decisiones de negocio pendientes
-- (docs/04_DECISIONES.md, PD-01..PD-08): valores CONFIGURABLES, nunca
-- hardcodeados en la app. Los valores sembrados aqui son PROVISIONALES hasta
-- que Jonathan/Gerencia los validen.
--
-- regla_penalidad.supplier_id/agreement_id referencian PROVEEDORES (0012,
-- posterior) -> FK diferida en 0029_fk_deferred.sql.

create table if not exists public.moneda (
  currency_code varchar(3) primary key,  -- PEN / USD
  name          varchar(80) not null,
  active        boolean not null default true
);

comment on table public.moneda is 'Catalogo de monedas.';

create table if not exists public.tipo_cambio (
  exchange_rate_id uuid primary key default gen_random_uuid(),
  rate_date     date not null,
  currency_code varchar(3) not null references public.moneda(currency_code),
  rate_type     varchar(20) not null check (rate_type in ('COMPRA','VENTA','CONTABLE')),
  rate_value    numeric(12,6) not null,
  source        varchar(120),
  unique (rate_date, currency_code, rate_type)
);

comment on table public.tipo_cambio is 'Historico del tipo de cambio usado en cada operacion.';

create table if not exists public.regla_aprobacion (
  approval_rule_id uuid primary key default gen_random_uuid(),
  company_id      uuid references public.empresa(company_id),  -- null = regla corporativa
  process         varchar(40) not null check (process in ('REQUERIMIENTO','COMPRA','CAJA_CHICA','CONVENIO')),
  amount_from     numeric(14,2),
  amount_to       numeric(14,2),
  supplier_type   varchar(30),
  supply_class    varchar(30),
  requires_approval        boolean not null default true,
  approver_permission_code varchar(80),
  priority        integer not null default 100,
  active          boolean not null default true
);

comment on table public.regla_aprobacion is
  'Aprobaciones por monto, proveedor, producto y ruta (PD-04: rangos monetarios pendientes de validar).';

create table if not exists public.regla_documentaria (
  document_rule_id uuid primary key default gen_random_uuid(),
  company_id       uuid references public.empresa(company_id),
  operation_type   varchar(40) not null check (operation_type in ('COMPRA','CAJA_CHICA','TRASLADO')),
  delivery_mode    varchar(30),
  amount_from      numeric(14,2),
  amount_to        numeric(14,2),
  required_document_type varchar(40) not null check (required_document_type in ('GUIA','FACTURA','XML','CONSTANCIA')),
  legal_basis      text,
  effective_from   date not null default current_date,
  effective_to     date,
  active           boolean not null default true
);

comment on table public.regla_documentaria is
  'Requisitos documentarios parametrizables (PD-02: umbral de guia de remision pendiente de validar legal).';

create table if not exists public.regla_penalidad (
  penalty_rule_id uuid primary key default gen_random_uuid(),
  company_id      uuid references public.empresa(company_id),
  supplier_id     uuid,   -- FK a proveedor, diferida
  agreement_id    uuid,   -- FK a convenio_proveedor, diferida
  breach_type     varchar(40) not null check (breach_type in ('PLAZO','CALIDAD','DOCUMENTACION')),
  penalty_pct     numeric(8,4) not null,
  grace_days      integer,
  max_penalty_pct numeric(8,4),
  active          boolean not null default true
);

comment on table public.regla_penalidad is
  'Penalidad configurable por incumplimiento (PD-03: porcentaje pendiente de confirmar).';

create table if not exists public.regla_caja_chica (
  petty_cash_rule_id uuid primary key default gen_random_uuid(),
  company_id     uuid references public.empresa(company_id),  -- null = regla corporativa
  rule_name      varchar(150) not null,
  low_amount_enabled     boolean not null default true,
  low_amount_threshold   numeric(14,2) not null default 100,
  threshold_currency     varchar(3) not null default 'PEN' references public.moneda(currency_code),
  threshold_scope        varchar(20) not null default 'REQUERIMIENTO'
                            check (threshold_scope in ('COMPRA','REQUERIMIENTO','ITEM')),
  same_day_enabled        boolean not null default true,
  supplier_failure_enabled boolean not null default true,
  manual_exception_enabled boolean not null default true,
  effective_from  date not null default current_date,
  effective_to    date,
  status          varchar(20) not null default 'BORRADOR' check (status in ('BORRADOR','VIGENTE','INACTIVA')),
  approved_by     uuid references public.usuario(user_id),
  approved_at     timestamptz
);

comment on table public.regla_caja_chica is
  'Lineamiento configurable de elegibilidad de Caja Chica (PD-05: umbral S/100 es referencial, '
  'NO usar el historico de S/4,900 como regla general -- pendiente de validar con Contabilidad/Gerencia).';

create table if not exists public.catalogo_estado (
  entity_type varchar(40) not null,
  state_code  varchar(40) not null,
  state_name  varchar(100) not null,
  terminal    boolean not null default false,
  active      boolean not null default true,
  primary key (entity_type, state_code)
);

comment on table public.catalogo_estado is 'Estados permitidos por entidad (referencia; la maquina de estados vive en cada service).';

insert into public.moneda (currency_code, name) values
  ('PEN', 'Sol peruano'),
  ('USD', 'Dolar americano')
on conflict (currency_code) do nothing;

insert into public.catalogo_estado (entity_type, state_code, state_name, terminal) values
  ('REQUERIMIENTO','BORRADOR','Borrador',false),
  ('REQUERIMIENTO','PENDIENTE_APROBACION','Pendiente de aprobacion',false),
  ('REQUERIMIENTO','VERIFICACION_STOCK','Verificacion de stock',false),
  ('REQUERIMIENTO','EN_ATENCION','En atencion',false),
  ('REQUERIMIENTO','ATENCION_PARCIAL','Atencion parcial',false),
  ('REQUERIMIENTO','ATENCION_COMPLETA','Atencion completa',true),
  ('REQUERIMIENTO','RECHAZADO','Rechazado',true),
  ('APROBACION','PENDIENTE','Pendiente',false),
  ('APROBACION','APROBADO','Aprobado',true),
  ('APROBACION','OBSERVADO','Observado',false),
  ('APROBACION','RECHAZADO','Rechazado',true),
  ('SOLICITUD_MAESTRO_PRODUCTO','BORRADOR','Borrador',false),
  ('SOLICITUD_MAESTRO_PRODUCTO','PENDIENTE_APROBACION_LOGISTICA','Pendiente Logistica',false),
  ('SOLICITUD_MAESTRO_PRODUCTO','OBSERVADO','Observado',false),
  ('SOLICITUD_MAESTRO_PRODUCTO','RECHAZADO','Rechazado',true),
  ('SOLICITUD_MAESTRO_PRODUCTO','APROBADO','Aprobado',true),
  ('SOLICITUD_MAESTRO_PRODUCTO','DUPLICADO','Duplicado',true),
  ('RFQ','BORRADOR','Borrador',false),
  ('RFQ','ENVIADA','Enviada',false),
  ('RFQ','RESPONDIDA','Respondida',false),
  ('RFQ','CERRADA','Cerrada',true),
  ('ORDEN_ABASTECIMIENTO','EMITIDA','Emitida',false),
  ('ORDEN_ABASTECIMIENTO','EN_ATENCION','En atencion',false),
  ('ORDEN_ABASTECIMIENTO','RECEPCION_PARCIAL','Recepcion parcial',false),
  ('ORDEN_ABASTECIMIENTO','RECEPCION_COMPLETA','Recepcion completa',false),
  ('ORDEN_ABASTECIMIENTO','PEND_VERIFICACION','Pendiente de verificacion',false),
  ('ORDEN_ABASTECIMIENTO','VERIFICADA','Verificada',true),
  ('ORDEN_ABASTECIMIENTO','ANULADA','Anulada',true),
  ('CAJA_CHICA_REF','PENDIENTE_REGULARIZACION','Pendiente de regularizacion',false),
  ('CAJA_CHICA_REF','EN_REVISION','En revision',false),
  ('CAJA_CHICA_REF','REGULARIZADO','Regularizado',false),
  ('CAJA_CHICA_REF','PENDIENTE_RECEPCION','Pendiente de recepcion',false),
  ('CAJA_CHICA_REF','CERRADO','Cerrado',true),
  ('CAJA_CHICA_REF','OBSERVADO','Observado',false),
  ('CAJA_CHICA_REF','ANULADO','Anulado',true)
on conflict (entity_type, state_code) do nothing;
