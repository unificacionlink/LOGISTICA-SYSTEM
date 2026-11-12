-- V28 — Caja chica del proyecto por producto y cantidad (DEC-L054, v0.39.0).
--
-- El solicitante indica en su borrador cuantas unidades de cada producto
-- sugiere comprar con la caja chica del proyecto; el resto sigue la via normal
-- (stock y compra). requerimiento.petty_cash_suggested queda como "algun
-- producto con unidades por caja chica" y la nota sigue siendo una por REQ.

alter table ${schema}.requerimiento_item
  add column petty_cash_qty numeric(14,3) not null default 0,
  add constraint requerimiento_item_petty_cash_qty_check
    check (petty_cash_qty >= 0 and petty_cash_qty <= qty_requested);

-- Los que ya la sugirieron para todo quedan con todo por caja chica.
update ${schema}.requerimiento_item ri set petty_cash_qty = ri.qty_requested
  from ${schema}.requerimiento r
 where r.request_id = ri.request_id and r.petty_cash_suggested;
