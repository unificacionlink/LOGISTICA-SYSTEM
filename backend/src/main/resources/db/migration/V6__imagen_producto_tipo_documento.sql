-- Habilita IMAGEN_PRODUCTO como tipo de documento valido, para que
-- PRODUCTO_IMAGEN pueda referenciar fotos de producto subidas por Logistica
-- (ver docs_base 16_Producto_Imagen_V32: el archivo va a storage externo,
-- aqui solo la URL via DOCUMENTO).
alter table ${schema}.documento drop constraint documento_document_type_check;

alter table ${schema}.documento add constraint documento_document_type_check
  check (document_type in (
    'COTIZACION','OC','FACTURA_PDF','FACTURA_XML','GUIA','REQUERIMIENTO',
    'NOTA_INVENTARIO','TICKET_ENVIO','CONFORMIDAD_ENTREGA','CONSTANCIA','EVIDENCIA',
    'IMAGEN_PRODUCTO'
  ));
