-- V23__req_desde_presupuesto.sql
-- DEC-L049: al aprobar un presupuesto se genera un requerimiento en BORRADOR por
-- cada mes programado. El REQ guarda de que presupuesto, version y mes salio
-- (trazabilidad y para no duplicarlo si el presupuesto se vuelve a aprobar).

alter table ${schema}.requerimiento
  add column budget_id uuid references ${schema}.presupuesto(budget_id),
  add column budget_version_id uuid references ${schema}.presupuesto_version(budget_version_id),
  add column budget_month smallint check (budget_month between 1 and 6),
  add constraint requerimiento_presupuesto_completo
    check ((budget_id is null) = (budget_month is null) and (budget_id is null) = (budget_version_id is null));

create unique index ux_requerimiento_presupuesto_mes
  on ${schema}.requerimiento (budget_id, budget_month) where budget_id is not null;

comment on column ${schema}.requerimiento.budget_month is
  'Mes 1..6 del semestre del presupuesto del que se genero el borrador (DEC-L049).';
