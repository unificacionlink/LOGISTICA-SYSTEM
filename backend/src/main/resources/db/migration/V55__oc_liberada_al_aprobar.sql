-- DEC-L094: la liberacion de la OC dejo de ser un paso. Al aprobarse, la OC
-- queda lista para recibir. Las aprobadas que esperaban liberacion se liberan
-- con la fecha y el usuario de su aprobacion.
update ${schema}.orden_abastecimiento
   set released_at = approved_at,
       released_by = approved_by
 where released_at is null
   and approved_at is not null
   and not services_only
   and status <> 'ANULADA';
