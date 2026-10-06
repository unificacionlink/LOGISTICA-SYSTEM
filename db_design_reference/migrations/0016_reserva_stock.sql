-- 0016_reserva_stock.sql
-- RESERVA_STOCK (grupo INVENTARIO). Se crea aqui (no en 0011) porque depende
-- de REQUERIMIENTO_ITEM. Redefine fn_saldo_stock() para descontar reservas
-- activas -- esta es la version que el modulo `inventario` debe usar para
-- "stock disponible" (RN-001/RN-002).

create table if not exists public.reserva_stock (
  reservation_id uuid primary key default gen_random_uuid(),
  request_item_id uuid not null references public.requerimiento_item(request_item_id),
  warehouse_id uuid not null references public.almacen(warehouse_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  qty_reserved numeric(14,3) not null check (qty_reserved > 0),
  status       varchar(30) not null default 'RESERVADA'
    check (status in ('RESERVADA','DESPACHADA','LIBERADA')),
  reserved_at  timestamptz not null default now()
);

comment on table public.reserva_stock is 'Reserva por requerimiento antes del despacho.';

create index if not exists idx_reserva_stock_producto
  on public.reserva_stock (warehouse_id, product_id, variant_id) where status = 'RESERVADA';

create or replace function public.fn_saldo_stock(
  p_company_id uuid,
  p_warehouse_id uuid,
  p_product_id uuid,
  p_variant_id uuid default null
)
returns numeric
language sql stable
as $$
  select
    coalesce((
      select sum(mi.qty)
      from public.movimiento_inventario mi
      where mi.company_id = p_company_id
        and mi.warehouse_id = p_warehouse_id
        and mi.product_id = p_product_id
        and (mi.variant_id is not distinct from p_variant_id)
    ), 0)
    -
    coalesce((
      select sum(rs.qty_reserved)
      from public.reserva_stock rs
      where rs.warehouse_id = p_warehouse_id
        and rs.product_id = p_product_id
        and (rs.variant_id is not distinct from p_variant_id)
        and rs.status = 'RESERVADA'
    ), 0);
$$;

comment on function public.fn_saldo_stock(uuid, uuid, uuid, uuid) is
  'Stock disponible = saldo del kardex (MOVIMIENTO_INVENTARIO) menos reservas activas (RESERVA_STOCK). '
  'RN-001: el saldo nunca se edita directo.';
