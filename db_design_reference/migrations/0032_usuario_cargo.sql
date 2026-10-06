-- 0032_usuario_cargo.sql
-- Agrega usuario.cargo. El diccionario original de Jonathan (03_Diccionario_Datos)
-- no incluye "cargo" en USUARIO, pero el usuario del proyecto pidio explicitamente
-- que la integracion con RRHH trajera "Ubicacion laboral (empresa, CECO, area,
-- cargo)". Columna aditiva, igual criterio que fuente/source_synced_at (0004).

alter table public.usuario
  add column if not exists cargo varchar(150);

comment on column public.usuario.cargo is
  'Cargo/puesto informativo (fuera del diccionario original de Jonathan; agregado por pedido '
  'explicito de datos de RRHH). No se usa para autorizacion -- eso vive en USUARIO_ROL.';
