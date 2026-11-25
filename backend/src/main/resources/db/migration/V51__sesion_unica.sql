-- DEC-L090: un usuario, una sesion. Cada ingreso genera una sesion nueva y la
-- guarda aqui; el token lleva su id (claim "sid") y el filtro rechaza los tokens
-- de sesiones anteriores ("Tu sesion se abrio en otro dispositivo"). Cerrar sesion
-- la rota a un valor que ningun token tiene. null = nadie ingreso desde este cambio
-- (los tokens previos siguen validos hasta vencer).

alter table ${schema}.usuario add column session_id uuid;

comment on column ${schema}.usuario.session_id is
  'DEC-L090: sesion vigente del usuario (sesion unica); los tokens con otro sid se rechazan.';
