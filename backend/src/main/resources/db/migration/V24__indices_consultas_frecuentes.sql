-- V24: indices para las busquedas por llave foranea que hoy recorren la tabla
-- completa. Con los datos actuales (decenas de filas) no se nota; evita que el
-- detalle de RFQ, el comparativo, la recepcion, el despacho y el control
-- presupuestal se vuelvan lentos cuando crezcan los datos. Solo agrega indices:
-- no cambia tablas ni datos.

-- Lineas de una RFQ (detalle de RFQ, comparativo, preparar solicitudes).
create index if not exists idx_rfq_item_rfq on ${schema}.rfq_item (rfq_id);

-- Lo cotizado a cada linea de RFQ (consolidados, comparativo, cantidad cotizada).
create index if not exists idx_cotizacion_item_rfq_item on ${schema}.cotizacion_item (rfq_item_id);

-- Items de una salida (nota de inventario, entregas, estado de atencion).
create index if not exists idx_salida_item_issue on ${schema}.salida_item (issue_id);

-- Items de una recepcion (historial de recepciones de una OC).
create index if not exists idx_recepcion_item_receipt on ${schema}.recepcion_item (receipt_id);

-- Requerimientos de un CECO por fecha (control presupuestal y cumplimiento, DEC-L038).
create index if not exists idx_requerimiento_ceco_fecha on ${schema}.requerimiento (ceco_id, required_date);

-- RFQ abierta de una empresa con un proveedor (comparativo, preparar solicitudes).
create index if not exists idx_rfq_company_supplier on ${schema}.rfq (company_id, supplier_id);

-- Cotizaciones y ordenes de un proveedor (ficha del proveedor, ultimo costo).
create index if not exists idx_cotizacion_supplier on ${schema}.cotizacion (supplier_id);
create index if not exists idx_orden_abastecimiento_supplier on ${schema}.orden_abastecimiento (supplier_id);

-- Usuarios asignados a un CECO (la llave primaria empieza por user_id).
create index if not exists idx_usuario_ceco_ceco on ${schema}.usuario_ceco (ceco_id);
