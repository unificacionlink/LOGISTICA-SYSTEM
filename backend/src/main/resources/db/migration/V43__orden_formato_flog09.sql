-- DEC-L079: la orden de compra sale con el formato F-LOG-09.

-- Datos de cada empresa para la cabecera del formato (el RUC ya existia). Logo y
-- firmas escaneadas viven en el bucket PRIVADO (DEC-L071): la BD guarda solo la clave.
alter table ${schema}.empresa
  add column address                varchar(250),
  add column phone                  varchar(40),
  add column logo_key               varchar(300),
  add column signature_manager_key  varchar(300),
  add column signature_buyer_key    varchar(300);

alter table ${schema}.empresa
  add constraint ck_empresa_ruc check (ruc is null or ruc ~ '^[0-9]{11}$');

update ${schema}.empresa
   set ruc = '20522095797',
       address = 'JR. TARICA 5202-URB. VILLA DEL NORTE-LIMA - LIMA - LOS OLIVOS',
       phone = '922257019'
 where code = 'TDEMSRL';

-- Condiciones de la OC: como se calcula el IGV (se elige en cada orden), plazo
-- de entrega y especificaciones adicionales. Las OC previas se registraron sin
-- IGV (tax_amount = 0): quedan como NO_AFECTO para que sus totales no cambien.
alter table ${schema}.orden_abastecimiento
  add column tax_mode         varchar(15) not null default 'NO_AFECTO',
  add column delivery_term    varchar(120),
  add column additional_specs varchar(2000);

alter table ${schema}.orden_abastecimiento
  alter column tax_mode set default 'MAS_IGV',
  add constraint ck_orden_tax_mode check (tax_mode in ('MAS_IGV', 'IGV_INCLUIDO', 'NO_AFECTO'));
