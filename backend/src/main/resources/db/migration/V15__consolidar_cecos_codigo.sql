-- V15__consolidar_cecos_codigo.sql
-- DEC-L032: el mismo CECO llego de RR.HH. como '0102' y como '102' (Excel le
-- quito el cero). Codigo canonico = numerico rellenado a 4 digitos
-- (common/CodigoCeco). Solo afecta CECOs con codigo de 1-3 digitos:
--   * con par de 4 digitos en la misma empresa: sus usuarios pasan al par
--     (usuario_ceco), se conserva su aprobador si el par no tiene, y se borra
--     SOLO si ninguna otra tabla lo referencia (si no, la migracion falla y
--     no aplica nada, para revisarlo a mano);
--   * sin par: solo se corrige su codigo a 4 digitos.
-- Al 2026-10-09 ninguno de los repetidos tiene requerimientos ni usuarios.

do $$
declare
  d record;
  keep_id uuid;
begin
  for d in
    select ceco_id, company_id, code, active, approver_user_id
      from ${schema}.ceco
     where code ~ '^[0-9]{1,3}$'
  loop
    select ceco_id into keep_id
      from ${schema}.ceco
     where company_id = d.company_id and code = lpad(d.code, 4, '0');

    if keep_id is null then
      update ${schema}.ceco set code = lpad(d.code, 4, '0') where ceco_id = d.ceco_id;
      continue;
    end if;

    insert into ${schema}.usuario_ceco (user_id, ceco_id)
      select user_id, keep_id from ${schema}.usuario_ceco where ceco_id = d.ceco_id
      on conflict do nothing;
    delete from ${schema}.usuario_ceco where ceco_id = d.ceco_id;

    update ${schema}.ceco
       set active = active or d.active,
           approver_user_id = coalesce(approver_user_id, d.approver_user_id)
     where ceco_id = keep_id;

    -- Con requerimientos, puntos de entrega o NI apuntando aqui, el delete
    -- falla por FK y la migracion completa se revierte.
    delete from ${schema}.ceco where ceco_id = d.ceco_id;
  end loop;
end $$;
