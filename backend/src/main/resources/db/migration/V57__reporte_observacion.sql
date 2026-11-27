-- V57: reportes de observaciones desde la pantalla (DEC-L104).
--
-- Cualquier usuario con sesion activa el "modo reporte": la pantalla se vuelve
-- un esqueleto, senala la zona (tarjeta, tabla, fila, boton, campo) o toda la
-- pantalla y describe la observacion. Se guarda el modulo, la pantalla y la
-- ruta de la zona tal como se ve en el esqueleto (solo textos, nunca el HTML).
-- La captura es opcional y va al bucket privado (capture_key). ADMIN las revisa
-- en Administracion y las exporta a Excel.

create table ${schema}.reporte_observacion (
  id              uuid primary key default gen_random_uuid(),
  numero          integer generated always as identity unique,
  created_at      timestamptz not null default now(),
  user_id         uuid not null references ${schema}.usuario(user_id),
  module          varchar(80),
  screen          varchar(200),
  route           varchar(500) not null,
  zone            varchar(500),
  description     varchar(2000) not null,
  capture_key     varchar(300),
  user_agent      varchar(300),
  viewport        varchar(20),
  status          varchar(12) not null default 'PENDIENTE'
                    check (status in ('PENDIENTE', 'EN_REVISION', 'RESUELTO', 'DESCARTADO')),
  response        varchar(1000),
  reviewed_by     uuid references ${schema}.usuario(user_id) on delete set null,
  reviewed_at     timestamptz
);

create index idx_reporte_observacion_fecha on ${schema}.reporte_observacion (created_at desc);
create index idx_reporte_observacion_estado on ${schema}.reporte_observacion (status, created_at desc);
