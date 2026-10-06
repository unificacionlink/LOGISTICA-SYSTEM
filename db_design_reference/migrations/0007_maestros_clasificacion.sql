-- 0007_maestros_clasificacion.sql
-- UNIDAD_MEDIDA, TIPO_PRODUCTO, FAMILIA_PRODUCTO, SUBFAMILIA_PRODUCTO (grupo MAESTROS).

create table if not exists public.unidad_medida (
  uom_id uuid primary key default gen_random_uuid(),
  code   varchar(20) not null unique,  -- UND / PAR / CAJA / MILLAR
  name   varchar(80) not null,
  active boolean not null default true
);

comment on table public.unidad_medida is 'Unidades estandarizadas y homologadas.';

create table if not exists public.tipo_producto (
  product_type_id uuid primary key default gen_random_uuid(),
  code        varchar(2) not null unique,  -- EP = EPP
  name        varchar(100) not null,
  description varchar(220),
  active      boolean not null default true
);

comment on table public.tipo_producto is
  'Catalogo del tipo principal que inicia la codificacion SKU (EP, HE, MA, EQ, etc.).';

create table if not exists public.familia_producto (
  family_id       uuid primary key default gen_random_uuid(),
  product_type_id uuid not null references public.tipo_producto(product_type_id),
  code            varchar(2) not null,  -- correlativo dentro del tipo
  name            varchar(120) not null,
  sort_order      integer,
  active          boolean not null default true,
  unique (product_type_id, code)
);

comment on table public.familia_producto is
  'Familias controladas por tipo de producto. Para EPP quedan 4 familias aprobadas.';

create table if not exists public.subfamilia_producto (
  subfamily_id uuid primary key default gen_random_uuid(),
  family_id    uuid not null references public.familia_producto(family_id),
  code         varchar(2) not null,  -- correlativo dentro de la familia
  name         varchar(120) not null,
  sort_order   integer,
  active       boolean not null default true,
  unique (family_id, code)
);

comment on table public.subfamilia_producto is
  'Subfamilias controladas dentro de cada familia; evita texto libre y duplicidades.';

-- Seed: unidades base y las 4 categorias EPP cerradas (Informe V38 Final,
-- seccion 6: 01 Cabeza/Rostro, 02 Cuerpo, 03 Ext. Superiores, 04 Ext. Inferiores).
insert into public.unidad_medida (code, name) values
  ('UND', 'Unidad'),
  ('PAR', 'Par'),
  ('CAJA', 'Caja'),
  ('MILLAR', 'Millar'),
  ('ND', 'No definido (pendiente completar en el maestro origen)')
on conflict (code) do nothing;

comment on column public.unidad_medida.code is
  'ND es un placeholder explicito: el maestro origen (833 registros consolidados a 725 SKU) '
  'no trae UOM. El catalogo se siembra con ND hasta que se complete el dato real (Informe V38 Final, seccion 6).';

insert into public.tipo_producto (code, name, description) values
  ('EP', 'EPP', 'Equipo de proteccion personal')
on conflict (code) do nothing;

insert into public.familia_producto (product_type_id, code, name, sort_order)
select pt.product_type_id, f.code, f.name, f.sort_order
from public.tipo_producto pt
cross join (values
  ('01', 'CABEZA / ROSTRO', 1),
  ('02', 'CUERPO', 2),
  ('03', 'EXTREMIDADES SUPERIORES', 3),
  ('04', 'EXTREMIDADES INFERIORES', 4)
) as f(code, name, sort_order)
where pt.code = 'EP'
on conflict (product_type_id, code) do nothing;
