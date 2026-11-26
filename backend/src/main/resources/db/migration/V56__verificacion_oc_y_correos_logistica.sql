-- DEC-L101: Logistica recibe correo cuando se emite un requerimiento y cuando se aprueba.
alter table ${schema}.correo_saliente drop constraint if exists correo_saliente_event_type_check;
alter table ${schema}.correo_saliente add constraint correo_saliente_event_type_check check (event_type in (
  'APROBACION_PENDIENTE', 'RECORDATORIO_APROBACION',
  'REQ_ENVIADO', 'REQ_APROBADO', 'REQ_OBSERVADO', 'REQ_RECHAZADO',
  'DESPACHO_ENVIADO', 'RECEPCION_REGISTRADA'));

-- DEC-L102: firma escaneada de cada usuario (bucket privado), para los V°B° del F-LOG-08.
alter table ${schema}.usuario add column if not exists firma_key varchar(300);

comment on column ${schema}.usuario.firma_key is
  'Clave en el bucket privado de la firma escaneada del usuario (PNG/JPG). La sube ADMIN en su ficha.';

-- DEC-L102: F-LOG-08 Verificacion de adquisiciones por orden de compra. Una por OC,
-- cuando la OC esta en RECEPCION_COMPLETA. Y = conforme, F = no conforme, null = no aplica.
create table if not exists ${schema}.verificacion_oc (
  order_id            uuid primary key references ${schema}.orden_abastecimiento(order_id),
  verified_at         timestamptz not null default now(),
  verified_by         uuid not null references ${schema}.usuario(user_id),
  specialist_user_id  uuid references ${schema}.usuario(user_id),
  conclusion          text not null,
  updated_at          timestamptz not null default now()
);

comment on table ${schema}.verificacion_oc is
  'F-LOG-08: verificacion de lo recibido de una OC. verified_by firma el V°B° Logistica; '
  'specialist_user_id el V°B° del area solicitante o especialista.';

create table if not exists ${schema}.verificacion_oc_item (
  order_item_id   uuid primary key references ${schema}.orden_item(order_item_id),
  order_id        uuid not null references ${schema}.verificacion_oc(order_id),
  functionality   char(1) check (functionality in ('Y', 'F')),
  standards       char(1) check (standards in ('Y', 'F')),
  specifications  char(1) not null check (specifications in ('Y', 'F')),
  observation     varchar(500)
);

create index if not exists idx_verificacion_oc_item_order on ${schema}.verificacion_oc_item (order_id);

-- El F-LOG-08 se archiva en el repositorio de documentos como los demas oficiales (DEC-L091).
alter table ${schema}.repositorio_documento drop constraint if exists repositorio_documento_doc_type_check;
alter table ${schema}.repositorio_documento add constraint repositorio_documento_doc_type_check
  check (doc_type in ('REQUERIMIENTO', 'ORDEN_COMPRA', 'NOTA_INVENTARIO', 'VERIFICACION_OC'));
