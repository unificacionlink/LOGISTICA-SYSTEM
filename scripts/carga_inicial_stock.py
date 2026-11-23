"""
carga_inicial_stock.py

Carga inicial del almacen de Logistica desde el reporte del sistema anterior
(DEC-L085). Lee el CSV "stock_minimo_TDEM_GOYDEL_<desde>_<hasta>.csv" (separado
por ";", columnas: Cod. TDEM; Cod. GOYDEL; Familia; Descripcion; UM; Stock
actual (und); Precio unitario (S/); Cantidad a pedir (und); Costo de compra)
y lo lleva al catalogo del sistema con las reglas que dio el usuario:

  - Un solo almacen: TDEM y GOYDEL se consolidan en un producto.
  - Stock actual = stock contado; Cantidad a pedir = stock minimo;
    Precio unitario = precio de venta del catalogo para TDEM, GOYDEL y METRIN
    (el mayor si las tallas o filas de un producto traen precios distintos);
    Costo de compra se ignora.
  - Cruce 1, correlativo: el codigo TDEM (o el GOYDEL si coincide) contra el
    legacySku de la base de la V38.7, verificando que la descripcion coincida.
  - Cruce 2, nombre: todas las palabras del producto del catalogo estan en la
    descripcion y lo que sobra es solo la marca (al final, sin numeros, sin
    logo). La marca no cambia el producto; el logo, el color y la medida si.
  - Lo que no cruza es un producto nuevo: las tallas se agrupan como
    variantes (TALLA), la subfamilia sale del producto mas parecido del
    catalogo o de la familia del Excel, y el SKU sigue el correlativo de la
    subfamilia (familia + subfamilia + 3 digitos), como MaestroService.

No toca la base de datos. Genera en la carpeta de salida:
  PROPUESTA_PRODUCTOS_NUEVOS.xlsx   productos y tallas a crear (para revisar)
  CARGA_STOCK.xlsx                  plantilla de Stock > Carga (contado y minimo)
  PENDIENTES.xlsx                   filas que no se pudieron llevar al catalogo
  alta_productos_y_precios.sql      alta de productos/variantes y precios (idempotente)

Uso:
  python scripts/carga_inicial_stock.py --csv <reporte.csv> --familias <familias.json>
         --usuario <user_id> --salida <carpeta>
  familias.json: [{"tipo":"AC","fam":"01","ultimo":10}, ...] (ultimo correlativo
  de cada subfamilia en la BD al momento de generar).
"""

import argparse
import collections
import csv
import json
import re
import unicodedata
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill
from openpyxl.utils import get_column_letter

BASE_V387 = Path(__file__).resolve().parent.parent / "docs_base" / "V38_7_ENTREGA_2026-10-06" / "base_datos" / "BASE_INICIAL_DEMO.json"
EMPRESAS = ("TDEMSRL", "GOYDELSAC", "METRIN")

TALLA = re.compile(r'\bT\s*\.?\s*(XXXL|XXL|XL|2XL|3XL|4XL|L|M|S|XS|ESTANDAR|ESTAND|STD|XX|\d{1,2}(?:\.5)?)(?=\s|$)')
STOP = {'DE', 'DEL', 'LA', 'EL', 'CON', 'C', 'P', 'PARA', 'Y', 'EN', 'X', 'A', 'AL', 'T', 'LAS', 'LOS', 'UND', 'UNID'}
LOGO = {'LOGO', 'TDEM', 'GOYDEL', 'METRIN', 'NORTE', 'C.C.NORTE', 'CC', 'PUNO', 'C.PUNO', 'SIG', 'ENEL'}
UNIDADES = {'UND': 'UND', 'PAR': 'PAR'}  # el resto (KIT, PZA, PAQ, MTS, KG) no existe: queda ND
FAMILIA_EXCEL = {
    'UNIFORME': ('EP', '02'), 'EPP': ('EP', '02'), 'CALZADO': ('EP', '04'), 'FORMATERIA': ('MA', '05'),
    'MATERIALES': ('MA', '06'), 'ECONOMATO': ('MA', '07'), 'AGREGADOS': ('MA', '01'), 'UTILES DE ASEO': ('MA', '02'),
    'EQUIPO DE COMPUTO': ('EQ', '02'), 'HERRAMIENTA ELECTRICA': ('EQ', '01'), 'HERRAMIENTA': ('HE', '03'),
    'EQUIPO PARA ALTURAS': ('HE', '02'), 'ACCESORIO DE CAMPO': ('AC', '02'), 'ACCESORIO DE MANTTO': ('AC', '02'),
    'INMUEBLE': ('MU', '03'),
}
PALABRA_FAMILIA = {'STICKER': ('MA', '08'), 'FORMATO': ('MA', '05')}
# Una medida no es marca: 1200W, 3.6MM, 256GB, 30KG... ni las unidades sueltas.
UNIDAD = re.compile(r'\d(MM|CM|M|MTS|W|V|KV|KW|KG|GR|G|GB|TB|MB|MP|GHZ|HZ|BTU|AWG|ML|LT|L|LB|PZ|PZS|VA|AH|MAH|IN|"|°)$')
UNIDAD_PALABRA = {'KG', 'MM', 'CM', 'M', 'MTS', 'W', 'V', 'GB', 'TB', 'LB', 'ML', 'LT', 'GL', 'GALON', 'UND', 'PZ', 'PZS',
                  'X', 'HZ', 'GHZ', 'BTU', 'AWG', 'VA'}


def num(s):
    try:
        return float(s.replace('.', '').replace(',', '.')) if s else 0.0
    except ValueError:
        return 0.0


def ascii_(s):
    s = unicodedata.normalize('NFD', s.upper())
    return ''.join(c for c in s if unicodedata.category(c) != 'Mn')


def norm(s):
    s = re.sub(r'\[[^\]]*\]', ' ', ascii_(s))
    s = s.replace('(', ' ').replace(')', ' ').replace(',', ' ').replace('-', ' ')
    return re.sub(r'[^A-Z0-9./"° ]', ' ', s)


def talla(s):
    m = TALLA.search(norm(s))
    if not m:
        return None
    return {'2XL': 'XXL', 'ESTAND': 'ESTANDAR', 'STD': 'ESTANDAR'}.get(m.group(1), m.group(1))


def lista(s):
    out = []
    for t in TALLA.sub(' ', norm(s)).split():
        t = t.strip('.').replace('C/', '').replace('P/', '')
        if t and t not in STOP and (re.search(r'\d', t) or len(t) > 1):
            out.append(t)
    return out


def es_medida(t):
    return bool(re.search(r'\d', t)) or t in {'AA', 'AAA'}


def codigo_talla(valor):
    """Como NomenclaturaVariante.codigo("TALLA", valor): solo letras y numeros."""
    return re.sub(r'[^A-Z0-9]', '', ascii_(valor))


def nombre_producto(desc):
    """Descripcion sin la talla ni etiquetas [..], como normalizarNombre de MaestroService."""
    s = re.sub(r'\[[^\]]*\]', ' ', desc.upper())
    s = TALLA.sub(' ', s)
    s = re.sub(r'\s+', ' ', s).strip(' -')
    return s


def sql_txt(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--csv', required=True)
    ap.add_argument('--familias', required=True)
    ap.add_argument('--usuario', required=True, help='user_id de Logistica que aprueba el alta y registra los precios')
    ap.add_argument('--salida', required=True)
    a = ap.parse_args()
    salida = Path(a.salida)
    salida.mkdir(parents=True, exist_ok=True)

    datos = json.load(open(BASE_V387, encoding='utf8'))['data']
    cat = [p for p in datos['products'] if p.get('masterCatalog')]
    legacy = {}
    prods = {}
    for p in cat:
        cod = p['skuBase'] + '-' + (p.get('attributeCode') or '00')
        if p.get('legacySku'):
            legacy[p['legacySku'].strip().upper()] = (cod, p['name'], p['skuBase'])
        b = prods.setdefault(p['skuBase'], {'nombre': p.get('productBase') or p['name'], 'vars': {}, 'atributo': None,
                                            'tipo': p['skuBase'][:2], 'fam': p['skuBase'][2:4]})
        b['vars'][(p.get('attributeCode') or '00').upper()] = cod
        if p.get('attributeValue'):
            b['vars'][str(p['attributeValue']).upper()] = cod
        if p.get('attribute'):
            b['atributo'] = p['attribute']
    for b in prods.values():
        b['toks'] = set(lista(b['nombre']))
        b['talla_nombre'] = talla(b['nombre'])
    vocab = set().union(*(b['toks'] for b in prods.values()))

    filas = []
    for x in list(csv.reader(open(a.csv, encoding='utf-8-sig'), delimiter=';'))[2:]:
        if len(x) < 8 or not (x[0] or x[1]):
            continue
        filas.append(dict(tdem=x[0].strip().upper(), goydel=x[1].strip().upper(), familia=x[2].strip(),
                          desc=x[3].strip(), um=x[4].strip().upper(), stock=num(x[5]), precio=num(x[6]),
                          minimo=num(x[7]), codigo=None, como=None))

    # ---- 1) correlativo
    def parecido(a_, b_):
        A, B = set(lista(a_)), set(lista(b_))
        return len(A & B) / max(1, min(len(A), len(B)))
    for f in filas:
        for c in (f['tdem'], f['goydel']):
            if c and c in legacy and parecido(f['desc'], legacy[c][1]) >= 0.6:
                f['codigo'], f['como'] = legacy[c][0], 'correlativo'
                break

    # ---- 2) nombre (la marca no cambia el producto)
    agregar_talla = []
    for f in [f for f in filas if not f['codigo']]:
        seq = lista(f['desc'])
        dt = set(seq)
        t = talla(f['desc'])
        mejor = None
        for sb, b in prods.items():
            if not b['toks'] or not b['toks'] <= dt:
                continue
            extra = dt - b['toks']
            if any(x in vocab or es_medida(x) or x in LOGO for x in extra):
                continue
            ult = max(seq.index(x) for x in b['toks'])
            if any(seq.index(x) < ult for x in extra):
                continue
            if mejor is None or len(b['toks']) > len(prods[mejor]['toks']):
                mejor = sb
        if not mejor:
            continue
        b = prods[mejor]
        con_tallas = b['atributo'] == 'TALLA'
        if not con_tallas:
            f['codigo'], f['como'] = b['vars'].get('00') or next(iter(b['vars'].values())), 'nombre'
        elif t and t in b['vars']:
            f['codigo'], f['como'] = b['vars'][t], 'nombre'
        elif t:
            cod = mejor + '-' + codigo_talla(t)
            b['vars'][t] = cod
            agregar_talla.append((mejor, t, cod))
            f['codigo'], f['como'] = cod, 'talla nueva'

    # ---- 3) lo que queda: nombre sin la marca ni el modelo del final (la marca no cambia el producto)
    nuevas = [f for f in filas if not f['codigo']]
    finales = set()
    for f in nuevas:
        finales.update(lista(nombre_producto(f['desc']))[-2:])

    def es_marca(t):
        if t in LOGO or t in vocab or t in UNIDAD_PALABRA:
            return False
        if re.search(r'[A-Z]', t) and re.search(r'\d', t):
            return not UNIDAD.search(t)          # modelo: N10, UT501A, L5590 (no 1200W ni 3.6MM)
        return not es_medida(t) and t in finales  # palabra que cierra descripciones y no existe en el catalogo

    def nucleo(desc):
        palabras_ = nombre_producto(desc).split()
        quitadas = 0
        while quitadas < 3 and len(lista(' '.join(palabras_[:-1]))) >= 2:
            ultima = lista(palabras_[-1])
            if not ultima and re.search(r'[A-Z0-9]', ascii_(palabras_[-1])):
                break  # una letra suelta (20 V, 1200 W) es parte de la medida
            if ultima and not all(es_marca(x) for x in ultima):
                break
            palabras_.pop()
            quitadas += 1 if ultima else 0
        return ' '.join(palabras_).strip(' -')

    grupos = collections.OrderedDict()
    for f in nuevas:
        nombre = nucleo(f['desc'])
        t = talla(f['desc'])
        # Sin la marca puede ser un producto que ya existe.
        existente = next((sb for sb, b in prods.items() if b['toks'] and b['toks'] == set(lista(nombre))), None)
        if existente:
            b = prods[existente]
            if b['atributo'] != 'TALLA':
                f['codigo'], f['como'] = b['vars'].get('00') or next(iter(b['vars'].values())), 'nombre'
                continue
            if t:
                if t not in b['vars']:
                    b['vars'][t] = existente + '-' + codigo_talla(t)
                    agregar_talla.append((existente, t, b['vars'][t]))
                f['codigo'], f['como'] = b['vars'][t], 'nombre'
                continue
        clave = ' '.join(lista(nombre)) + ('' if t else '#SIN')
        f['clave'], f['nombre_nuevo'] = clave, nombre
        grupos.setdefault(clave, []).append(f)
    ultimos = {(x['tipo'], x['fam']): x['ultimo'] for x in json.load(open(a.familias, encoding='utf8'))}
    nuevos = []
    for clave, fs in grupos.items():
        nombre = min((f['nombre_nuevo'] for f in fs), key=len)
        toks = set(lista(nombre))
        primera = lista(nombre)[0] if lista(nombre) else ''
        fam = PALABRA_FAMILIA.get(primera)
        if not fam:
            cand = [(len(toks & b['toks']) / max(len(toks), len(b['toks'])), sb) for sb, b in prods.items()
                    if b['toks'] and lista(b['nombre'])[0] == primera]
            if cand and max(cand)[0] >= 0.4:
                b = prods[max(cand)[1]]
                fam = (b['tipo'], b['fam'])
        if not fam:
            fam = FAMILIA_EXCEL.get(collections.Counter(f['familia'] for f in fs).most_common(1)[0][0], ('MA', '06'))
        unidades = collections.Counter(UNIDADES.get(f['um'], 'ND') for f in fs)
        nuevos.append(dict(clave=clave, nombre=nombre, tipo=fam[0], fam=fam[1], uom=unidades.most_common(1)[0][0], filas=fs,
                           tallas=sorted({talla(f['desc']) for f in fs if talla(f['desc'])})))
    nuevos.sort(key=lambda n: (n['tipo'], n['fam'], n['nombre']))
    vistos = set()
    for n in nuevos:
        k = (n['tipo'], n['fam'])
        ultimos[k] = ultimos.get(k, 0) + 1
        n['sku'] = f"{n['tipo']}{n['fam']}{ultimos[k]:03d}"
        if (k, n['nombre']) in vistos:
            n['nombre'] = f"{n['nombre']} ({n['sku']})"
        vistos.add((k, n['nombre']))
        for f in n['filas']:
            t = talla(f['desc'])
            f['codigo'] = n['sku'] + '-' + (codigo_talla(t) if n['tallas'] else '00')
            f['como'] = 'producto nuevo'

    # ---- consolidado por variante y precio por producto (el mayor)
    variantes = collections.OrderedDict()
    for f in sorted(filas, key=lambda f: f['codigo']):
        v = variantes.setdefault(f['codigo'], {'stock': 0, 'minimo': 0, 'origen': []})
        v['stock'] += f['stock']
        v['minimo'] += f['minimo']
        v['origen'].append(f"{f['tdem'] or '-'}/{f['goydel'] or '-'} {f['desc']} [{f['como']}]")
    precio = collections.defaultdict(float)
    for f in filas:
        if f['precio'] > 0:
            b = f['codigo'].split('-')[0]
            precio[b] = max(precio[b], round(f['precio'], 2))
    nombre_de = {sb: b['nombre'] for sb, b in prods.items()}
    nombre_de.update({n['sku']: n['nombre'] for n in nuevos})

    # ---- Excel
    neg, gris, amarillo = Font(bold=True), PatternFill('solid', fgColor='D9D9D9'), PatternFill('solid', fgColor='FFF2CC')

    def hoja(ws, cab, filas_, editables=(), anchos=()):
        ws.append(cab)
        for i, c in enumerate(cab, 1):
            cel = ws.cell(row=1, column=i)
            cel.font, cel.fill = neg, (amarillo if c in editables else gris)
        for r in filas_:
            ws.append(r)
        ws.freeze_panes, ws.auto_filter.ref = 'B2', ws.dimensions
        for i, w in enumerate(anchos, 1):
            ws.column_dimensions[get_column_letter(i)].width = w

    wb = Workbook()
    ws = wb.active
    ws.title = 'PRODUCTOS NUEVOS'
    hoja(ws, ['SKU', 'Familia', 'Subfamilia', 'Nombre', 'Unidad', 'Tallas', 'Precio venta (S/)', 'Stock', 'Stock mínimo',
              'Filas de origen'],
         [[n['sku'], n['tipo'], n['tipo'] + n['fam'], n['nombre'], n['uom'], ', '.join(n['tallas']),
           precio.get(n['sku']) or None, int(sum(f['stock'] for f in n['filas'])), int(sum(f['minimo'] for f in n['filas'])),
           ' | '.join(f"{f['tdem'] or '-'}/{f['goydel'] or '-'} {f['desc']}" for f in n['filas'])] for n in nuevos],
         anchos=(11, 8, 10, 50, 7, 22, 15, 9, 12, 90))
    ws2 = wb.create_sheet('TALLAS AGREGADAS')
    hoja(ws2, ['SKU', 'Producto', 'Talla nueva', 'Variante'],
         [[sb, nombre_de[sb], t, cod] for sb, t, cod in agregar_talla], anchos=(11, 50, 12, 16))
    wb.save(salida / 'PROPUESTA_PRODUCTOS_NUEVOS.xlsx')

    wb = Workbook()
    ws = wb.active
    ws.title = 'INVENTARIO'
    hoja(ws, ['Código', 'SKU base', 'Producto', 'Atributo', 'Stock sistema', 'Reservado', 'Stock contado', 'Stock mínimo',
              'Origen (sistema anterior)'],
         [[cod, cod.split('-')[0], nombre_de.get(cod.split('-')[0]), '' if cod.endswith('-00') else cod.split('-', 1)[1],
           0, 0, int(v['stock']), int(v['minimo']) if v['minimo'] > 0 else None, ' | '.join(v['origen'])]
          for cod, v in variantes.items()],
         ('Stock contado', 'Stock mínimo'), (16, 11, 46, 11, 13, 11, 14, 13, 90))
    wb.save(salida / 'CARGA_STOCK.xlsx')

    # ---- SQL (idempotente: un SKU o variante que ya existe no se repite; precio igual no crea fila)
    sql = ["-- Generado por scripts/carga_inicial_stock.py (DEC-L085). Una transaccion.", "begin;",
           f"create temp table _u on commit drop as select '{a.usuario}'::uuid as id;"]
    for n in nuevos:
        sql.append(f"""insert into logistica.producto (sku_base, product_type_id, family_id, subfamily_id, name, base_uom_id, active, approved_by, approved_at)
select {sql_txt(n['sku'])}, t.product_type_id, f.family_id, s.subfamily_id, {sql_txt(n['nombre'])}, u.uom_id, true, (select id from _u), now()
  from logistica.tipo_producto t join logistica.familia_producto f on f.product_type_id = t.product_type_id and f.code = {sql_txt(n['fam'])}
  join logistica.subfamilia_producto s on s.family_id = f.family_id and s.code = '00'
  join logistica.unidad_medida u on u.code = {sql_txt(n['uom'])}
 where t.code = {sql_txt(n['tipo'])} and not exists (select 1 from logistica.producto p where p.sku_base = {sql_txt(n['sku'])});""")
        if n['tallas']:
            for t in n['tallas']:
                c = codigo_talla(t)
                sql.append(f"""insert into logistica.producto_variante (product_id, attribute_type, attribute_value, attribute_code, internal_variant_code)
select product_id, 'TALLA', {sql_txt(t)}, {sql_txt(c)}, {sql_txt(n['sku'] + '-' + c)} from logistica.producto where sku_base = {sql_txt(n['sku'])}
   and not exists (select 1 from logistica.producto_variante where internal_variant_code = {sql_txt(n['sku'] + '-' + c)});""")
        else:
            sql.append(f"""insert into logistica.producto_variante (product_id, attribute_type, attribute_value, attribute_code, internal_variant_code)
select product_id, null, null, '00', {sql_txt(n['sku'] + '-00')} from logistica.producto where sku_base = {sql_txt(n['sku'])}
   and not exists (select 1 from logistica.producto_variante where internal_variant_code = {sql_txt(n['sku'] + '-00')});""")
        sql.append(f"""insert into logistica.log_cambios (entidad, entidad_id, accion, despues, usuario_id)
select 'PRODUCTO', product_id, 'ALTA_CARGA_INICIAL', jsonb_build_object('sku', {sql_txt(n['sku'])}, 'origen', 'stock_minimo_TDEM_GOYDEL 2026-11-20'), (select id from _u)
  from logistica.producto where sku_base = {sql_txt(n['sku'])}
   and not exists (select 1 from logistica.log_cambios l where l.accion = 'ALTA_CARGA_INICIAL' and l.despues->>'sku' = {sql_txt(n['sku'])});""")
    for sb, t, cod in agregar_talla:
        c = cod.split('-', 1)[1]
        sql.append(f"""insert into logistica.producto_variante (product_id, attribute_type, attribute_value, attribute_code, internal_variant_code)
select product_id, 'TALLA', {sql_txt(t)}, {sql_txt(c)}, {sql_txt(cod)} from logistica.producto where sku_base = {sql_txt(sb)}
   and not exists (select 1 from logistica.producto_variante where internal_variant_code = {sql_txt(cod)});""")
    valores = ",".join(f"({sql_txt(sb)}, {p:.2f})" for sb, p in sorted(precio.items()))
    empresas = ",".join(sql_txt(e) for e in EMPRESAS)
    sql.append(f"""create temp table _p on commit drop as
select e.company_id, p.product_id, v.precio,
       (select pv.sale_price from logistica.precio_venta_producto pv where pv.company_id = e.company_id and pv.product_id = p.product_id and pv.active) as anterior
  from (values {valores}) v(sku, precio)
  join logistica.producto p on p.sku_base = v.sku
  cross join logistica.empresa e where e.code in ({empresas});
delete from _p where anterior is not null and anterior >= precio;
update logistica.precio_venta_producto pv set active = false from _p where pv.company_id = _p.company_id and pv.product_id = _p.product_id and pv.active;
insert into logistica.precio_venta_producto (company_id, product_id, sale_price, created_by) select company_id, product_id, precio, (select id from _u) from _p;
insert into logistica.log_cambios (entidad, entidad_id, accion, antes, despues, usuario_id)
select 'PRODUCTO', product_id, 'PRECIO_VENTA', jsonb_build_object('empresa', company_id, 'precio', anterior),
       jsonb_build_object('empresa', company_id, 'precio', precio, 'origen', 'Carga inicial: stock_minimo_TDEM_GOYDEL 2026-11-20'), (select id from _u)
  from _p;""")
    sql.append("commit;")
    (salida / 'alta_productos_y_precios.sql').write_text("\n".join(sql), encoding='utf8')

    pend = [f for f in filas if not f['codigo']]
    wb = Workbook()
    hoja(wb.active, ['Cód. TDEM', 'Cód. GOYDEL', 'Descripción', 'Stock', 'Precio', 'Mínimo'],
         [[f['tdem'], f['goydel'], f['desc'], f['stock'], f['precio'], f['minimo']] for f in pend], anchos=(10, 10, 60, 8, 8, 8))
    wb.save(salida / 'PENDIENTES.xlsx')

    c = collections.Counter(f['como'] for f in filas)
    print(f"filas {len(filas)}: " + ", ".join(f"{k} {v}" for k, v in c.items()))
    print(f"productos nuevos {len(nuevos)} (con tallas {sum(1 for n in nuevos if n['tallas'])}), tallas agregadas {len(agregar_talla)}")
    print(f"variantes en la carga {len(variantes)}, con stock {sum(1 for v in variantes.values() if v['stock'] > 0)}, "
          f"con minimo {sum(1 for v in variantes.values() if v['minimo'] > 0)}; precios {len(precio)}; pendientes {len(pend)}")


if __name__ == '__main__':
    main()
