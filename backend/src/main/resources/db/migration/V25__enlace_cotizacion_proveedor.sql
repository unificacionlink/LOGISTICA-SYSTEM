-- V25: enlace unico para que el proveedor cotice sobre las lineas de su RFQ
-- (DEC-L052). Solo se guarda el SHA-256 del token: el enlace se muestra una
-- vez al generarlo. Un enlace vigente por RFQ; generar otro anula el anterior.

create table ${schema}.rfq_enlace (
  link_id      uuid primary key default gen_random_uuid(),
  rfq_id       uuid not null references ${schema}.rfq(rfq_id),
  token_hash   varchar(64) not null unique,
  expires_at   timestamptz not null,
  revoked_at   timestamptz,
  opened_at    timestamptz,
  submitted_at timestamptz,
  created_by   uuid not null references ${schema}.usuario(user_id),
  created_at   timestamptz not null default now()
);

create index idx_rfq_enlace_rfq on ${schema}.rfq_enlace (rfq_id);
create unique index ux_rfq_enlace_vigente on ${schema}.rfq_enlace (rfq_id) where revoked_at is null;

comment on table ${schema}.rfq_enlace is
  'Enlace publico /cotizar/{token} de una RFQ (DEC-L052). token_hash = SHA-256 '
  'hex del token; expires_at = 30 dias; revoked_at al anular o generar otro.';

-- La cotizacion puede venir del enlace del proveedor.
alter table ${schema}.cotizacion drop constraint cotizacion_source_check;
alter table ${schema}.cotizacion add constraint cotizacion_source_check
  check (source in ('OCR', 'MANUAL', 'ENLACE'));
