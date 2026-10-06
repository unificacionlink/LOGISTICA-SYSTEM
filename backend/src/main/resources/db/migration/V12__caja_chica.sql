-- V12 — Lote 9: Caja Chica como ruta de compra del requerimiento (DEC-L003, DEC-L026).
--
-- Basado en db_design_reference/migrations/0010_configuracion_reglas.sql
-- (regla_caja_chica) y 0023_caja_chica.sql (caja_chica_ref/_item), sin RLS.
-- Solo REFERENCIA al aplicativo externo de Caja Chica: nunca duplica su fondo
-- ni su rendicion. Lo comprado ya esta en mano: al registrarlo entra al
-- almacen central y queda reservado para el item del REQ (sigue el despacho
-- existente).

-- ------------------------------------------------------------------ regla
-- regla_caja_chica ya existe desde V1 (0010_configuracion_reglas) sin filas:
-- se le agrega quien/cuando la edito y se siembra la regla corporativa.
alter table ${schema}.regla_caja_chica
  add column updated_by uuid references ${schema}.usuario(user_id),
  add column updated_at timestamptz not null default now();

-- Una sola regla vigente por empresa (y una corporativa).
create unique index uq_regla_caja_chica_vigente
  on ${schema}.regla_caja_chica (coalesce(company_id, '00000000-0000-0000-0000-000000000000'))
  where status = 'VIGENTE';

insert into ${schema}.regla_caja_chica (company_id, rule_name, low_amount_threshold, status)
values (null, 'Regla corporativa (umbral referencial PD-05)', 100, 'VIGENTE');

-- ------------------------------------------------------------------ compra
create table ${schema}.caja_chica_ref (
  petty_cash_ref_id    uuid primary key default gen_random_uuid(),
  petty_cash_code      varchar(50) not null unique,   -- CC-<n>-<EMPRESA>
  external_movement_id varchar(80),                   -- id en el aplicativo de Caja Chica (al regularizar)
  company_id  uuid not null references ${schema}.empresa(company_id),
  request_id  uuid not null references ${schema}.requerimiento(request_id),
  purchase_date date not null,
  -- El proveedor de una compra menor (ferreteria, tienda) rara vez esta en el
  -- maestro: se guarda el RUC/razon social del comprobante y, si el RUC existe
  -- en el maestro, tambien el supplier_id.
  supplier_id   uuid references ${schema}.proveedor(supplier_id),
  supplier_ruc  varchar(20),
  supplier_name varchar(200) not null,
  amount        numeric(14,2) not null check (amount > 0),
  currency_code varchar(3) not null references ${schema}.moneda(currency_code),
  document_type varchar(30) not null check (document_type in ('FACTURA','BOLETA','RECIBO','TICKET','OTRO')),
  document_series varchar(20),
  document_number varchar(40) not null,
  document_id   uuid references ${schema}.documento(document_id),
  duplicate_control_key varchar(250) not null unique,  -- RUC+tipo+serie+numero+importe (RF-029)
  route_reason_code varchar(40) not null
    check (route_reason_code in ('MISMO_DIA','FALLA_PROVEEDOR','BAJO_MONTO','EXCEPCION_AUTORIZADA')),
  route_reason_detail text,
  route_rule_id uuid references ${schema}.regla_caja_chica(petty_cash_rule_id),
  regularization_status varchar(30) not null default 'PENDIENTE_REGULARIZACION'
    check (regularization_status in ('PENDIENTE_REGULARIZACION','REGULARIZADO','OBSERVADO','ANULADO')),
  observed_reason text,
  purchase_user_id uuid not null references ${schema}.usuario(user_id),
  regularized_by   uuid references ${schema}.usuario(user_id),
  regularized_at   timestamptz,
  created_at timestamptz not null default now(),
  check (regularization_status <> 'OBSERVADO' or observed_reason is not null),
  check (route_reason_code <> 'EXCEPCION_AUTORIZADA' or route_reason_detail is not null)
);

comment on table ${schema}.caja_chica_ref is
  'Compra por Caja Chica de un REQ. Referencia al aplicativo externo; no duplica su fondo/rendicion.';

create index idx_caja_chica_ref_request on ${schema}.caja_chica_ref (request_id);
create index idx_caja_chica_ref_status on ${schema}.caja_chica_ref (company_id, regularization_status);

create table ${schema}.caja_chica_item (
  petty_cash_item_id uuid primary key default gen_random_uuid(),
  petty_cash_ref_id  uuid not null references ${schema}.caja_chica_ref(petty_cash_ref_id),
  request_item_id    uuid not null references ${schema}.requerimiento_item(request_item_id),
  product_id  uuid not null references ${schema}.producto(product_id),
  variant_id  uuid references ${schema}.producto_variante(variant_id),
  quantity    numeric(14,3) not null check (quantity > 0),
  unit_cost   numeric(14,4) not null check (unit_cost >= 0),
  line_total  numeric(14,2) not null
);

create index idx_caja_chica_item_request_item on ${schema}.caja_chica_item (request_item_id);

-- ------------------------------------------------------------------ stock
-- Lo comprado por Caja Chica entra al almacen y queda reservado para el item.
alter table ${schema}.reserva_stock drop constraint reserva_stock_origin_check;
alter table ${schema}.reserva_stock
  add constraint reserva_stock_origin_check check (origin in ('VERIFICACION_STOCK','RECEPCION_OC','CAJA_CHICA')),
  add column petty_cash_item_id uuid references ${schema}.caja_chica_item(petty_cash_item_id);

alter table ${schema}.movimiento_inventario drop constraint movimiento_inventario_reference_type_check;
alter table ${schema}.movimiento_inventario
  add constraint movimiento_inventario_reference_type_check
    check (reference_type in ('OC','RECEPCION','SALIDA','AJUSTE','CAJA_CHICA'));

-- Comprobante de la compra por Caja Chica como documento propio.
alter table ${schema}.documento drop constraint documento_document_type_check;
alter table ${schema}.documento add constraint documento_document_type_check
  check (document_type in (
    'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
    'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA',
    'IMAGEN_PRODUCTO','COMPROBANTE_CAJA_CHICA'
  ));
