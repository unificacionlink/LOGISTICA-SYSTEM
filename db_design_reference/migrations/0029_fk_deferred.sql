-- 0029_fk_deferred.sql
-- FKs que no se pudieron declarar en la tabla de origen porque la tabla
-- destino se crea en una migracion posterior (dependencias hacia adelante).
-- Cada una esta comentada en el archivo donde se creo la columna.

alter table public.proyecto
  add constraint fk_proyecto_responsible_user
  foreign key (responsible_user_id) references public.usuario(user_id);

alter table public.documento
  add constraint fk_documento_uploaded_by
  foreign key (uploaded_by) references public.usuario(user_id);

alter table public.identidad_manual_empresa_habilitada
  add constraint fk_identidad_manual_empresa_habilitado_por
  foreign key (habilitado_por) references public.usuario(user_id);

alter table public.producto
  add constraint fk_producto_approved_request
  foreign key (approved_request_id) references public.solicitud_maestro_producto(product_master_request_id);

alter table public.necesidad_abastecimiento
  add constraint fk_necesidad_abastecimiento_failed_order
  foreign key (failed_order_id) references public.orden_abastecimiento(order_id);

alter table public.regla_penalidad
  add constraint fk_regla_penalidad_supplier
  foreign key (supplier_id) references public.proveedor(supplier_id);

alter table public.regla_penalidad
  add constraint fk_regla_penalidad_agreement
  foreign key (agreement_id) references public.convenio_proveedor(agreement_id);
