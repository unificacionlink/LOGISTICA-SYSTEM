-- DEC-L091: repositorio de documentos (observacion 9 de Logistica). Cada PDF
-- oficial que emite el ERP se archiva una vez en el bucket privado, en
-- repositorio/<TIPO>/<EMPRESA>/<AAAA-MM>/: el requerimiento al aprobarse, la OC
-- al aprobarse (con firmas) y la Nota de Inventario al emitirse y, si se anula,
-- tambien su version anulada. Lo archiva una tarea programada que recupera lo
-- pendiente (si el almacenamiento falla, lo reintenta en la siguiente vuelta).

create table ${schema}.repositorio_documento (
  archive_id   uuid primary key default gen_random_uuid(),
  doc_type     varchar(30) not null check (doc_type in ('REQUERIMIENTO','ORDEN_COMPRA','NOTA_INVENTARIO')),
  entity_id    uuid not null,
  doc_code     varchar(60) not null,
  company_id   uuid not null references ${schema}.empresa(company_id),
  period       char(7) not null check (period ~ '^[0-9]{4}-[0-9]{2}$'),
  version_key  varchar(30) not null,
  storage_key  varchar(300) not null,
  size_bytes   integer,
  archived_at  timestamptz not null default now(),
  unique (doc_type, entity_id, version_key)
);

comment on table ${schema}.repositorio_documento is
  'DEC-L091: copia archivada (bucket privado) de cada PDF oficial: tipo, empresa, mes y version.';

create index idx_repositorio_filtro on ${schema}.repositorio_documento (period, doc_type, company_id);
