-- V10__cotizacion_fecha_recepcion.sql
-- V10 (DEC-L020): momento en que se registro la respuesta del proveedor, para
-- medir su tiempo de respuesta en el dashboard (recibida - RFQ creada).
-- Las cotizaciones ya existentes toman su fecha de emision, o la de la RFQ si
-- no la tienen (mejor aproximacion disponible); las nuevas, now().

alter table ${schema}.cotizacion add column received_at timestamptz;

update ${schema}.cotizacion c
   set received_at = coalesce(c.issue_date::timestamptz, q.created_at)
  from ${schema}.rfq q
 where q.rfq_id = c.rfq_id;

alter table ${schema}.cotizacion
  alter column received_at set default now(),
  alter column received_at set not null;
