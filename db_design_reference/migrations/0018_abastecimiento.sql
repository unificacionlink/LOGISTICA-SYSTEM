-- 0018_abastecimiento.sql
-- NECESIDAD_ABASTECIMIENTO, CONSOLIDACION, CONSOLIDACION_ITEM (grupo
-- ABASTECIMIENTO). Incluye los campos V36 de decision de ruta (Excel
-- 19_Ruta_Req_CajaChica_V36). failed_order_id referencia ORDEN_ABASTECIMIENTO
-- (0022, posterior) -> FK diferida en 0029_fk_deferred.sql.

create table if not exists public.necesidad_abastecimiento (
  supply_need_id uuid primary key default gen_random_uuid(),
  request_item_id uuid not null references public.requerimiento_item(request_item_id),
  company_id   uuid not null references public.empresa(company_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  qty_request_gap     numeric(14,3) not null,  -- faltante del requerimiento
  qty_replenishment    numeric(14,3) not null default 0,  -- reposicion hasta stock objetivo (RN-002)
  qty_total_to_supply  numeric(14,3) not null,
  route  varchar(30) not null check (route in ('STOCK','CONVENIO','RFQ','CAJA_CHICA')),
  status varchar(40) not null default 'PENDIENTE_CONSOLIDAR',
  created_at timestamptz not null default now(),

  -- V36: decision de ruta (Excel 19_Ruta_Req_CajaChica_V36).
  estimated_amount     numeric(14,2),
  route_reason_code     varchar(40)
    check (route_reason_code in ('STOCK','CONVENIO','NORMAL','MISMO_DIA','FALLA_PROVEEDOR','BAJO_MONTO','EXCEPCION_AUTORIZADA')),
  route_rule_id         uuid references public.regla_caja_chica(petty_cash_rule_id),
  route_decided_by      uuid references public.usuario(user_id),
  route_decided_at      timestamptz,
  failed_order_id       uuid,  -- FK a orden_abastecimiento, diferida
  supplier_failure_reason text
);

comment on table public.necesidad_abastecimiento is
  'Faltante calculado = requerimiento + stock objetivo - stock disponible (RN-002).';

create index if not exists idx_necesidad_abastecimiento_route on public.necesidad_abastecimiento (route, status);

create table if not exists public.consolidacion (
  consolidation_id uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.empresa(company_id),
  product_id uuid not null references public.producto(product_id),
  variant_id uuid references public.producto_variante(variant_id),
  uom_id     uuid not null references public.unidad_medida(uom_id),
  total_qty  numeric(14,3) not null,
  status     varchar(30) not null default 'PENDIENTE' check (status in ('PENDIENTE','CONSULTANDO','RFQ','CERRADA')),
  created_by uuid not null references public.usuario(user_id),
  created_at timestamptz not null default now()
);

comment on table public.consolidacion is
  'Agrupa necesidades por Empresa+Producto+Variante+Unidad antes de consultar/cotizar (RN-004).';

create table if not exists public.consolidacion_item (
  consolidation_item_id uuid primary key default gen_random_uuid(),
  consolidation_id uuid not null references public.consolidacion(consolidation_id),
  supply_need_id   uuid not null references public.necesidad_abastecimiento(supply_need_id),
  request_item_id  uuid not null references public.requerimiento_item(request_item_id),
  ceco_id    uuid not null references public.ceco(ceco_id),
  project_id uuid references public.proyecto(project_id),
  qty_contributed numeric(14,3) not null check (qty_contributed > 0)
);

comment on table public.consolidacion_item is
  'Trazabilidad de cada necesidad dentro del consolidado (RN-004: preserva REQ/CECO/proyecto de origen).';
