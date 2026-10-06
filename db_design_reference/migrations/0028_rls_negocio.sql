-- 0028_rls_negocio.sql
-- RLS transversal para las tablas de negocio creadas en 0007..0026.
-- Modelo (ver docs/04_DECISIONES.md): LOGISTICA/ADMIN ven y mantienen todo;
-- APROBADOR lee lo operativo para poder decidir aprobaciones; SOLICITANTE solo
-- ve/crea lo propio (sus requerimientos, su solicitud de producto, sus
-- notificaciones, y el catalogo de solo lectura que necesita para elegir SKU).
-- No hay alcance por empresa/CECO mas fino en esta fase (el prototipo V38 le
-- da a LOGISTICA/ADMIN acceso a los 26 modulos sin particionar por empresa).

create or replace function public.current_es_operativo()
returns boolean
language sql stable security definer set search_path = public
as $$
  select public.current_tiene_rol(array['LOGISTICA', 'ADMIN', 'APROBADOR']);
$$;

comment on function public.current_es_operativo() is
  'True si el usuario puede leer informacion operativa (LOGISTICA/ADMIN/APROBADOR).';

-- ===== Tier A: catalogos/maestros (lectura para cualquier autenticado) =====
do $$
declare
  t text;
  catalogos text[] := array[
    'unidad_medida','tipo_producto','familia_producto','subfamilia_producto',
    'producto','producto_variante','producto_imagen',
    'moneda','tipo_cambio','regla_aprobacion','regla_documentaria','regla_penalidad',
    'regla_caja_chica','catalogo_estado',
    'almacen','proveedor','proveedor_empresa','proveedor_producto',
    'convenio_proveedor','convenio_proveedor_item','escala_precio_proveedor',
    'punto_entrega','plantilla_documento'
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

-- ===== Tier B: operativo interno (lectura LOGISTICA/ADMIN/APROBADOR, escritura LOGISTICA/ADMIN) =====
do $$
declare
  t text;
  operativas text[] := array[
    'politica_stock','reserva_stock',
    'consulta_proveedor','consulta_proveedor_item',
    'rfq','rfq_item','rfq_item_origen','comunicacion_proveedor',
    'cotizacion','cotizacion_item',
    'extraccion_documento','extraccion_campo','extraccion_cotizacion_item',
    'necesidad_abastecimiento','consolidacion','consolidacion_item',
    'orden_abastecimiento','orden_item','orden_item_distribucion',
    'caja_chica_ref','caja_chica_item',
    'guia_remision','factura_proveedor','factura_item',
    'distribucion_costo','gestion_pago',
    'verificacion_adquisicion','verificacion_adquisicion_item'
  ];
begin
  foreach t in array operativas loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I_select on public.%I', t, t);
    execute format(
      'create policy %I_select on public.%I for select to authenticated '
      'using (public.current_es_operativo())', t, t
    );
    execute format('drop policy if exists %I_write on public.%I', t, t);
    execute format(
      'create policy %I_write on public.%I for all to authenticated '
      'using (public.current_es_logistica_o_admin()) '
      'with check (public.current_es_logistica_o_admin())', t, t
    );
  end loop;
end $$;

-- MOVIMIENTO_INVENTARIO: solo INSERT (ya se revoco UPDATE/DELETE en 0011).
alter table public.movimiento_inventario enable row level security;
drop policy if exists movimiento_inventario_select on public.movimiento_inventario;
create policy movimiento_inventario_select on public.movimiento_inventario for select to authenticated
  using (public.current_es_operativo());
drop policy if exists movimiento_inventario_insert on public.movimiento_inventario;
create policy movimiento_inventario_insert on public.movimiento_inventario for insert to authenticated
  with check (public.current_es_logistica_o_admin() and created_by = public.current_usuario_id());

-- AUDITORIA: solo INSERT (ya se revoco UPDATE/DELETE en 0026).
alter table public.auditoria enable row level security;
drop policy if exists auditoria_select on public.auditoria;
create policy auditoria_select on public.auditoria for select to authenticated
  using (public.current_es_operativo());
drop policy if exists auditoria_insert on public.auditoria;
create policy auditoria_insert on public.auditoria for insert to authenticated
  with check (user_id = public.current_usuario_id());

-- ===== Tablas con ambito propio de SOLICITANTE =====

alter table public.contacto_externo enable row level security;
drop policy if exists contacto_externo_select on public.contacto_externo;
create policy contacto_externo_select on public.contacto_externo for select to authenticated
  using (public.current_es_operativo());
drop policy if exists contacto_externo_write on public.contacto_externo;
create policy contacto_externo_write on public.contacto_externo for all to authenticated
  using (public.current_es_logistica_o_admin())
  with check (public.current_es_logistica_o_admin());

alter table public.requerimiento enable row level security;
drop policy if exists requerimiento_select on public.requerimiento;
create policy requerimiento_select on public.requerimiento for select to authenticated
  using (public.current_es_operativo() or requester_user_id = public.current_usuario_id());
drop policy if exists requerimiento_insert on public.requerimiento;
create policy requerimiento_insert on public.requerimiento for insert to authenticated
  with check (
    public.current_es_logistica_o_admin()
    or (public.current_tiene_rol(array['SOLICITANTE']) and requester_user_id = public.current_usuario_id())
  );
drop policy if exists requerimiento_update on public.requerimiento;
create policy requerimiento_update on public.requerimiento for update to authenticated
  using (
    public.current_es_logistica_o_admin()
    or (requester_user_id = public.current_usuario_id() and status = 'BORRADOR')
  )
  with check (
    public.current_es_logistica_o_admin()
    or (requester_user_id = public.current_usuario_id() and status = 'BORRADOR')
  );

alter table public.requerimiento_item enable row level security;
drop policy if exists requerimiento_item_select on public.requerimiento_item;
create policy requerimiento_item_select on public.requerimiento_item for select to authenticated
  using (
    public.current_es_operativo()
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
  using (public.current_es_operativo() or requested_by = public.current_usuario_id());
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

-- APROBACION: el decisor/operativo ve todo; el solicitante ve las de sus propios requerimientos.
alter table public.aprobacion enable row level security;
drop policy if exists aprobacion_select on public.aprobacion;
create policy aprobacion_select on public.aprobacion for select to authenticated
  using (
    public.current_es_operativo()
    or (
      entity_type = 'REQUERIMIENTO'
      and exists (
        select 1 from public.requerimiento r
        where r.request_id = aprobacion.entity_id
          and r.requester_user_id = public.current_usuario_id()
      )
    )
  );
drop policy if exists aprobacion_write on public.aprobacion;
create policy aprobacion_write on public.aprobacion for all to authenticated
  using (public.current_tiene_rol(array['LOGISTICA','ADMIN','APROBADOR']))
  with check (public.current_tiene_rol(array['LOGISTICA','ADMIN','APROBADOR']));

-- NOTIFICACION: cada quien ve solo las propias (o LOGISTICA/ADMIN todas); el
-- INSERT lo hace siempre el service con el cliente admin (bypassa RLS).
alter table public.notificacion enable row level security;
drop policy if exists notificacion_select on public.notificacion;
create policy notificacion_select on public.notificacion for select to authenticated
  using (public.current_es_logistica_o_admin() or recipient_user_id = public.current_usuario_id());
