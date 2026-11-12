-- V27 — Caja chica del proyecto (DEC-L054).
--
-- La caja chica es de cada proyecto: el solicitante puede sugerirla en su
-- requerimiento, Logistica autoriza la ruta en Necesidades, el proyecto compra
-- con su fondo (fuera del sistema) y el solicitante sube el comprobante.
-- Lo comprado se queda en el proyecto: no entra al almacen central y cuenta
-- como atendido cuando Logistica regulariza (verifica el comprobante).

-- Sugerencia del solicitante.
alter table ${schema}.requerimiento
  add column petty_cash_suggested boolean not null default false,
  add column petty_cash_suggestion_note varchar(300);

-- Nuevo motivo de ruta: lo sugiere el solicitante.
alter table ${schema}.necesidad_compra drop constraint necesidad_compra_suggested_reason_check;
alter table ${schema}.necesidad_compra add constraint necesidad_compra_suggested_reason_check
  check (suggested_reason in ('BAJO_MONTO','MISMO_DIA','FALLA_PROVEEDOR','SOLICITANTE'));

alter table ${schema}.caja_chica_ref drop constraint caja_chica_ref_route_reason_code_check;
alter table ${schema}.caja_chica_ref add constraint caja_chica_ref_route_reason_code_check
  check (route_reason_code in ('MISMO_DIA','FALLA_PROVEEDOR','BAJO_MONTO','EXCEPCION_AUTORIZADA','SOLICITANTE'));

-- Donde quedo lo comprado: en el proyecto (desde DEC-L054) o en el almacen
-- central (compras anteriores, que entraron al stock y se despachan).
alter table ${schema}.caja_chica_ref
  add column destination varchar(20) not null default 'PROYECTO'
    check (destination in ('PROYECTO','ALMACEN'));
update ${schema}.caja_chica_ref set destination = 'ALMACEN';
