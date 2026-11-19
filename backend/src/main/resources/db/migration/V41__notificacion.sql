-- DEC-L076: avisos al solicitante dentro de la plataforma (campana del menu).
-- Reemplazan los correos de cambio de estado de su requerimiento.
create table ${schema}.notificacion (
  notification_id uuid primary key default gen_random_uuid(),
  user_id         uuid not null references ${schema}.usuario (user_id) on delete cascade,
  request_id      uuid references ${schema}.requerimiento (request_id) on delete cascade,
  kind            varchar(40) not null check (kind in (
                    'REQ_APROBADO', 'REQ_OBSERVADO', 'REQ_RECHAZADO', 'REQ_PARCIAL', 'REQ_COMPLETO',
                    'DESPACHO_ENVIADO', 'RECEPCION_REGISTRADA')),
  title           varchar(200) not null,
  body            varchar(500),
  created_at      timestamptz not null default now(),
  read_at         timestamptz
);

create index idx_notificacion_usuario on ${schema}.notificacion (user_id, created_at desc);
create index idx_notificacion_no_leida on ${schema}.notificacion (user_id) where read_at is null;
