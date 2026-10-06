-- 0033_rls_modulo_requerimientos.sql
-- RLS para el PRIMER lote de despliegue (solo modulo Requerimientos): las
-- tablas de catalogo/config que ya existen en este lote, mas las tablas con
-- ambito propio de SOLICITANTE (contacto_externo, requerimiento,
-- requerimiento_item, solicitud_maestro_producto).
--
-- Es un subconjunto de lo que ya esta escrito en 0028_rls_negocio.sql: ese
-- archivo asume que TODAS las tablas de la Fase 1 existen (74 tablas). Como
-- el despliegue va modulo por modulo, este archivo cubre solo las tablas de
-- este primer lote; cuando se agregue el siguiente modulo (Cotizaciones/
-- Compras/Caja Chica) se aplicara el resto de 0028 sobre las tablas nuevas
-- de ese lote (no hay que volver a tocar este archivo).

do $$
declare
  t text;
  catalogos text[] := array[
    'unidad_medida','tipo_producto','familia_producto','subfamilia_producto',
    'producto','producto_variante','producto_imagen',
    'moneda','tipo_cambio','regla_aprobacion','regla_documentaria','regla_penalidad',
    'regla_caja_chica','catalogo_estado','punto_entrega'
  ];
begin
  foreach t in array catalogos loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I_select on public.%I', t, t);
    execute format(
      'create policy %I_select on public.%I for select to authenticated using (true)', t, t
    );
    execute format('drop policy if exists %I_write on public.%I', t, t);
    execute format(
      'create policy %I_write on public.%I for all to authenticated '
      'using (public.current_es_logistica_o_admin()) '
      'with check (public.current_es_logistica_o_admin())', t, t
    );
  end loop;
end $$;

alter table public.contacto_externo enable row level security;
drop policy if exists contacto_externo_select on public.contacto_externo;
create policy contacto_externo_select on public.contacto_externo for select to authenticated
  using (public.current_es_logistica_o_admin());
drop policy if exists contacto_externo_write on public.contacto_externo;
create policy contacto_externo_write on public.contacto_externo for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

alter table public.requerimiento enable row level security;
drop policy if exists requerimiento_select on public.requerimiento;
create policy requerimiento_select on public.requerimiento for select to authenticated
  using (public.current_es_logistica_o_admin() or requester_user_id = public.current_usuario_id());
drop policy if exists requerimiento_insert on public.requerimiento;
create policy requerimiento_insert on public.requerimiento for insert to authenticated
  with check (
    public.current_es_logistica_o_admin()
    or (public.current_tiene_rol(array['SOLICITANTE']) and requester_user_id = public.current_usuario_id())
  );
-- El WITH CHECK se evalua sobre la fila NUEVA: exigir status='BORRADOR' ahi
-- impediria para siempre la transicion BORRADOR->PENDIENTE_APROBACION (el
-- propio "enviar a aprobacion") para el dueno. Se permite mantenerse en
-- BORRADOR (edicion) o pasar a PENDIENTE_APROBACION (envio); cualquier otra
-- transicion (aprobar/rechazar/etc.) requiere LOGISTICA/ADMIN.
drop policy if exists requerimiento_update on public.requerimiento;
create policy requerimiento_update on public.requerimiento for update to authenticated
  using (
    public.current_es_logistica_o_admin()
    or (requester_user_id = public.current_usuario_id() and status = 'BORRADOR')
  )
  with check (
    public.current_es_logistica_o_admin()
    or (requester_user_id = public.current_usuario_id() and status in ('BORRADOR', 'PENDIENTE_APROBACION'))
  );

alter table public.requerimiento_item enable row level security;
drop policy if exists requerimiento_item_select on public.requerimiento_item;
create policy requerimiento_item_select on public.requerimiento_item for select to authenticated
  using (
    public.current_es_logistica_o_admin()
    or exists (
      select 1 from public.requerimiento r
      where r.request_id = requerimiento_item.request_id
        and r.requester_user_id = public.current_usuario_id()
    )
  );
drop policy if exists requerimiento_item_write on public.requerimiento_item;
create policy requerimiento_item_write on public.requerimiento_item for all to authenticated
  using (
    public.current_es_logistica_o_admin()
    or exists (
      select 1 from public.requerimiento r
      where r.request_id = requerimiento_item.request_id
        and r.requester_user_id = public.current_usuario_id()
        and r.status = 'BORRADOR'
    )
  )
  with check (
    public.current_es_logistica_o_admin()
    or exists (
      select 1 from public.requerimiento r
      where r.request_id = requerimiento_item.request_id
        and r.requester_user_id = public.current_usuario_id()
        and r.status = 'BORRADOR'
    )
  );

alter table public.solicitud_maestro_producto enable row level security;
drop policy if exists solicitud_maestro_producto_select on public.solicitud_maestro_producto;
create policy solicitud_maestro_producto_select on public.solicitud_maestro_producto for select to authenticated
  using (public.current_es_logistica_o_admin() or requested_by = public.current_usuario_id());
drop policy if exists solicitud_maestro_producto_insert on public.solicitud_maestro_producto;
create policy solicitud_maestro_producto_insert on public.solicitud_maestro_producto for insert to authenticated
  with check (requested_by = public.current_usuario_id());
drop policy if exists solicitud_maestro_producto_update on public.solicitud_maestro_producto;
create policy solicitud_maestro_producto_update on public.solicitud_maestro_producto for update to authenticated
  using (
    public.current_es_logistica_o_admin()
    or (requested_by = public.current_usuario_id() and status = 'BORRADOR')
  )
  with check (
    public.current_es_logistica_o_admin()
    or (requested_by = public.current_usuario_id() and status = 'BORRADOR')
  );
