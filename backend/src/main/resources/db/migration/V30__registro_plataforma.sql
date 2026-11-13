-- V30: registro de la plataforma para el administrador (DEC-L060).
--
-- * registro_actividad: metadata de cada operacion que cambia datos (POST, PUT,
--   PATCH, DELETE bajo /api) y de cada intento de ingreso: quien, que ruta,
--   resultado HTTP, duracion, IP y navegador. Nunca el cuerpo de la peticion
--   (puede traer contrasenas o datos personales).
-- * registro_error: errores inesperados del servidor (5xx) y del navegador.
--
-- Se escriben en segundo plano y por lotes (RegistroPlataforma), fuera de la
-- transaccion de la accion: un fallo al registrar nunca tumba la operacion.
-- Las acciones criticas con antes/despues siguen en log_cambios (RF-064).

create table ${schema}.registro_actividad (
  id          uuid primary key default gen_random_uuid(),
  created_at  timestamptz not null default now(),
  user_id     uuid references ${schema}.usuario(user_id) on delete set null,
  method      varchar(8) not null,
  path        varchar(300) not null,
  module      varchar(40),
  entity_id   uuid,
  status      integer not null,
  duration_ms integer not null,
  ip          varchar(64),
  user_agent  varchar(300),
  -- Dato extra sin secretos: correo intentado al ingresar, codigo de error de la respuesta.
  detail      varchar(300)
);

create index idx_registro_actividad_fecha on ${schema}.registro_actividad (created_at desc);
create index idx_registro_actividad_usuario on ${schema}.registro_actividad (user_id, created_at desc);

create table ${schema}.registro_error (
  id          uuid primary key default gen_random_uuid(),
  created_at  timestamptz not null default now(),
  origin      varchar(12) not null check (origin in ('SERVIDOR', 'NAVEGADOR')),
  user_id     uuid references ${schema}.usuario(user_id) on delete set null,
  method      varchar(8),
  path        varchar(500),
  status      integer,
  error_type  varchar(200),
  message     text,
  stack       text,
  user_agent  varchar(300)
);

create index idx_registro_error_fecha on ${schema}.registro_error (created_at desc);

create index if not exists idx_log_cambios_fecha on ${schema}.log_cambios (created_at desc);
create index if not exists idx_movimiento_inventario_fecha on ${schema}.movimiento_inventario (created_at desc);
