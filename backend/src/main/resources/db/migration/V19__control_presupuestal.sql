-- V19__control_presupuestal.sql
-- Lote 15 (DEC-L038, V38.7 REQ-02/REQ-03): control presupuestal al enviar un
-- requerimiento, con excepciones justificadas, y su registro por envio.

-- PYC activa el control por periodo (mandatory de budgetPeriods). Desactivado
-- por defecto, como en la V38.7.
alter table ${schema}.periodo_presupuestal
  add column mandatory_control boolean not null default false;

comment on column ${schema}.periodo_presupuestal.mandatory_control is
  'true = al enviar un REQ del CECO en este semestre se valida contra el presupuesto aprobado.';

-- Resultado del control en cada envio del REQ (budgetValidationHistory).
create table ${schema}.control_presupuestal_req (
  budget_check_id   uuid primary key default gen_random_uuid(),
  request_id        uuid not null references ${schema}.requerimiento(request_id),
  budget_period_id  uuid not null references ${schema}.periodo_presupuestal(budget_period_id),
  budget_id         uuid references ${schema}.presupuesto(budget_id),
  budget_version_id uuid references ${schema}.presupuesto_version(budget_version_id),
  status            varchar(30) not null check (status in ('CUBIERTO','EXCEPCION_JUSTIFICADA')),
  amount            numeric(14,2),
  justification     varchar(1000),
  -- [{productId, sku, solicitado, disponible, exceso, motivo}]; motivo =
  -- SIN_PRESUPUESTO_APROBADO | SKU_NO_PRESUPUESTADO | EXCESO.
  deviations        jsonb not null default '[]'::jsonb,
  checked_by        uuid not null references ${schema}.usuario(user_id),
  checked_at        timestamptz not null default now(),
  check (status = 'CUBIERTO' or justification is not null)
);

create index idx_control_presupuestal_req_request on ${schema}.control_presupuestal_req (request_id, checked_at);
create index idx_control_presupuestal_req_periodo on ${schema}.control_presupuestal_req (budget_period_id)
  where status = 'EXCEPCION_JUSTIFICADA';
