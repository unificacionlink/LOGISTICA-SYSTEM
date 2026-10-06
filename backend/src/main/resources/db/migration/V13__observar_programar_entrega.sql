-- V13 — Lote 10: observar requerimiento, programar entrega y confirmaciones (DEC-L027).
--
-- * RF-006/048: el aprobador puede OBSERVAR un REQ con motivo obligatorio.
--   El REQ vuelve al solicitante (estado OBSERVADO), que lo corrige y lo
--   reenvia (nueva aprobacion PENDIENTE). El motivo vive en `aprobacion`
--   (decision_reason, ya obligatorio para OBSERVADO desde V4).
-- * RF-068: la fecha programada ya existe (requerimiento.scheduled_delivery_date,
--   V1); quien y cuando la fijo queda en log_cambios.
-- * RF-064: las acciones irreversibles (anular OC, cerrar RFQ, programar
--   entrega) se auditan en log_cambios (existe desde V1, sin uso hasta ahora).

alter table ${schema}.requerimiento drop constraint requerimiento_status_check;

alter table ${schema}.requerimiento add constraint requerimiento_status_check
  check (status in (
    'BORRADOR','PENDIENTE_APROBACION','OBSERVADO','VERIFICACION_STOCK','EN_ATENCION',
    'ATENCION_PARCIAL','ATENCION_COMPLETA','RECHAZADO'
  ));

insert into ${schema}.catalogo_estado (entity_type, state_code, state_name, terminal) values
  ('REQUERIMIENTO','OBSERVADO','Observado',false)
on conflict (entity_type, state_code) do nothing;
