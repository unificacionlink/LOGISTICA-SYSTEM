-- DEC-L088: anular una Nota de Inventario emitida, con motivo, mientras no tenga
-- envio registrado (lo despachado aun no salio del almacen). La anulacion
-- devuelve lo despachado al stock (kardex DEVOLUCION con referencia a la salida),
-- vuelve a reservarlo para el requerimiento y deja la nota y su salida en ANULADA,
-- visibles en la lista con su motivo.

alter table ${schema}.salida drop constraint salida_status_check;
alter table ${schema}.salida add constraint salida_status_check
  check (status in ('BORRADOR','EMITIDA','DESPACHADA','CERRADA','ANULADA'));

alter table ${schema}.nota_inventario
  add column annulled_at timestamptz,
  add column annulled_by uuid references ${schema}.usuario(user_id),
  add column annulment_reason text,
  add constraint nota_inventario_anulada_check
    check ((status = 'ANULADA') = (annulled_at is not null and annulment_reason is not null));

comment on column ${schema}.nota_inventario.annulment_reason is
  'DEC-L088: motivo de la anulacion (obligatorio); tambien queda en log_cambios.';
