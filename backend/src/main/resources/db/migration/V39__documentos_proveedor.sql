-- Lote 19a (DEC-L071): documentos del proveedor con vencimiento. El archivo va
-- al bucket PRIVADO (se descarga solo a traves del backend, con sesion y rol);
-- documento.file_url guarda "privado:<clave>". Nada se borra: se anula con motivo.

create table ${schema}.requisito_documento_proveedor (
  requirement_id uuid primary key default gen_random_uuid(),
  name           varchar(150) not null,
  description    varchar(500),
  -- Obligatorio solo advierte al habilitar (DEC-L069), no bloquea.
  required       boolean not null default false,
  has_expiry     boolean not null default false,
  sort_order     integer not null default 0,
  active         boolean not null default true
);

insert into ${schema}.requisito_documento_proveedor (name, required, has_expiry, sort_order) values
  ('Ficha RUC / consulta SUNAT', true, false, 1),
  ('Ficha de información del proveedor (F-LOG-06)', true, false, 2),
  ('Constancia de cuenta bancaria / CCI', false, false, 3),
  ('Certificación ISO 9001 / 14001 / 45001', false, true, 4),
  ('Fichas técnicas de productos', false, false, 5),
  ('Documentos SSOMA (subcontratistas)', false, true, 6);

create table ${schema}.proveedor_documento (
  supplier_document_id uuid primary key default gen_random_uuid(),
  supplier_id    uuid not null references ${schema}.proveedor(supplier_id),
  document_id    uuid not null references ${schema}.documento(document_id),
  requirement_id uuid references ${schema}.requisito_documento_proveedor(requirement_id),
  evaluation_id  uuid references ${schema}.evaluacion_proveedor(evaluation_id),
  issued_at      date,
  expires_at     date,
  notes          varchar(500),
  status         varchar(20) not null default 'ACTIVO' check (status in ('ACTIVO', 'ANULADO')),
  void_reason    varchar(1000),
  voided_by      uuid references ${schema}.usuario(user_id),
  voided_at      timestamptz,
  created_by     uuid references ${schema}.usuario(user_id),
  created_at     timestamptz not null default now(),
  check (expires_at is null or issued_at is null or expires_at >= issued_at),
  check (status = 'ACTIVO' or void_reason is not null)
);

create index idx_proveedor_documento_supplier on ${schema}.proveedor_documento (supplier_id, status);

alter table ${schema}.documento drop constraint documento_document_type_check;
alter table ${schema}.documento add constraint documento_document_type_check
  check (document_type in (
    'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
    'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA',
    'IMAGEN_PRODUCTO','COMPROBANTE_CAJA_CHICA','CONSTANCIA_PAGO','EVIDENCIA_EMBALAJE',
    'DOC_PROVEEDOR'
  ));
