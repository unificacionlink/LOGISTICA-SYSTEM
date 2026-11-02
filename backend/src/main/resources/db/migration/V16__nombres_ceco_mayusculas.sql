-- V16__nombres_ceco_mayusculas.sql
-- DEC-L033: nombres de CECO estandarizados en mayusculas, con tildes y sin
-- espacios repetidos (common/CodigoCeco.nombre hace lo mismo al sincronizar,
-- cargar Excel o crear a mano).

update ${schema}.ceco
   set name = upper(regexp_replace(btrim(name), '\s+', ' ', 'g'))
 where name <> upper(regexp_replace(btrim(name), '\s+', ' ', 'g'));
