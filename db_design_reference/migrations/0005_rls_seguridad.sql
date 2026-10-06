-- 0005_rls_seguridad.sql
-- Helper central current_tiene_rol() (mismo patron que RRHH-SYSTEM: current_rol()
-- SECURITY DEFINER, evita recursion de RLS al leer USUARIO/USUARIO_ROL) y RLS de
-- las tablas creadas hasta ahora (empresa, ceco, proyecto, documento*, seguridad).

create or replace function public.current_usuario_id()
returns uuid
language sql stable security definer set search_path = public
as $$
  select user_id from public.usuario where identity_provider_id = auth.uid()::text and active = true;
$$;

comment on function public.current_usuario_id() is
  'Resuelve el user_id de USUARIO a partir del auth.uid() de Supabase Auth.';

create or replace function public.current_tiene_rol(codes text[])
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1
    from public.usuario_rol ur
    join public.rol r on r.role_id = ur.role_id
    join public.usuario u on u.user_id = ur.user_id
    where u.identity_provider_id = auth.uid()::text
      and u.active = true
      and r.active = true
      and r.code = any(codes)
  );
$$;

comment on function public.current_tiene_rol(text[]) is
  'True si el usuario autenticado tiene alguno de los roles dados, en cualquier empresa.';

create or replace function public.current_es_logistica_o_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_tiene_rol(array['LOGISTICA', 'ADMIN']);
$$;

-- EMPRESA / CECO / PROYECTO: catalogos legibles por cualquier autenticado;
-- solo LOGISTICA/ADMIN los mantiene.
alter table public.empresa enable row level security;
drop policy if exists empresa_select on public.empresa;
create policy empresa_select on public.empresa for select to authenticated using (true);
drop policy if exists empresa_write on public.empresa;
create policy empresa_write on public.empresa for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

alter table public.ceco enable row level security;
drop policy if exists ceco_select on public.ceco;
create policy ceco_select on public.ceco for select to authenticated using (true);
drop policy if exists ceco_write on public.ceco;
create policy ceco_write on public.ceco for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

alter table public.proyecto enable row level security;
drop policy if exists proyecto_select on public.proyecto;
create policy proyecto_select on public.proyecto for select to authenticated using (true);
drop policy if exists proyecto_write on public.proyecto;
create policy proyecto_write on public.proyecto for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

-- ROL / PERMISO / ROL_PERMISO: catalogo de seguridad, solo ADMIN escribe.
alter table public.rol enable row level security;
drop policy if exists rol_select on public.rol;
create policy rol_select on public.rol for select to authenticated using (true);
drop policy if exists rol_write on public.rol;
create policy rol_write on public.rol for all to authenticated
  using (public.current_tiene_rol(array['ADMIN']))
  with check (public.current_tiene_rol(array['ADMIN']));

alter table public.permiso enable row level security;
drop policy if exists permiso_select on public.permiso;
create policy permiso_select on public.permiso for select to authenticated using (true);
drop policy if exists permiso_write on public.permiso;
create policy permiso_write on public.permiso for all to authenticated
  using (public.current_tiene_rol(array['ADMIN']))
  with check (public.current_tiene_rol(array['ADMIN']));

alter table public.rol_permiso enable row level security;
drop policy if exists rol_permiso_select on public.rol_permiso;
create policy rol_permiso_select on public.rol_permiso for select to authenticated using (true);
drop policy if exists rol_permiso_write on public.rol_permiso;
create policy rol_permiso_write on public.rol_permiso for all to authenticated
  using (public.current_tiene_rol(array['ADMIN']))
  with check (public.current_tiene_rol(array['ADMIN']));

-- USUARIO: cualquiera ve el directorio basico (para selects de "asignar a", etc.);
-- cada quien edita su propio registro (campos no sensibles se controlan en el
-- service); LOGISTICA/ADMIN puede todo. Los INSERT/UPDATE con fuente=RRHH_SYNC
-- los hace SIEMPRE personal_sync.service con el cliente admin (bypassa RLS);
-- esta policy solo cubre altas/ediciones MANUALES desde la UI (ver 0007_identidad_manual.sql).
alter table public.usuario enable row level security;
drop policy if exists usuario_select on public.usuario;
create policy usuario_select on public.usuario for select to authenticated using (true);
drop policy if exists usuario_update_self on public.usuario;
create policy usuario_update_self on public.usuario for update to authenticated
  using (identity_provider_id = auth.uid()::text or public.current_es_logistica_o_admin())
  with check (identity_provider_id = auth.uid()::text or public.current_es_logistica_o_admin());

alter table public.usuario_rol enable row level security;
drop policy if exists usuario_rol_select on public.usuario_rol;
create policy usuario_rol_select on public.usuario_rol for select to authenticated
  using (user_id = public.current_usuario_id() or public.current_es_logistica_o_admin());
drop policy if exists usuario_rol_write on public.usuario_rol;
create policy usuario_rol_write on public.usuario_rol for all to authenticated
  using (public.current_tiene_rol(array['ADMIN']))
  with check (public.current_tiene_rol(array['ADMIN']));

alter table public.usuario_ceco enable row level security;
drop policy if exists usuario_ceco_select on public.usuario_ceco;
create policy usuario_ceco_select on public.usuario_ceco for select to authenticated
  using (user_id = public.current_usuario_id() or public.current_es_logistica_o_admin());
drop policy if exists usuario_ceco_write on public.usuario_ceco;
create policy usuario_ceco_write on public.usuario_ceco for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

-- DOCUMENTO / DOCUMENTO_RELACION: cualquier autenticado puede subir/ver
-- (la restriccion fina de que entidad puede adjuntar que documento vive en
-- cada modulo de negocio, via document_relacion.entity_type/entity_id).
alter table public.documento enable row level security;
drop policy if exists documento_select on public.documento;
create policy documento_select on public.documento for select to authenticated using (true);
drop policy if exists documento_insert on public.documento;
create policy documento_insert on public.documento for insert to authenticated
  with check (uploaded_by = public.current_usuario_id());

alter table public.documento_relacion enable row level security;
drop policy if exists documento_relacion_select on public.documento_relacion;
create policy documento_relacion_select on public.documento_relacion for select to authenticated using (true);
drop policy if exists documento_relacion_insert on public.documento_relacion;
create policy documento_relacion_insert on public.documento_relacion for insert to authenticated with check (true);
