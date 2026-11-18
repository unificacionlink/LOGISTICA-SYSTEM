-- DEC-L068: prioridades con tiempo de atencion (SLA) en dias habiles.
--   URGENTE 2, APROVISIONAMIENTO 10, IMPLEMENTACION 20.
-- Critica y Urgente pasan a Urgente; Normal a Aprovisionamiento. Los REQ ya
-- creados conservan su fecha requerida: solo cambia la etiqueta.

alter table ${schema}.requerimiento drop constraint if exists requerimiento_priority_check;

update ${schema}.requerimiento set priority = 'URGENTE' where priority = 'CRITICA';
update ${schema}.requerimiento set priority = 'APROVISIONAMIENTO' where priority = 'NORMAL';

alter table ${schema}.requerimiento alter column priority set default 'APROVISIONAMIENTO';
alter table ${schema}.requerimiento add constraint requerimiento_priority_check
  check (priority in ('URGENTE', 'APROVISIONAMIENTO', 'IMPLEMENTACION'));

-- Dia en que el REQ se envio a aprobacion: desde ahi corre el tiempo de atencion.
alter table ${schema}.requerimiento add column submitted_at timestamptz;

update ${schema}.requerimiento r
   set submitted_at = coalesce(
         (select min(a.created_at) from ${schema}.aprobacion a
           where a.entity_type = 'REQUERIMIENTO' and a.entity_id = r.request_id),
         r.created_at)
 where r.status <> 'BORRADOR';
