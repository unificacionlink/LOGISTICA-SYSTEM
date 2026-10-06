-- 0006_identidad_manual.sql
-- Restringe el alta/edicion MANUAL de USUARIO (fuente='MANUAL') a empresas que
-- RRHH-SYSTEM todavia no cubre, y solo para LOGISTICA/ADMIN. Ver
-- docs/04_DECISIONES.md, DEC-L002.
--
-- La lista de empresas habilitadas vive en esta tabla (no en CONFIG de la app)
-- porque RLS necesita poder consultarla en el servidor de Postgres; el
-- validator de identidad-manual en la app consulta la misma tabla, nunca una
-- copia hardcodeada.

create table if not exists public.identidad_manual_empresa_habilitada (
  company_id       uuid primary key references public.empresa(company_id),
  motivo           text not null,
  habilitado_desde timestamptz not null default now(),
  habilitado_por   uuid  -- FK a usuario, diferida (0029_fk_deferred.sql)
);

comment on table public.identidad_manual_empresa_habilitada is
  'Empresas para las que se permite alta manual de identidad mientras RRHH-SYSTEM '
  'no tenga su padron activo. Quitar la fila cuando RRHH incorpore la empresa: '
  'el upsert de personal_sync.service migra automaticamente MANUAL -> RRHH_SYNC.';

alter table public.identidad_manual_empresa_habilitada enable row level security;
drop policy if exists identidad_manual_empresa_select on public.identidad_manual_empresa_habilitada;
create policy identidad_manual_empresa_select on public.identidad_manual_empresa_habilitada
  for select to authenticated using (true);
drop policy if exists identidad_manual_empresa_write on public.identidad_manual_empresa_habilitada;
create policy identidad_manual_empresa_write on public.identidad_manual_empresa_habilitada
  for all to authenticated
  using (public.current_tiene_rol(array['ADMIN']))
  with check (public.current_tiene_rol(array['ADMIN']));

-- Seed inicial: METRIN (no existe en RRHH) y GOYDELSAC (estructura sin datos aun).
insert into public.identidad_manual_empresa_habilitada (company_id, motivo)
select company_id, 'RRHH-SYSTEM aun no tiene padron activo para esta empresa (2026-09-15).'
from public.empresa
where code in ('METRIN', 'GOYDELSAC')
on conflict (company_id) do nothing;

-- Reemplaza la policy generica de 0005 por una que distingue fuente=MANUAL vs RRHH_SYNC.
drop policy if exists usuario_update_self on public.usuario;

create policy usuario_update_self on public.usuario for update to authenticated
  using (
    identity_provider_id = auth.uid()::text
    or public.current_es_logistica_o_admin()
  )
  with check (
    identity_provider_id = auth.uid()::text
    or (
      public.current_es_logistica_o_admin()
      and (
        fuente = 'RRHH_SYNC'
        or (
          fuente = 'MANUAL'
          and company_id in (select company_id from public.identidad_manual_empresa_habilitada)
        )
      )
    )
  );

drop policy if exists usuario_insert_manual on public.usuario;
create policy usuario_insert_manual on public.usuario for insert to authenticated
  with check (
    public.current_es_logistica_o_admin()
    and fuente = 'MANUAL'
    and company_id in (select company_id from public.identidad_manual_empresa_habilitada)
  );

comment on policy usuario_insert_manual on public.usuario is
  'Alta manual (fuente=MANUAL) solo por LOGISTICA/ADMIN y solo para empresas aun no '
  'cubiertas por RRHH. El alta con fuente=RRHH_SYNC la hace siempre personal_sync.service '
  'con el cliente admin (bypassa RLS), nunca desde este formulario.';
