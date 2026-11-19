# Plan — Módulo de Proveedores integral (Lote 19a, v0.55.0)

Decisiones: DEC-L069 (ciclo y ficha), DEC-L070 (selección y evaluación F-LOG),
DEC-L071 (documentos privados). Pendientes de negocio: PD-09..PD-16 en
`04_DECISIONES.md`.

## Diagnóstico (antes del lote)

| Existía y se conservó | Faltaba |
|---|---|
| `proveedor` (V7): RUC único global, razón social, un contacto, tipo, entrega, `active` | Estados de solicitud, nombre comercial, ubicación, condiciones comerciales, fechas de alta/decisión, auditoría al crear/editar |
| `proveedor_producto` (V20): habilitación por producto que usa Compras, automática al cotizar o emitir OC | Editarla desde el proveedor (solo desde Catálogo) |
| `proveedor_equivalencia` (V26) y su sección en la ficha | — |
| `documento`, `Auditoria`, `Descargas` | Documentos del proveedor, vencimientos, bucket privado |
| Clasificación del catálogo (tipo/familia) | Categorías del proveedor |
| RFQ, cotización, OC, recepción e incidencias por `supplier_id` | Historial y desempeño por proveedor |
| — | Plantillas, evaluaciones, vigencias, indicadores, búsqueda, ficha con pestañas |

Riesgos de compatibilidad atendidos:
- Compras (combobox, OCR del comparativo, cobertura) lee solo `active`. Un
  check de la BD impide `active` sin `HABILITADO`, y `GET /api/proveedores`
  conserva su contrato.
- Los existentes quedan `HABILITADO` (default de la columna); el alta nueva fija
  `BORRADOR` de forma explícita, así los `insert` de los tests siguen igual.

## Etapas

| # | Etapa | Estado |
|---|---|---|
| 1 | Ficha y registro: V37, campos nuevos, alta como borrador, edición auditada, categorías y productos, lista nueva, ficha (General) | Hecho |
| 2 | Incorporación y selección: ciclo con advertencias y motivo, V38 con F-LOG-03 v7, panel de evaluación y pestaña Selección | Hecho |
| 3 | Evaluaciones F-LOG-07: historial, detalle por criterio, anulación, configuración de plantillas | Hecho |
| 4 | Vigencias y alertas: Vigente / Por vencer / Vencida / Sin evaluación, indicador "Por reevaluar"; documentos (V39, bucket privado) con vencimiento | Hecho |
| 5 | Historial comercial y desempeño leídos de Compras/Almacén | Hecho |
| 6 | Indicadores, UX y documentación | Hecho |

## Indicadores

Se calculan con datos reales:
- Solicitudes enviadas y respondidas, y días de respuesta.
- Cotizaciones, órdenes de compra y monto por moneda.
- Productos comprados con su precio histórico.
- Cantidades atendidas (aceptado / ordenado).
- Plazo real (de la liberación a la primera recepción).
- Recepciones observadas.
- Incidencias por tipo, días de cierre y tasa de adjudicación.

Son referenciales o están pendientes:
- **Entrega en el plazo ofrecido**: es referencial. Compara la liberación más el plazo de la cotización contra la primera recepción, y solo cuenta las OC que tienen plazo. Queda así hasta que la OC registre una fecha comprometida (PD-14).
- **Montos en una sola moneda**: no hay tipo de cambio registrado, así que cada monto se muestra en su moneda.
- **Diferencias en destino**: no se atribuyen al proveedor.

## Acción de despliegue

- Crear el bucket **privado** `logistica-privado` en el Storage de RR.HH., o el nombre que se ponga en `STORAGE_PRIVATE_BUCKET` en Render.
- Las migraciones V37 a V39 se aplican solas al arrancar.

## Pruebas

`ProveedoresTest` cubre:
- El ciclo completo y los permisos.
- Que los proveedores migrados quedan habilitados.
- La nota F-LOG-03 (8.63) y la copia de pesos cuando se edita la plantilla.
- La anulación y la vigencia vencida, que no cambia el estado.
- Los bordes de la vigencia.
- Los documentos privados con requisitos y vencimiento.
- El historial comercial sobre un flujo real de REQ → OC → recepción.
- Categorías y productos.

`MigracionesFlywayTest` cuenta 39 migraciones.
