-- ============================================================
-- V35 — Correos automaticos (DEC-L066, RF-007, RF-063, ADM-03 de la V38.7)
-- Bandeja de salida: cada aviso se escribe en la misma transaccion que la
-- accion que lo origina y un proceso programado lo envia (Resend). Asi enviar
-- nunca frena la operacion, cada envio queda registrado y los fallos se
-- reintentan.
-- ============================================================

create table if not exists ${schema}.correo_saliente (
  correo_id           uuid primary key default gen_random_uuid(),
  event_type          varchar(40)  not null check (event_type in (
                        'APROBACION_PENDIENTE', 'RECORDATORIO_APROBACION',
                        'REQ_APROBADO', 'REQ_OBSERVADO', 'REQ_RECHAZADO',
                        'DESPACHO_ENVIADO', 'RECEPCION_REGISTRADA')),
  entity_type         varchar(40)  not null,
  entity_id           uuid         not null,
  recipient_user_id   uuid references ${schema}.usuario(user_id),
  to_email            varchar(180) not null,
  subject             varchar(300) not null,
  html                text         not null,
  status              varchar(12)  not null default 'PENDIENTE'
                        check (status in ('PENDIENTE', 'ENVIADO', 'ERROR', 'OMITIDO')),
  attempts            integer      not null default 0,
  next_attempt_at     timestamptz  not null default now(),
  last_error          text,
  provider_message_id varchar(120),
  dedupe_key          varchar(200) unique,
  created_at          timestamptz  not null default now(),
  sent_at             timestamptz
);

create index if not exists ix_correo_saliente_pendiente
  on ${schema}.correo_saliente (next_attempt_at) where status = 'PENDIENTE';
create index if not exists ix_correo_saliente_entidad
  on ${schema}.correo_saliente (entity_type, entity_id);

comment on table ${schema}.correo_saliente is
  'Bandeja de salida de correos (DEC-L066). PENDIENTE -> ENVIADO, o ERROR tras 5 intentos (RF-063); '
  'OMITIDO si el correo no esta configurado. dedupe_key evita repetir el recordatorio del dia.';
