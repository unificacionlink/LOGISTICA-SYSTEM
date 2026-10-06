-- V4__aprobaciones.sql
-- Lote 2: modulo Aprobaciones. Basada en
-- db_design_reference/migrations/0017_workflow_aprobacion.sql, sin la tabla
-- NOTIFICACION (envio de correo fuera de alcance, no hay SMTP configurado)
-- y sin RLS (DEC-L008: autorizacion en la capa de servicio de Spring).

create table ${schema}.aprobacion (
  approval_id uuid primary key default gen_random_uuid(),
  entity_type varchar(40) not null check (entity_type in ('REQUERIMIENTO','COMPRA','CAJA_CHICA','CONVENIO')),
  entity_id   uuid not null,  -- polimorfico segun entity_type, sin FK directa
  approval_rule_id uuid references ${schema}.regla_aprobacion(approval_rule_id),
  status      varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','APROBADO','OBSERVADO','RECHAZADO')),
  decided_by  uuid references ${schema}.usuario(user_id),
  decided_at  timestamptz,
  comment     text,
  decision_reason text,  -- obligatorio cuando status = OBSERVADO/RECHAZADO (validado en el service tambien)
  created_at  timestamptz not null default now(),
  check (status not in ('OBSERVADO','RECHAZADO') or decision_reason is not null)
);

comment on table ${schema}.aprobacion is
  'Aprobacion generica para requerimiento, compra, caja chica o convenio. '
  'Lote 2 solo usa entity_type=REQUERIMIENTO.';

create index idx_aprobacion_entity on ${schema}.aprobacion (entity_type, entity_id);
