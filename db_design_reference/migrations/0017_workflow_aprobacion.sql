-- 0017_workflow_aprobacion.sql
-- APROBACION, NOTIFICACION (grupo WORKFLOW).

create table if not exists public.aprobacion (
  approval_id uuid primary key default gen_random_uuid(),
  entity_type varchar(40) not null check (entity_type in ('REQUERIMIENTO','COMPRA','CAJA_CHICA','CONVENIO')),
  entity_id   uuid not null,  -- polimorfico segun entity_type, sin FK directa
  approval_rule_id uuid references public.regla_aprobacion(approval_rule_id),
  status      varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','APROBADO','OBSERVADO','RECHAZADO')),
  decided_by  uuid references public.usuario(user_id),
  decided_at  timestamptz,
  comment     text,
  decision_reason      text,  -- obligatorio cuando status = OBSERVADO/RECHAZADO (validado en el service)
  notification_required boolean not null default true,
  check (status not in ('OBSERVADO','RECHAZADO') or decision_reason is not null)
);

comment on table public.aprobacion is 'Aprobacion generica para requerimiento, compra, caja chica o convenio.';

create index if not exists idx_aprobacion_entity on public.aprobacion (entity_type, entity_id);

create table if not exists public.notificacion (
  notification_id uuid primary key default gen_random_uuid(),
  event_type  varchar(60) not null,  -- REQUERIMIENTO_OBSERVADO / REQUERIMIENTO_RECHAZADO / OTROS
  entity_type varchar(40) not null check (entity_type in ('REQUERIMIENTO','COMPRA','OTRO')),
  entity_id   uuid not null,  -- polimorfico, sin FK directa
  channel     varchar(20) not null default 'EMAIL' check (channel in ('EMAIL')),
  recipient_user_id            uuid references public.usuario(user_id),
  recipient_external_contact_id uuid references public.contacto_externo(external_contact_id),
  recipient_email varchar(180) not null,
  subject varchar(250) not null,
  body    text not null,
  status  varchar(30) not null default 'PENDIENTE' check (status in ('PENDIENTE','ENVIANDO','ENVIADA','ERROR')),
  attempt_count integer not null default 0,
  created_at timestamptz not null default now(),
  sent_at    timestamptz,
  error_message text,
  created_by uuid references public.usuario(user_id),
  check (recipient_user_id is not null or recipient_external_contact_id is not null)
);

comment on table public.notificacion is
  'Registro auditable de notificaciones generadas por eventos del ERP (incluye observacion de requerimientos por correo).';

create index if not exists idx_notificacion_status on public.notificacion (status) where status in ('PENDIENTE','ERROR');
