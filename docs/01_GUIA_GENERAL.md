# Guia general — ERP Logistico Corporativo

## Que es

ERP interno de logistica para TDEM, METRIN y GOYDEL. Fase 1: Requerimientos +
Compras (con Caja Chica como ruta del mismo flujo). Reemplaza el prototipo
V38 Final (HTML + localStorage) entregado por Jonathan, que era solo para
demo/UAT, por un backend productivo real.

## Roles

- **SOLICITANTE** — crea requerimientos, ve su tracking, puede pedir alta de
  SKU nuevo.
- **LOGISTICA** — verifica stock, decide ruta de abastecimiento, gestiona
  proveedores/cotizaciones/compras/caja chica.
- **APROBADOR** — decide los requerimientos de los CECOs que tiene asignados
  (o de cualquiera sin aprobador asignado). Nunca los propios.
- **GERENCIA** — decide en el ambito de sus CECOs; ve el dashboard. Aprobara
  presupuestos y ordenes de compra (lotes 14 y 17).
- **PYC** — administra presupuestos y precios de venta (lotes 13–15). Los
  precios de venta por empresa ya se registran en el Catalogo (DEC-L035).
- **ADMIN** — todo lo de LOGISTICA + usuarios/roles/configuracion/reglas +
  identidad manual (METRIN/GOYDEL no cubierto por RRHH).

## Referencias

- Material fuente de Jonathan: `docs_base/` (gitignored; xlsx/pdf/html).
- Plan de implementacion Fase 1:
  `C:\Users\User\.claude\plans\rol-y-contexto-eres-typed-beaver.md`.
- Decisiones de arquitectura y negocio: `docs/04_DECISIONES.md`.
