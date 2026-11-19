-- Lote 19a (DEC-L070): seleccion inicial (F-LOG-03) y evaluacion de desempeno
-- (F-LOG-07) con plantillas configurables. Cada evaluacion copia nombre, peso
-- y etiqueta de cada criterio: editar la plantilla no cambia la historia.
-- El resultado es informativo: nunca cambia el estado del proveedor.

create table ${schema}.plantilla_evaluacion (
  template_id        uuid primary key default gen_random_uuid(),
  kind               varchar(20) not null check (kind in ('SELECCION', 'DESEMPENO')),
  code               varchar(30) not null,
  version            integer not null default 1,
  name               varchar(150) not null,
  scale_max          numeric(5,2) not null default 10 check (scale_max > 0),
  approval_threshold numeric(5,2) not null default 6 check (approval_threshold >= 0),
  validity_months    integer not null default 12 check (validity_months between 1 and 60),
  alert_days         integer not null default 30 check (alert_days between 0 and 365),
  active             boolean not null default true,
  updated_by         uuid references ${schema}.usuario(user_id),
  updated_at         timestamptz not null default now(),
  check (approval_threshold <= scale_max)
);

-- Una sola plantilla vigente por tipo.
create unique index ux_plantilla_evaluacion_activa on ${schema}.plantilla_evaluacion (kind) where active;

create table ${schema}.plantilla_criterio (
  criterion_id uuid primary key default gen_random_uuid(),
  template_id  uuid not null references ${schema}.plantilla_evaluacion(template_id),
  sort_order   integer not null,
  name         varchar(250) not null,
  weight       numeric(5,2) not null check (weight > 0 and weight <= 100),
  -- [{"score":10,"label":"Si cumple"}, ...]; vacio = puntaje libre de 0 a scale_max.
  options      jsonb not null default '[]'::jsonb,
  active       boolean not null default true
);

create index idx_plantilla_criterio_template on ${schema}.plantilla_criterio (template_id, sort_order);

create table ${schema}.evaluacion_proveedor (
  evaluation_id        uuid primary key default gen_random_uuid(),
  supplier_id          uuid not null references ${schema}.proveedor(supplier_id),
  template_id          uuid not null references ${schema}.plantilla_evaluacion(template_id),
  kind                 varchar(20) not null check (kind in ('SELECCION', 'DESEMPENO')),
  template_code        varchar(30) not null,
  template_version     integer not null,
  evaluated_at         date not null,
  evaluator_user_id    uuid not null references ${schema}.usuario(user_id),
  area                 varchar(120),
  scope                varchar(500),
  user_area_reviewer   varchar(150),
  logistics_conformity varchar(2) check (logistics_conformity in ('SI', 'NO')),
  user_area_conformity varchar(2) check (user_area_conformity in ('SI', 'NO')),
  final_score          numeric(5,2) not null,
  scale_max            numeric(5,2) not null,
  threshold            numeric(5,2) not null,
  result               varchar(30) not null check (result in ('APROBADO', 'NO_APROBADO', 'PENDIENTE_CONFORMIDAD')),
  observations         varchar(2000),
  next_evaluation_date date not null,
  status               varchar(20) not null default 'REGISTRADA' check (status in ('REGISTRADA', 'ANULADA')),
  void_reason          varchar(1000),
  voided_by            uuid references ${schema}.usuario(user_id),
  voided_at            timestamptz,
  created_at           timestamptz not null default now(),
  check (next_evaluation_date >= evaluated_at),
  check (status = 'REGISTRADA' or void_reason is not null)
);

create index idx_evaluacion_proveedor_supplier on ${schema}.evaluacion_proveedor (supplier_id, evaluated_at desc);

create table ${schema}.evaluacion_criterio (
  evaluation_criterion_id uuid primary key default gen_random_uuid(),
  evaluation_id  uuid not null references ${schema}.evaluacion_proveedor(evaluation_id),
  sort_order     integer not null,
  criterion_name varchar(250) not null,
  weight         numeric(5,2) not null,
  score          numeric(5,2) not null check (score >= 0),
  score_label    varchar(250),
  observation    varchar(1000)
);

create index idx_evaluacion_criterio_eval on ${schema}.evaluacion_criterio (evaluation_id, sort_order);

-- Formatos corporativos de la V38.7 (proveedores-integrados.js).
with t as (
  insert into ${schema}.plantilla_evaluacion (kind, code, version, name)
  values ('SELECCION', 'F-LOG-03', 7, 'Selección de proveedor')
  returning template_id
)
insert into ${schema}.plantilla_criterio (template_id, sort_order, name, weight, options)
select t.template_id, c.orden, c.nombre, c.peso, c.opciones::jsonb from t, (values
  (1, '¿Cumple con los requisitos técnicos y/o específicos solicitados?', 50,
   '[{"score":10,"label":"Sí cumple"},{"score":0,"label":"No cumple"}]'),
  (2, '¿Cuenta con sistemas de gestión certificados?', 7,
   '[{"score":10,"label":"Tiene 1 o más certificaciones vigentes (14001, 45001, 9001)"},{"score":5,"label":"Implementación de sistema SST (Ley 29783)"},{"score":0,"label":"No tiene"}]'),
  (3, '¿Tiene competitividad en sus precios?', 15,
   '[{"score":10,"label":"Mejor precio del mercado"},{"score":7,"label":"Mejor (barato) que el promedio del mercado"},{"score":3,"label":"Peor (caro) que el promedio del mercado"},{"score":0,"label":"Muy por encima (+25%) del promedio del mercado"}]'),
  (4, '¿Qué tiempo de atención ofrece?', 10,
   '[{"score":10,"label":"Rápido y eficiente"},{"score":0,"label":"Lento y poco fiable"}]'),
  (5, 'Tiempo en el mercado', 3,
   '[{"score":10,"label":"Más de 5 años"},{"score":0,"label":"Menos de 5 años"}]'),
  (6, '¿Qué condiciones de pago brinda?', 4,
   '[{"score":10,"label":"Crédito superior a 15 días"},{"score":7,"label":"Crédito hasta 15 días"},{"score":3,"label":"Contado o contraentrega"},{"score":0,"label":"Pago adelantado"}]'),
  (7, '¿Ofrece soporte y garantía?', 4,
   '[{"score":10,"label":"Ofrece garantía"},{"score":0,"label":"No ofrece garantía"}]'),
  (8, 'Reparto a oficina', 3,
   '[{"score":10,"label":"Reparto gratuito"},{"score":5,"label":"Reparto con costo adicional"},{"score":0,"label":"No tiene reparto"}]'),
  (9, 'Omisiones tributarias (SUNAT)', 4,
   '[{"score":10,"label":"No tiene omisiones tributarias"},{"score":0,"label":"Tiene omisiones tributarias"}]')
) as c(orden, nombre, peso, opciones);

with t as (
  insert into ${schema}.plantilla_evaluacion (kind, code, version, name)
  values ('DESEMPENO', 'F-LOG-07', 2, 'Evaluación de desempeño y reevaluación')
  returning template_id
)
insert into ${schema}.plantilla_criterio (template_id, sort_order, name, weight, options)
select t.template_id, c.orden, c.nombre, c.peso, c.opciones::jsonb from t, (values
  (1, 'Conformidad de bienes o servicios', 60,
   '[{"score":10,"label":"Conforme"},{"score":0,"label":"No conforme"}]'),
  (2, 'Sin reclamos', 10,
   '[{"score":10,"label":"Ningún reclamo"},{"score":0,"label":"Generó reclamos"}]'),
  (3, 'Soporte y garantía', 5,
   '[{"score":10,"label":"Conforme"},{"score":0,"label":"No conforme"}]'),
  (4, 'Entrega en plazo', 5,
   '[{"score":10,"label":"Dentro del plazo"},{"score":0,"label":"Fuera del plazo"}]'),
  (5, 'Gestión SST', 3,
   '[{"score":10,"label":"Sí tiene"},{"score":0,"label":"No tiene"}]'),
  (6, 'Competitividad de precios', 8,
   '[{"score":10,"label":"Mejor precio del mercado"},{"score":7,"label":"Mejor (barato) que el promedio del mercado"},{"score":3,"label":"Peor (caro) que el promedio del mercado"},{"score":0,"label":"Muy por encima (+25%) del promedio del mercado"}]'),
  (7, 'Condiciones de pago', 3,
   '[{"score":10,"label":"Crédito superior a 15 días"},{"score":7,"label":"Crédito hasta 15 días"},{"score":3,"label":"Contado o contraentrega"},{"score":0,"label":"Pago adelantado"}]'),
  (8, 'Gestión de calidad', 3,
   '[{"score":10,"label":"Sí tiene"},{"score":0,"label":"No tiene"}]'),
  (9, 'Gestión ambiental', 3,
   '[{"score":10,"label":"Sí tiene"},{"score":0,"label":"No tiene"}]')
) as c(orden, nombre, peso, opciones);
