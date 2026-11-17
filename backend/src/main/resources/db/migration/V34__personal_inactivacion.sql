-- ============================================================
-- V34 — Padron de personal: inactivacion en lugar de borrado (DEC-L064)
-- Nadie se elimina del padron: se inactiva y queda la fecha.
--   inactivated_by null     = cese que trajo el sync de RR.HH. (o anterior a
--                             V34); el sync lo reactiva si RR.HH. lo vuelve a enviar.
--   inactivated_by not null = lo inactivo un ADMIN; solo un ADMIN lo reactiva
--                             (el sync no lo toca).
-- ============================================================

alter table ${schema}.personal
  add column if not exists inactivated_at timestamptz,
  add column if not exists inactivated_by uuid references ${schema}.usuario(user_id);

-- Los inactivos de antes: su ultima modificacion es la mejor fecha disponible.
update ${schema}.personal set inactivated_at = updated_at where not active and inactivated_at is null;

alter table ${schema}.personal
  add constraint ck_personal_inactivacion check (active = (inactivated_at is null)),
  add constraint ck_personal_inactivado_por check (inactivated_by is null or inactivated_at is not null);

comment on column ${schema}.personal.inactivated_at is
  'Cuando se inactivo (null = activo). Nunca se borra una persona del padron (DEC-L064).';
comment on column ${schema}.personal.inactivated_by is
  'ADMIN que la inactivo; null = cese de RR.HH. El sync no reactiva a quien inactivo un ADMIN.';
