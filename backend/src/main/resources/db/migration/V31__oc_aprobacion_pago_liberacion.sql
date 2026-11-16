-- V31: orden de compra con aprobacion, pago y liberacion (Lote 17, DEC-L061).
--
-- * La OC nace POR_APROBAR: Gerencia o Logistica (Admin de respaldo) la
--   aprueba (pasa a EMITIDA) o la rechaza (queda ANULADA y sus consolidados
--   vuelven a cotizacion). Nadie aprueba la OC que emitio. La decision queda
--   en aprobacion (entity_type = 'COMPRA').
-- * Pago (payment_status): PENDIENTE, ANTICIPO o PAGADO, con constancia desde
--   ANTICIPO. Es un dato de control: no frena la recepcion, solo impide anular.
-- * Liberacion (released_at): Logistica la deja lista para recibir en el
--   almacen, con una nota opcional. Solo una OC aprobada se libera.
--
-- Las OC existentes quedan aprobadas y liberadas en su fecha de emision, para
-- no frenar recepciones en curso.

alter table ${schema}.orden_abastecimiento drop constraint orden_abastecimiento_status_check;
alter table ${schema}.orden_abastecimiento add constraint orden_abastecimiento_status_check
  check (status in (
    'POR_APROBAR','EMITIDA','EN_ATENCION','RECEPCION_PARCIAL','RECEPCION_COMPLETA',
    'PEND_VERIFICACION','VERIFICADA','ANULADA'
  ));
alter table ${schema}.orden_abastecimiento alter column status set default 'POR_APROBAR';

alter table ${schema}.orden_abastecimiento
  add column approved_at timestamptz,
  add column approved_by uuid references ${schema}.usuario(user_id),
  add column payment_status varchar(20) not null default 'PENDIENTE'
    check (payment_status in ('PENDIENTE','ANTICIPO','PAGADO')),
  add column payment_proof_document_id uuid references ${schema}.documento(document_id),
  add column paid_at timestamptz,
  add column paid_by uuid references ${schema}.usuario(user_id),
  add column released_at timestamptz,
  add column released_by uuid references ${schema}.usuario(user_id),
  add column release_note varchar(300),
  add constraint orden_abastecimiento_pago_constancia_check
    check (payment_status = 'PENDIENTE' or payment_proof_document_id is not null),
  add constraint orden_abastecimiento_liberada_aprobada_check
    check (released_at is null or approved_at is not null);

update ${schema}.orden_abastecimiento
   set approved_at = created_at, approved_by = created_by,
       released_at = created_at, released_by = created_by;

alter table ${schema}.documento drop constraint documento_document_type_check;
alter table ${schema}.documento add constraint documento_document_type_check
  check (document_type in (
    'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
    'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA',
    'IMAGEN_PRODUCTO','COMPROBANTE_CAJA_CHICA','CONSTANCIA_PAGO'
  ));

insert into ${schema}.catalogo_estado (entity_type, state_code, state_name, terminal) values
  ('ORDEN_ABASTECIMIENTO','POR_APROBAR','Por aprobar',false)
on conflict (entity_type, state_code) do nothing;

-- Bandeja de OC por aprobar.
create index if not exists idx_orden_abastecimiento_status on ${schema}.orden_abastecimiento (status);
