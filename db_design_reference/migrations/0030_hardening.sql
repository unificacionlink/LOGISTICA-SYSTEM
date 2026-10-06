-- 0030_hardening.sql
-- Indices adicionales para las consultas mas frecuentes del flujo
-- Requerimiento -> Aprobacion -> Stock -> Ruta -> Cotizacion -> OC.

create index if not exists idx_producto_sku on public.producto (sku_base);
create index if not exists idx_producto_family on public.producto (family_id, subfamily_id) where active = true;
create index if not exists idx_proveedor_producto_lookup on public.proveedor_producto (product_id, variant_id) where active = true;
create index if not exists idx_convenio_proveedor_item_lookup
  on public.convenio_proveedor_item (product_id, variant_id) where active = true;
create index if not exists idx_orden_abastecimiento_supplier on public.orden_abastecimiento (supplier_id);
create index if not exists idx_rfq_supplier on public.rfq (supplier_id, status);
create index if not exists idx_cotizacion_rfq on public.cotizacion (rfq_id, status);
create index if not exists idx_usuario_company on public.usuario (company_id) where active = true;

comment on schema public is
  'ERP Logistico Corporativo TDEM/METRIN/GOYDEL -- Fase 1 (Requerimientos + Compras + Caja Chica). '
  'Ver C:\Users\User\.claude\plans\rol-y-contexto-eres-typed-beaver.md y docs/04_DECISIONES.md.';
