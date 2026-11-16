-- V33: recepcion en destino con diferencias (Lote 18b, ALM-03 de la V38.7,
-- DEC-L063). Flujo de la V38.7 (opSaveDelivery, aiResolve, aiReceiveReturn)
-- con destino ENTREGA DIRECTA: lo recibido queda en el CECO; sin subalmacenes.
--
-- * Recepcion parcial o total por producto, con resultado CONFORME o
--   NO_CONFORME (observacion obligatoria), y la Nota de Inventario firmada
--   (archivo) o el enlace de la conformidad firmada.
-- * Por producto de la salida: recibido = aceptado + observado + devuelto.
--   La atencion del requerimiento cuenta solo lo aceptado (v37ReceivedQty).
-- * Lo observado se resuelve: SUBSANAR (pasa a aceptado) o DEVOLVER (vuelve
--   al almacen central), siempre con enlace de evidencia. Lo devuelto queda
--   EN_TRANSITO hasta que el almacen central lo recibe en cuarentena.
-- * Evidencia de embalaje como enlace (V38.7: packingEvidence).

alter table ${schema}.salida_item
  add column dest_received_qty numeric(14,3) not null default 0,
  add column dest_accepted_qty numeric(14,3) not null default 0,
  add column dest_observed_qty numeric(14,3) not null default 0,
  add column dest_returned_qty numeric(14,3) not null default 0,
  add constraint salida_item_destino_check check (
    dest_accepted_qty >= 0 and dest_observed_qty >= 0 and dest_returned_qty >= 0
    and dest_received_qty = dest_accepted_qty + dest_observed_qty + dest_returned_qty
    and dest_received_qty <= qty);

-- Lo ya recepcionado antes de V33 se tomo como conforme.
update ${schema}.salida_item si
   set dest_received_qty = si.qty, dest_accepted_qty = si.qty
 where exists (select 1 from ${schema}.envio v
                where v.issue_id = si.issue_id and v.status in ('RECEPCIONADO','CERRADO'));

alter table ${schema}.envio drop constraint envio_status_check;
alter table ${schema}.envio add constraint envio_status_check
  check (status in ('PENDIENTE','EN_TRANSITO','RECEPCION_PARCIAL','RECEPCIONADO',
                    'RECEPCIONADO_OBSERVADO','OBSERVADO','CERRADO'));
alter table ${schema}.envio
  add column conformity_status varchar(20) check (conformity_status in ('CONFORME','NO_CONFORME'));
update ${schema}.envio set conformity_status = 'CONFORME' where status in ('RECEPCIONADO','CERRADO');

alter table ${schema}.nota_inventario drop constraint nota_inventario_status_check;
alter table ${schema}.nota_inventario add constraint nota_inventario_status_check
  check (status in ('BORRADOR','EMITIDA','DESPACHADA','RECEPCION_PARCIAL','RECEPCIONADA','ANULADA'));
alter table ${schema}.nota_inventario
  add column packing_evidence_url varchar(500);

create table ${schema}.recepcion_destino (
  destination_receipt_id uuid primary key default gen_random_uuid(),
  shipment_id     uuid not null references ${schema}.envio(shipment_id),
  receipt_number  integer not null,
  client_token    uuid not null unique,  -- un envio del formulario = una recepcion
  result          varchar(20) not null check (result in ('CONFORME','NO_CONFORME')),
  observation     varchar(500),
  conformity_url  varchar(500),
  document_id     uuid references ${schema}.documento(document_id),  -- NI firmada
  received_by     uuid not null references ${schema}.usuario(user_id),
  created_at      timestamptz not null default now(),
  unique (shipment_id, receipt_number),
  constraint recepcion_destino_evidencia_check check (conformity_url is not null or document_id is not null),
  constraint recepcion_destino_observacion_check check (result = 'CONFORME' or observation is not null)
);

create table ${schema}.recepcion_destino_item (
  destination_receipt_item_id uuid primary key default gen_random_uuid(),
  destination_receipt_id uuid not null references ${schema}.recepcion_destino(destination_receipt_id),
  issue_item_id   uuid not null references ${schema}.salida_item(issue_item_id),
  qty             numeric(14,3) not null check (qty > 0),
  unique (destination_receipt_id, issue_item_id)
);

create table ${schema}.resolucion_destino (
  resolution_id   uuid primary key default gen_random_uuid(),
  shipment_id     uuid not null references ${schema}.envio(shipment_id),
  issue_item_id   uuid not null references ${schema}.salida_item(issue_item_id),
  qty             numeric(14,3) not null check (qty > 0),
  action          varchar(20) not null check (action in ('SUBSANAR','DEVOLVER')),
  evidence_url    varchar(500) not null,
  status          varchar(20) not null check (status in ('RESUELTA','EN_TRANSITO','RECIBIDA_OBSERVADA')),
  created_by      uuid not null references ${schema}.usuario(user_id),
  created_at      timestamptz not null default now(),
  received_by     uuid references ${schema}.usuario(user_id),
  received_at     timestamptz,
  return_evidence_url varchar(500),
  constraint resolucion_destino_estado_check check (
    (action = 'SUBSANAR' and status = 'RESUELTA')
    or (action = 'DEVOLVER' and status in ('EN_TRANSITO','RECIBIDA_OBSERVADA'))),
  constraint resolucion_destino_retorno_check check (
    status <> 'RECIBIDA_OBSERVADA' or (received_at is not null and return_evidence_url is not null))
);

create index idx_recepcion_destino_envio on ${schema}.recepcion_destino (shipment_id);
create index idx_resolucion_destino_envio on ${schema}.resolucion_destino (shipment_id);
create index idx_resolucion_destino_estado on ${schema}.resolucion_destino (status);
