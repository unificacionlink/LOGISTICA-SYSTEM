-- V8__almacen_recepcion_despacho.sql
-- Lote 6 (Almacen Central, DEC-L017): recepcion de OC (RF-030/032/033),
-- despacho con Nota de Inventario (RF-072/073/074) y envio al destino
-- (RF-075/076/077/078/080). Columnas fieles a 03_Diccionario_Datos del Excel
-- maestro V38 (grupos ALMACEN, SALIDAS, LOGISTICA), recortadas a lo usado.
-- Todo el stock sigue saliendo del kardex (movimiento_inventario, RN-001):
-- una recepcion inserta ENTRADA, un despacho inserta SALIDA.

-- ------------------------------------------------------------
-- Recepcion de ordenes de compra en almacen
-- ------------------------------------------------------------
create table ${schema}.recepcion (
  receipt_id     uuid primary key default gen_random_uuid(),
  order_id       uuid not null references ${schema}.orden_abastecimiento(order_id),
  warehouse_id   uuid not null references ${schema}.almacen(warehouse_id),
  receipt_number integer not null,  -- secuencia por orden (1, 2, 3...)
  receipt_date   timestamptz not null default now(),
  status         varchar(30) not null
    check (status in ('PARCIAL','COMPLETA','OBSERVADA')),
  registration_source varchar(20) not null default 'MANUAL'
    check (registration_source in ('MANUAL','CARGA_MASIVA')),
  observation    text,
  received_by    uuid not null references ${schema}.usuario(user_id),
  unique (order_id, receipt_number)
);

comment on table ${schema}.recepcion is
  'Ingreso fisico contra una OC. Varias recepciones por OC hasta completar (RF-032). '
  'CARGA_MASIVA queda listo para RF-031 (Lote 7).';

create table ${schema}.recepcion_item (
  receipt_item_id uuid primary key default gen_random_uuid(),
  receipt_id      uuid not null references ${schema}.recepcion(receipt_id),
  order_item_id   uuid not null references ${schema}.orden_item(order_item_id),
  qty_received    numeric(14,3) not null check (qty_received > 0),
  condition_status varchar(30) not null default 'CONFORME'
    check (condition_status in ('CONFORME','OBSERVADO')),
  observation     text
);

comment on table ${schema}.recepcion_item is
  'Solo cantidad ACEPTADA (entra a stock y descuenta el pendiente de la OC). Lo '
  'danado/faltante no se recibe: se registra como incidencia_recepcion (DEC-L017).';

create index idx_recepcion_order on ${schema}.recepcion (order_id);
create index idx_recepcion_item_order_item on ${schema}.recepcion_item (order_item_id);

create table ${schema}.incidencia_recepcion (
  receipt_issue_id uuid primary key default gen_random_uuid(),
  receipt_id      uuid not null references ${schema}.recepcion(receipt_id),
  order_item_id   uuid not null references ${schema}.orden_item(order_item_id),
  receipt_item_id uuid references ${schema}.recepcion_item(receipt_item_id),  -- null si no se acepto nada del item
  issue_type      varchar(40) not null
    check (issue_type in ('FALTANTE','DANO','PRODUCTO_ERRADO','OTRO')),
  qty_affected    numeric(14,3) check (qty_affected is null or qty_affected > 0),
  description     text not null,
  supplier_action varchar(40) check (supplier_action in ('REPONER','CAMBIO','NOTA_CREDITO')),
  status          varchar(30) not null default 'ABIERTA'
    check (status in ('ABIERTA','EN_GESTION','CERRADA')),
  created_at      timestamptz not null default now(),
  created_by      uuid not null references ${schema}.usuario(user_id),
  closed_at       timestamptz
);

comment on column ${schema}.incidencia_recepcion.issue_type is
  'DANO sin enye a proposito (el diccionario dice DANO con enye): evita problemas '
  'de codificacion en CHECK/JSON; la UI muestra "Dano" con enye.';

create index idx_incidencia_recepcion_order_item on ${schema}.incidencia_recepcion (order_item_id);

create table ${schema}.guia_remision (
  guide_id    uuid primary key default gen_random_uuid(),
  order_id    uuid references ${schema}.orden_abastecimiento(order_id),
  receipt_id  uuid references ${schema}.recepcion(receipt_id),
  shipment_id uuid,  -- FK a envio, agregada abajo
  issuer_type varchar(20) not null check (issuer_type in ('PROVEEDOR','EMPRESA')),
  issuer_ruc  varchar(11) not null,
  series      varchar(10) not null,
  number      varchar(20) not null,
  issue_date  date not null,
  document_id uuid references ${schema}.documento(document_id),
  unique (issuer_ruc, series, number)
);

-- ------------------------------------------------------------
-- Salida de inventario + Nota de Inventario (despacho de un requerimiento)
-- ------------------------------------------------------------
create table ${schema}.salida (
  issue_id     uuid primary key default gen_random_uuid(),
  company_id   uuid not null references ${schema}.empresa(company_id),
  warehouse_id uuid not null references ${schema}.almacen(warehouse_id),
  issue_type   varchar(30) not null
    check (issue_type in ('CONSUMO_INTERNO','VENTA_INTERCOMPANY','VENTA_TERCERO','PRESTAMO','DEVOLUCION')),
  ceco_id      uuid references ${schema}.ceco(ceco_id),
  request_id   uuid references ${schema}.requerimiento(request_id),
  requires_invoice boolean not null default false,
  status       varchar(30) not null default 'EMITIDA'
    check (status in ('BORRADOR','EMITIDA','DESPACHADA','CERRADA')),
  issued_at    timestamptz not null default now(),
  created_by   uuid not null references ${schema}.usuario(user_id)
);

create table ${schema}.salida_item (
  issue_item_id uuid primary key default gen_random_uuid(),
  issue_id      uuid not null references ${schema}.salida(issue_id),
  product_id    uuid not null references ${schema}.producto(product_id),
  variant_id    uuid references ${schema}.producto_variante(variant_id),
  qty           numeric(14,3) not null check (qty > 0),
  request_item_id uuid references ${schema}.requerimiento_item(request_item_id),  -- RF-072
  purchase_cost_reference numeric(14,4)
);

comment on column ${schema}.salida_item.request_item_id is
  'RF-072: obligatorio cuando la salida viene de un requerimiento (validado en el service). '
  'Permite calcular atencion parcial/completa por linea (RF-079).';

create index idx_salida_request on ${schema}.salida (request_id);
create index idx_salida_item_request_item on ${schema}.salida_item (request_item_id);

create table ${schema}.nota_inventario (
  inventory_note_id   uuid primary key default gen_random_uuid(),
  note_number         integer not null,        -- correlativo por empresa
  inventory_note_code varchar(50) not null,    -- NI-<n>-<EMPRESA>
  company_id          uuid not null references ${schema}.empresa(company_id),
  request_id          uuid not null references ${schema}.requerimiento(request_id),
  issue_id            uuid not null unique references ${schema}.salida(issue_id),
  origin_warehouse_id uuid not null references ${schema}.almacen(warehouse_id),
  destination_ceco_id uuid references ${schema}.ceco(ceco_id),
  delivery_point_id   uuid references ${schema}.punto_entrega(delivery_point_id),
  movement_type       varchar(40) not null
    check (movement_type in ('TRANSFERENCIA_CECO','CONSUMO','VENTA','PRESTAMO','DEVOLUCION')),
  picking_date        date not null default current_date,
  picking_user_id     uuid not null references ${schema}.usuario(user_id),
  item_state          varchar(20) not null default 'NUEVO'
    check (item_state in ('NUEVO','USADO','REACONDICIONADO','OTRO')),
  total_value_snapshot numeric(14,3),
  total_item_qty      numeric(14,3),
  package_count       integer check (package_count is null or package_count > 0),
  comments            text,
  document_id         uuid references ${schema}.documento(document_id),  -- PDF (Lote 7)
  status              varchar(30) not null default 'EMITIDA'
    check (status in ('BORRADOR','EMITIDA','DESPACHADA','RECEPCIONADA','ANULADA')),
  issued_at           timestamptz,
  created_by          uuid not null references ${schema}.usuario(user_id),
  created_at          timestamptz not null default now(),
  unique (company_id, note_number),
  unique (company_id, inventory_note_code)
);

comment on table ${schema}.nota_inventario is
  'RF-073/074: un requerimiento puede tener varias NI (entregas parciales sin crear '
  'un REQ nuevo). El detalle es la salida_item de su salida (no se duplican lineas).';

create index idx_nota_inventario_request on ${schema}.nota_inventario (request_id);

-- ------------------------------------------------------------
-- Envio (despacho de requerimiento; ABASTECIMIENTO queda listo)
-- ------------------------------------------------------------
create table ${schema}.envio (
  shipment_id       uuid primary key default gen_random_uuid(),
  shipment_type     varchar(30) not null
    check (shipment_type in ('ABASTECIMIENTO','DESPACHO_REQUERIMIENTO')),
  order_id          uuid references ${schema}.orden_abastecimiento(order_id),
  request_id        uuid references ${schema}.requerimiento(request_id),
  issue_id          uuid references ${schema}.salida(issue_id),
  inventory_note_id uuid unique references ${schema}.nota_inventario(inventory_note_id),
  delivery_point_id uuid references ${schema}.punto_entrega(delivery_point_id),
  delivery_mode     varchar(30) not null
    check (delivery_mode in ('AGENCIA','PROVEEDOR_ENTREGA','RECOJO','TERRESTRE_NORMAL','COURIER','PROPIO')),
  carrier_name      varchar(150),
  tracking_code     varchar(80),
  package_count     integer check (package_count is null or package_count > 0),
  declared_weight   numeric(12,3),
  currency_code     varchar(3) references ${schema}.moneda(currency_code),
  shipping_cost_net   numeric(14,2),  -- RF-076: valor que exporta el Tracking (sin IGV)
  shipping_tax_amount numeric(14,2),
  shipping_cost_total numeric(14,2),
  ship_date         date,
  expected_date     date,
  status            varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','EN_TRANSITO','RECEPCIONADO','OBSERVADO','CERRADO')),
  received_at       timestamptz,  -- RF-078: recepcion en DESTINO (no la de compra)
  received_by       uuid references ${schema}.usuario(user_id),
  delivery_observation text,
  created_by        uuid not null references ${schema}.usuario(user_id),
  created_at        timestamptz not null default now(),
  check (shipment_type <> 'ABASTECIMIENTO' or order_id is not null),
  check (shipment_type <> 'DESPACHO_REQUERIMIENTO'
         or (request_id is not null and issue_id is not null and inventory_note_id is not null))
);

create index idx_envio_request on ${schema}.envio (request_id);

alter table ${schema}.guia_remision
  add constraint fk_guia_remision_envio
  foreign key (shipment_id) references ${schema}.envio(shipment_id);

-- ------------------------------------------------------------
-- Origen de las reservas (DEC-L017)
-- ------------------------------------------------------------
-- VERIFICACION_STOCK: stock existente apartado al verificar el REQ (Lote 3).
-- RECEPCION_OC: lo comprado para un REQ, apartado al recibirlo. Compras solo
-- descuenta del pendiente de compra las de VERIFICACION_STOCK (las de
-- RECEPCION_OC ya estan contadas como cantidad ordenada).
alter table ${schema}.reserva_stock
  add column origin varchar(30) not null default 'VERIFICACION_STOCK'
    check (origin in ('VERIFICACION_STOCK','RECEPCION_OC')),
  add column receipt_item_id uuid references ${schema}.recepcion_item(receipt_item_id);

create index idx_reserva_stock_request_item on ${schema}.reserva_stock (request_item_id);
