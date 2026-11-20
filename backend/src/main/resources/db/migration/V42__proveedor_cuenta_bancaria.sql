-- DEC-L078: cuentas bancarias del proveedor (pago y detraccion).
-- Dato sensible: solo Logistica y Admin lo ven y editan; nunca sale en la lista de
-- proveedores, en las cotizaciones ni en el enlace publico del proveedor.
create table ${schema}.proveedor_cuenta_bancaria (
  bank_account_id uuid primary key default gen_random_uuid(),
  supplier_id     uuid not null references ${schema}.proveedor (supplier_id) on delete cascade,
  bank_name       varchar(80) not null,
  account_type    varchar(20) not null check (account_type in ('AHORROS', 'CORRIENTE', 'DETRACCION', 'OTRA')),
  currency_code   varchar(3) not null references ${schema}.moneda (currency_code),
  account_number  varchar(40) not null,
  cci             varchar(30),
  is_primary      boolean not null default false,
  created_at      timestamptz not null default now(),
  created_by      uuid references ${schema}.usuario (user_id),
  updated_at      timestamptz,
  updated_by      uuid references ${schema}.usuario (user_id)
);

create index idx_cuenta_bancaria_proveedor on ${schema}.proveedor_cuenta_bancaria (supplier_id);
create unique index ux_cuenta_bancaria_numero
  on ${schema}.proveedor_cuenta_bancaria (supplier_id, bank_name, account_number);
