-- V18__presupuestos.sql
-- Lote 14 (DEC-L036, V38.7 PRE-01..PRE-04): periodos presupuestales por CECO y
-- semestre, presupuesto por SKU x 6 meses con emision/aprobacion/revisiones y
-- precios congelados al aprobar (versiones).

-- Periodo habilitado por PYC (budgetPeriods). Cerrado = todo solo consulta.
create table ${schema}.periodo_presupuestal (
  budget_period_id uuid primary key default gen_random_uuid(),
  ceco_id     uuid not null references ${schema}.ceco(ceco_id),
  year        integer not null check (year between 2020 and 2100),
  semester    smallint not null check (semester in (1, 2)),
  closed      boolean not null default false,
  created_by  uuid not null references ${schema}.usuario(user_id),
  created_at  timestamptz not null default now(),
  updated_by  uuid references ${schema}.usuario(user_id),
  updated_at  timestamptz,
  unique (ceco_id, year, semester)
);

comment on table ${schema}.periodo_presupuestal is
  'Periodo presupuestal por CECO, anio y semestre (6 meses). Lo habilita PYC; cerrado = solo consulta.';

create table ${schema}.presupuesto (
  budget_id         uuid primary key default gen_random_uuid(),
  budget_code       varchar(30) not null unique,
  budget_period_id  uuid not null references ${schema}.periodo_presupuestal(budget_period_id),
  requester_user_id uuid not null references ${schema}.usuario(user_id),
  -- Aprobador del CECO copiado al emitir (como aprobacion.assigned_to).
  approver_user_id  uuid references ${schema}.usuario(user_id),
  status            varchar(20) not null default 'BORRADOR'
    check (status in ('BORRADOR','EMITIDO','APROBADO','OBSERVADO','MODIFICADO','ANULADO')),
  observations      varchar(500),
  decision_reason   varchar(500),
  issued_at         timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table ${schema}.presupuesto is
  'Presupuesto semestral de un CECO. Uno no anulado por periodo. Las lineas son la version de trabajo; '
  'lo aprobado vive en presupuesto_version (current_version_id = vigente).';

-- Un solo presupuesto no anulado por periodo (CECO + semestre).
create unique index ux_presupuesto_periodo_activo
  on ${schema}.presupuesto (budget_period_id) where status <> 'ANULADO';

-- Version de trabajo: SKU base x cantidades de los 6 meses del semestre.
create table ${schema}.presupuesto_linea (
  budget_id  uuid not null references ${schema}.presupuesto(budget_id) on delete cascade,
  product_id uuid not null references ${schema}.producto(product_id),
  qty_m1 numeric(14,3) not null default 0 check (qty_m1 >= 0),
  qty_m2 numeric(14,3) not null default 0 check (qty_m2 >= 0),
  qty_m3 numeric(14,3) not null default 0 check (qty_m3 >= 0),
  qty_m4 numeric(14,3) not null default 0 check (qty_m4 >= 0),
  qty_m5 numeric(14,3) not null default 0 check (qty_m5 >= 0),
  qty_m6 numeric(14,3) not null default 0 check (qty_m6 >= 0),
  primary key (budget_id, product_id),
  check (qty_m1 + qty_m2 + qty_m3 + qty_m4 + qty_m5 + qty_m6 > 0)
);

-- Cada aprobacion congela una version (priceSnapshots de la V38.7): cantidades,
-- precio de venta vigente y ultimo precio de compra registrado en PEN.
create table ${schema}.presupuesto_version (
  budget_version_id uuid primary key default gen_random_uuid(),
  budget_id   uuid not null references ${schema}.presupuesto(budget_id),
  version_no  integer not null check (version_no > 0),
  approved_by uuid not null references ${schema}.usuario(user_id),
  approved_at timestamptz not null default now(),
  unique (budget_id, version_no)
);

create table ${schema}.presupuesto_version_linea (
  budget_version_id uuid not null references ${schema}.presupuesto_version(budget_version_id),
  product_id uuid not null references ${schema}.producto(product_id),
  qty_m1 numeric(14,3) not null check (qty_m1 >= 0),
  qty_m2 numeric(14,3) not null check (qty_m2 >= 0),
  qty_m3 numeric(14,3) not null check (qty_m3 >= 0),
  qty_m4 numeric(14,3) not null check (qty_m4 >= 0),
  qty_m5 numeric(14,3) not null check (qty_m5 >= 0),
  qty_m6 numeric(14,3) not null check (qty_m6 >= 0),
  sale_price      numeric(14,4) not null check (sale_price > 0),
  purchase_price  numeric(14,4) check (purchase_price >= 0),
  purchase_status varchar(20) not null
    check (purchase_status in ('CONGELADO','SIN_COMPRA','NO_VALORIZABLE_PEN')),
  purchase_order_id uuid references ${schema}.orden_abastecimiento(order_id),
  primary key (budget_version_id, product_id)
);

comment on column ${schema}.presupuesto_version_linea.purchase_status is
  'CONGELADO = ultima OC en PEN; SIN_COMPRA = no hay OC del SKU; NO_VALORIZABLE_PEN = la ultima OC no es en PEN. Nunca se inventa precio.';

-- Version aprobada vigente: un presupuesto en revision (MODIFICADO) sigue
-- rigiendo con su ultima version aprobada (DEC-L030).
alter table ${schema}.presupuesto
  add column current_version_id uuid references ${schema}.presupuesto_version(budget_version_id);
