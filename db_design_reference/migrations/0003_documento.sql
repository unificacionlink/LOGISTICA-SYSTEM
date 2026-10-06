-- 0003_documento.sql
-- DOCUMENTO / DOCUMENTO_RELACION (grupo DOCUMENTOS). Se crea temprano porque
-- PRODUCTO_IMAGEN, SOLICITUD_MAESTRO_PRODUCTO, COMUNICACION_PROVEEDOR,
-- COTIZACION y EXTRACCION_DOCUMENTO ya referencian document_id.
-- documento.uploaded_by referencia a USUARIO, aun no existe -> FK diferida
-- en 0029_fk_deferred.sql.

create table if not exists public.documento (
  document_id   uuid primary key default gen_random_uuid(),
  document_type varchar(40) not null
    check (document_type in (
      'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
      'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA'
    )),
  file_name    varchar(220) not null,
  file_url     text not null,          -- ruta en storage; la BD nunca guarda el binario
  mime_type    varchar(100),
  file_hash    varchar(128),           -- control de duplicidad/integridad
  uploaded_by  uuid,                   -- FK a usuario, diferida
  uploaded_at  timestamptz not null default now(),
  status       varchar(30) not null default 'ACTIVO' check (status in ('ACTIVO', 'ANULADO'))
);

comment on table public.documento is
  'Repositorio logico de archivos. El archivo fisico va a storage; aqui solo URL/hash.';

create table if not exists public.documento_relacion (
  document_relation_id uuid primary key default gen_random_uuid(),
  document_id uuid not null references public.documento(document_id),
  entity_type varchar(40) not null
    check (entity_type in (
      'REQUERIMIENTO','RFQ','COTIZACION','ORDEN','RECEPCION','FACTURA','PAGO',
      'INCIDENCIA','SALIDA','NOTA_INVENTARIO','ENVIO','GUIA_REMISION','CONFORMIDAD_ENTREGA'
    )),
  entity_id uuid not null
);

comment on table public.documento_relacion is
  'Permite vincular un mismo archivo a varias entidades (polimorfico por entity_type/entity_id).';

create index if not exists idx_documento_relacion_entity
  on public.documento_relacion (entity_type, entity_id);
