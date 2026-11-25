-- DEC-L087: suplente del aprobador por CECO. Cuando el aprobador asignado falta
-- (vacaciones, campo), el suplente tambien puede decidir los requerimientos del
-- CECO y recibe los mismos avisos. No reemplaza al aprobador: ambos pueden
-- decidir; Gerencia y Admin siguen siendo el respaldo general.

alter table ${schema}.ceco
  add column backup_approver_user_id uuid references ${schema}.usuario(user_id),
  add constraint ceco_suplente_distinto_check
    check (backup_approver_user_id is null or backup_approver_user_id is distinct from approver_user_id);

comment on column ${schema}.ceco.backup_approver_user_id is
  'DEC-L087: suplente del aprobador del CECO; decide y recibe los avisos igual que el aprobador.';

create index idx_ceco_suplente on ${schema}.ceco(backup_approver_user_id)
  where backup_approver_user_id is not null;
