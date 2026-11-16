-- V32: preparacion de la Nota de Inventario antes del envio (Lote 18, ALM-02
-- de la V38.7, DEC-L062).
--
-- * Picking (picked_at): Logistica confirma que los productos de la NI ya se
--   separaron en el almacen central.
-- * Embalaje (packed_at): cantidad de bultos y evidencia (foto o PDF subido al
--   sistema). Solo una NI piqueada se embala.
-- * El envio exige la NI embalada.
--
-- Son fechas, no estados nuevos: la NI sigue EMITIDA hasta el envio (mismo
-- criterio que la liberacion de la OC, DEC-L061). Las NI existentes quedan
-- piqueadas y embaladas en su fecha de emision, para no frenar envios en curso.

alter table ${schema}.nota_inventario
  add column picked_at timestamptz,
  add column picked_by uuid references ${schema}.usuario(user_id),
  add column packed_at timestamptz,
  add column packed_by uuid references ${schema}.usuario(user_id),
  add column packing_document_id uuid references ${schema}.documento(document_id),
  add constraint nota_inventario_embalada_piqueada_check
    check (packed_at is null or picked_at is not null);

update ${schema}.nota_inventario
   set picked_at = created_at, picked_by = created_by,
       packed_at = created_at, packed_by = created_by;

alter table ${schema}.documento drop constraint documento_document_type_check;
alter table ${schema}.documento add constraint documento_document_type_check
  check (document_type in (
    'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
    'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA',
    'IMAGEN_PRODUCTO','COMPROBANTE_CAJA_CHICA','CONSTANCIA_PAGO','EVIDENCIA_EMBALAJE'
  ));
