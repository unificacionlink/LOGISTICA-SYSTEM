"""
seed_catalogo_sku.py

Genera db_design_reference/seed/0001_catalogo_producto.sql a partir de la hoja
13_Catalogo_SKU de docs_base/ERP_BASE_DATOS_FINAL_V38_MAESTRO_SKU_CONSOLIDADO.xlsx
(725 SKU consolidados desde 833 registros origen, V38 Final).

No ejecuta nada contra la base de datos: solo lee el Excel y escribe SQL
estatico versionable (mismo criterio que los seeds de RRHH-SYSTEM).

MAPEO DE COLUMNAS (verificado contra los datos reales del Excel, fila por fila
-- ver docs/04_DECISIONES.md para la pregunta abierta a validar con Jonathan):

  Hoja 13_Catalogo_SKU          ->  BD (03_Diccionario_Datos)
  COD_FAMILIA / FAMILIA_FINAL   ->  TIPO_PRODUCTO.code / .name   (nivel 1 del SKU, 2 chars: AC/EP/EQ/HE/MA/MU)
  COD_SUBFAMILIA / SUBFAMILIA_FINAL -> FAMILIA_PRODUCTO.code / .name (nivel 2, 2 chars, anidado en tipo)
  PRODUCTO_BASE / DESCRIPCION / COD_PRODUCTO_FINAL -> PRODUCTO (nivel 3)

  SUBFAMILIA_PRODUCTO (nivel 3 formal del diccionario, NOT NULL en PRODUCTO)
  NO tiene dato de origen: la hoja de Jonathan solo llega a 2 niveles de
  clasificacion (familia+subfamilia) antes del producto individual. Se crea
  UNA subfamilia "00 - GENERAL" por cada familia_producto como marcador de
  paso, para no inventar una granularidad que el origen no tiene. Ajustar
  cuando Jonathan defina subfamilias reales.

  UOM: el maestro origen NO trae unidad de medida (confirmado en el Informe
  V38 Final, seccion 6: "el aplicativo muestra N/D hasta completar el dato
  maestro"). Se usa el codigo UND_ND ('N/D') sembrado en 0007_maestros_clasificacion.sql
  como placeholder explicito, NUNCA se asume 'UND' por defecto.

Uso:
    python scripts/seed_catalogo_sku.py
"""
import re
import sys
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
XLSX_PATH = ROOT / "docs_base" / "ERP_BASE_DATOS_FINAL_V38_MAESTRO_SKU_CONSOLIDADO.xlsx"
OUT_PATH = ROOT / "db_design_reference" / "seed" / "0001_catalogo_producto.sql"

EXPECTED_ROWS = 725


def sql_str(value) -> str:
    if value is None:
        return "null"
    text = str(value).strip()
    if text == "":
        return "null"
    text = text.replace("'", "''")
    return f"'{text}'"


def sql_str_truncated(value, max_len: int, label: str, sku: str) -> str:
    if value is None:
        return "null"
    text = str(value).strip()
    if text == "":
        return "null"
    if len(text) > max_len:
        print(f"  [WARN] {sku}: {label} truncado de {len(text)} a {max_len} caracteres", file=sys.stderr)
        text = text[:max_len]
    text = text.replace("'", "''")
    return f"'{text}'"


def main() -> None:
    if not XLSX_PATH.exists():
        raise SystemExit(f"No se encuentra el Excel fuente: {XLSX_PATH}")

    wb = openpyxl.load_workbook(str(XLSX_PATH), read_only=True, data_only=True)
    ws = wb["13_Catalogo_SKU"]
    rows = list(ws.iter_rows(values_only=True))

    header = rows[9]
    expected_header = (
        "product_id", "variant_id", "SKU_FINAL", "SKU_BASE_FINAL", "FAMILIA_FINAL",
        "COD_FAMILIA", "SUBFAMILIA_FINAL", "COD_SUBFAMILIA", "PRODUCTO_BASE",
        "COD_PRODUCTO_FINAL", "DESCRIPCION",
    )
    if header[:11] != expected_header:
        raise SystemExit(
            f"La cabecera de 13_Catalogo_SKU cambio de estructura; "
            f"revisar el mapeo antes de regenerar el seed.\nEsperado: {expected_header}\nEncontrado: {header[:11]}"
        )

    data = [r for r in rows[10:] if r and r[2]]  # filas con SKU_FINAL no vacio

    if len(data) != EXPECTED_ROWS:
        raise SystemExit(
            f"Se esperaban {EXPECTED_ROWS} SKU y se leyeron {len(data)}. "
            f"El Excel maestro pudo haber cambiado -- revisar antes de continuar."
        )

    tipos = {}       # code -> name
    familias = {}     # (tipo_code, familia_code) -> familia_name
    skus_vistos = set()

    lines = []
    lines.append("-- 0001_catalogo_producto.sql")
    lines.append("-- GENERADO por scripts/seed_catalogo_sku.py -- NO editar a mano.")
    lines.append(f"-- Fuente: docs_base/{XLSX_PATH.name}, hoja 13_Catalogo_SKU ({EXPECTED_ROWS} SKU).")
    lines.append("-- Ver el docstring del script para el mapeo de columnas y sus supuestos.")
    lines.append("")

    # 1) TIPO_PRODUCTO / FAMILIA_PRODUCTO / SUBFAMILIA_PRODUCTO dinamicos
    #    (EP ya viene sembrado en 0007_maestros_clasificacion.sql con las 4
    #    familias EPP oficiales; on conflict do nothing evita duplicarlo).
    for r in data:
        familia_final, cod_familia = r[4], r[5]
        subfamilia_final, cod_subfamilia = r[6], r[7]
        tipos[cod_familia] = familia_final
        familias[(cod_familia, cod_subfamilia)] = subfamilia_final

    lines.append("-- Tipos de producto (nivel 1 del SKU).")
    for code, name in sorted(tipos.items()):
        lines.append(
            f"insert into logistica.tipo_producto (code, name) values "
            f"({sql_str(code)}, {sql_str(name)}) on conflict (code) do nothing;"
        )
    lines.append("")

    lines.append("-- Familias de producto (nivel 2 del SKU, anidadas por tipo).")
    for (tipo_code, fam_code), fam_name in sorted(familias.items()):
        lines.append(
            "insert into logistica.familia_producto (product_type_id, code, name) "
            f"select product_type_id, {sql_str(fam_code)}, {sql_str(fam_name)} "
            f"from logistica.tipo_producto where code = {sql_str(tipo_code)} "
            "on conflict (product_type_id, code) do nothing;"
        )
    lines.append("")

    lines.append(
        "-- Subfamilias de producto (nivel 3 formal del diccionario): el origen NO trae "
        "esta granularidad -- se crea una subfamilia '00 GENERAL' de paso por cada familia. "
        "Ver docstring del script."
    )
    for (tipo_code, fam_code) in sorted(familias.keys()):
        lines.append(
            "insert into logistica.subfamilia_producto (family_id, code, name) "
            "select fp.family_id, '00', 'GENERAL' "
            "from logistica.familia_producto fp join logistica.tipo_producto tp on tp.product_type_id = fp.product_type_id "
            f"where tp.code = {sql_str(tipo_code)} and fp.code = {sql_str(fam_code)} "
            "on conflict (family_id, code) do nothing;"
        )
    lines.append("")

    # 2) PRODUCTO + PRODUCTO_VARIANTE, uno por fila del catalogo.
    lines.append("-- Catalogo maestro: 725 SKU consolidados V38 Final.")
    for r in data:
        (
            _product_id, variant_id, sku_final, sku_base_final, familia_final, cod_familia,
            subfamilia_final, cod_subfamilia, producto_base, cod_producto_final, descripcion,
            atributo, valor_atributo, cod_atributo, marca, modelo, id_origen, sku_anterior,
            sku_propuesto_origen, nro_registros_origen, recodificado_epp, motivo_recodificacion,
            estado,
        ) = r[:23]

        if sku_final in skus_vistos:
            print(f"  [WARN] SKU duplicado en el origen, se omite la repeticion: {sku_final}", file=sys.stderr)
            continue
        skus_vistos.add(sku_final)

        name = descripcion or producto_base or sku_final
        active = "true" if (estado or "").strip().upper() == "ACTIVO" else "false"

        lines.append(
            "insert into logistica.producto "
            "(sku_base, product_type_id, family_id, subfamily_id, name, base_uom_id, active) "
            "select "
            f"{sql_str(sku_base_final or sku_final)}, tp.product_type_id, fp.family_id, sf.subfamily_id, "
            f"{sql_str_truncated(name, 220, 'DESCRIPCION', sku_final)}, uom.uom_id, {active} "
            "from logistica.tipo_producto tp "
            f"join logistica.familia_producto fp on fp.product_type_id = tp.product_type_id and fp.code = {sql_str(cod_subfamilia)} "
            "join logistica.subfamilia_producto sf on sf.family_id = fp.family_id and sf.code = '00' "
            "cross join logistica.unidad_medida uom "
            f"where tp.code = {sql_str(cod_familia)} and uom.code = 'ND' "
            "on conflict (sku_base) do nothing;"
        )

        variant_code = cod_atributo or "00"
        internal_variant_code = f"{sku_base_final or sku_final}-{variant_code}"
        lines.append(
            "insert into logistica.producto_variante "
            "(product_id, attribute_type, attribute_value, attribute_code, internal_variant_code) "
            "select p.product_id, "
            f"{sql_str(atributo)}, {sql_str_truncated(valor_atributo, 80, 'VALOR_ATRIBUTO', sku_final)}, "
            f"{sql_str(variant_code)}, {sql_str(internal_variant_code)} "
            f"from logistica.producto p where p.sku_base = {sql_str(sku_base_final or sku_final)} "
            "on conflict (internal_variant_code) do nothing;"
        )

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"OK: {len(skus_vistos)} SKU escritos en {OUT_PATH}")


if __name__ == "__main__":
    main()
