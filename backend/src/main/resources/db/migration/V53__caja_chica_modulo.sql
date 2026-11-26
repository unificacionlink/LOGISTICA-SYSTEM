-- DEC-L092: Caja chica como modulo propio (acuerdo con Logistica, 2026-11-25).
-- El solicitante registra lo que compro con la caja chica de su proyecto (CECO),
-- sin requerimiento ni autorizacion previa y sin revision: cada compra lleva sus
-- lineas (texto libre o producto del catalogo) y la factura es obligatoria, como
-- archivo (foto o PDF) o como enlace. Logistica, Gerencia y el aprobador del CECO
-- ven el consolidado por CECO, mes y solicitante. Reemplaza el flujo de caja chica
-- dentro del requerimiento (DEC-L054), que se retira de la interfaz.

create table ${schema}.caja_chica_compra (
  purchase_id          uuid primary key default gen_random_uuid(),
  purchase_code        varchar(40) not null unique,
  company_id           uuid not null references ${schema}.empresa(company_id),
  ceco_id              uuid not null references ${schema}.ceco(ceco_id),
  purchase_user_id     uuid not null references ${schema}.usuario(user_id),
  purchase_date        date not null,
  supplier_ruc         varchar(11) check (supplier_ruc is null or supplier_ruc ~ '^[0-9]{11}$'),
  supplier_name        varchar(200) not null,
  document_type        varchar(15) not null check (document_type in ('FACTURA','BOLETA','TICKET','RECIBO','OTRO')),
  document_series      varchar(10),
  document_number      varchar(20) not null,
  currency_code        varchar(3) not null default 'PEN' references ${schema}.moneda(currency_code),
  total                numeric(14,2) not null check (total > 0),
  evidence_document_id uuid references ${schema}.documento(document_id),
  evidence_url         varchar(500),
  notes                text,
  status               varchar(10) not null default 'REGISTRADA' check (status in ('REGISTRADA','ANULADA')),
  annulled_at          timestamptz,
  annulled_by          uuid references ${schema}.usuario(user_id),
  annulment_reason     text,
  created_at           timestamptz not null default now(),
  -- La factura es obligatoria: archivo o enlace.
  constraint caja_chica_compra_factura_check check (evidence_document_id is not null or evidence_url is not null),
  constraint caja_chica_compra_anulada_check check ((status = 'ANULADA') = (annulled_at is not null))
);

comment on table ${schema}.caja_chica_compra is
  'DEC-L092: compra con la caja chica del proyecto, registrada por el solicitante con su factura (archivo o enlace).';

-- Un mismo comprobante no se registra dos veces (salvo que el anterior se haya anulado).
create unique index uq_caja_chica_compra_comprobante on ${schema}.caja_chica_compra
  (coalesce(supplier_ruc, upper(supplier_name)), document_type, coalesce(document_series, ''), document_number)
  where status = 'REGISTRADA';
create index idx_caja_chica_compra_ceco_fecha on ${schema}.caja_chica_compra (ceco_id, purchase_date);

create table ${schema}.caja_chica_compra_linea (
  line_id      uuid primary key default gen_random_uuid(),
  purchase_id  uuid not null references ${schema}.caja_chica_compra(purchase_id) on delete cascade,
  product_id   uuid references ${schema}.producto(product_id),
  variant_id   uuid references ${schema}.producto_variante(variant_id),
  description  varchar(300) not null,
  quantity     numeric(14,3) not null check (quantity > 0),
  unit_price   numeric(14,4) not null check (unit_price >= 0),
  line_total   numeric(14,2) not null check (line_total >= 0)
);

create index idx_caja_chica_compra_linea on ${schema}.caja_chica_compra_linea (purchase_id);
