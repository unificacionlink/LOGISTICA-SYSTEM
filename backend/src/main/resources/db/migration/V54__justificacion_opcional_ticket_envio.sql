-- DEC-L093: ajustes pedidos por Logistica.
-- * La justificacion del requerimiento deja de ser obligatoria.
-- * El envio de una Nota de Inventario se registra con el enlace de su ticket
--   (se pasa de picking a envio, sin el paso de embalaje).

alter table ${schema}.requerimiento alter column justification drop not null;

alter table ${schema}.envio add column ticket_url varchar(500);
