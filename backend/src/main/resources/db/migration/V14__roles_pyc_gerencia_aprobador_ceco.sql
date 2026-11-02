-- V14__roles_pyc_gerencia_aprobador_ceco.sql
-- Lote 12 (DEC-L030, V38.7): roles PYC y GERENCIA y aprobador asignado por CECO.

insert into ${schema}.rol (code, name) values
  ('PYC', 'PYC / Presupuestos'),
  ('GERENCIA', 'Gerencia')
on conflict (code) do nothing;

update ${schema}.rol set name = 'Aprobador' where code = 'APROBADOR';

-- Un aprobador por CECO (projectAssignments de la V38.7). Null = cualquier
-- APROBADOR/GERENCIA puede decidir los REQ de ese CECO (comportamiento previo).
alter table ${schema}.ceco
  add column approver_user_id    uuid references ${schema}.usuario(user_id),
  add column approver_updated_by uuid references ${schema}.usuario(user_id),
  add column approver_updated_at timestamptz;

comment on column ${schema}.ceco.approver_user_id is
  'Aprobador asignado del CECO (rol APROBADOR o GERENCIA). Lo fija ADMIN.';

-- Quien debe decidir esta aprobacion (copia del aprobador del CECO al enviar).
alter table ${schema}.aprobacion
  add column assigned_to uuid references ${schema}.usuario(user_id);

create index idx_aprobacion_assigned_pendiente
  on ${schema}.aprobacion (assigned_to) where status = 'PENDIENTE';
