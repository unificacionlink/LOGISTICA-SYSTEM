-- Callback de Flyway: corre antes de CADA migrate (DEC-L016).
-- Red de seguridad para la BD compartida con RRHH-SYSTEM: nunca migrar el ERP
-- Logistico sobre el esquema public (ahi viven las tablas y policies de RRHH).
do $$
begin
  if '${schema}' = 'public' then
    raise exception 'DEC-L016: LOGISTICA no puede migrarse en el esquema public (DB_SCHEMA).';
  end if;
  if current_schema() is distinct from '${schema}' then
    raise exception 'DEC-L016: search_path apunta a % y se esperaba %', current_schema(), '${schema}';
  end if;
end $$;
