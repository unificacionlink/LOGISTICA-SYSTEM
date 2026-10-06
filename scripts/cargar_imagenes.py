"""
cargar_imagenes.py

Carga masiva de fotos de producto al storage compatible con S3 que usa el
backend (hoy Supabase Storage del proyecto de RRHH; opcional Cloudflare R2,
ver DEC-L015/DEC-L016) para los ~725 SKU del catalogo V38, y genera el SQL que las vincula en la BD (DOCUMENTO + PRODUCTO_IMAGEN),
con el mismo modelo que la subida individual del backend
(RequerimientoService.subirImagenProducto): una imagen principal activa por
producto, la anterior queda inactiva.

Igual que seed_catalogo_sku.py, NO ejecuta nada contra la base de datos: sube
los binarios al storage y escribe un .sql para revisar y aplicar (psql o el SQL
editor de Supabase).

CONVENCION DE NOMBRES: cada archivo de la carpeta se llama como el SKU base
o el codigo de variante, p. ej. AC01001.jpg, ep02015.PNG, EP02001-XL.webp (la
foto queda a nivel de producto base: si hay varias variantes del mismo base se
usa la primera en orden alfabetico). Formatos: jpg, jpeg, png, webp, gif. Los
archivos cuyo nombre no coincide con el catalogo se reportan y se omiten.

IDEMPOTENTE: guarda un manifiesto (<salida>.manifest.json) con el hash de cada
archivo ya subido. Re-ejecutar solo sube archivos nuevos o modificados, y el
SQL generado se puede aplicar mas de una vez sin duplicar filas.

Variables de entorno (las mismas que usa el backend):
    STORAGE_ENDPOINT, STORAGE_REGION (default: auto), STORAGE_ACCESS_KEY_ID,
    STORAGE_SECRET_ACCESS_KEY, STORAGE_BUCKET (default: logistica-productos),
    STORAGE_PUBLIC_URL

Uso:
    pip install boto3 pillow
    python scripts/cargar_imagenes.py C:/fotos_sku --usuario-email logistica@tdem.com.pe
    python scripts/cargar_imagenes.py C:/fotos_sku --usuario-email ... --optimizar
    python scripts/cargar_imagenes.py C:/fotos_sku --usuario-email ... --dry-run
"""
import argparse
import hashlib
import io
import json
import os
import re
import sys
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED_CATALOGO = ROOT / "db_design_reference" / "seed" / "0001_catalogo_producto.sql"
DEFAULT_SALIDA = ROOT / "db_design_reference" / "seed" / "0002_imagenes_producto.sql"

MIME_POR_EXTENSION = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".gif": "image/gif",
}
# Mismo criterio que el backend: el path nunca sale del nombre del cliente,
# y cada version usa un UUID nuevo, asi que el objeto es inmutable.
CACHE_CONTROL = "public, max-age=31536000, immutable"
LADO_MAXIMO_OPTIMIZADO = 1600


def cargar_codigos_catalogo() -> dict[str, str]:
    """codigo aceptado en el nombre de archivo -> sku_base. Acepta el SKU base
    (589 productos) y el codigo de variante (725 SKU finales, p. ej.
    EP02001-XL); la foto siempre queda a nivel de producto base."""
    texto = SEED_CATALOGO.read_text(encoding="utf-8")
    codigos = {b: b for b in re.findall(r"insert into \w+\.producto \([^)]*\) select '([^']+)'", texto)}
    for variante, base in re.findall(
            r"'([^']+)' from \w+\.producto p where p\.sku_base = '([^']+)'", texto):
        codigos.setdefault(variante, base)
    return codigos


def sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def optimizar(contenido: bytes) -> tuple[bytes, str, str]:
    """Reduce a max 1600px por lado y convierte a WebP (fotos de celular de
    varios MB quedan en ~100-300 KB, el catalogo carga mucho mas rapido)."""
    from PIL import Image, ImageOps

    with Image.open(io.BytesIO(contenido)) as img:
        img = ImageOps.exif_transpose(img)
        img.thumbnail((LADO_MAXIMO_OPTIMIZADO, LADO_MAXIMO_OPTIMIZADO))
        if img.mode not in ("RGB", "RGBA"):
            img = img.convert("RGBA" if "A" in img.getbands() else "RGB")
        salida = io.BytesIO()
        img.save(salida, format="WEBP", quality=82, method=6)
    return salida.getvalue(), ".webp", "image/webp"


def crear_cliente_s3():
    import boto3
    from botocore.config import Config

    faltantes = [v for v in ("STORAGE_ENDPOINT", "STORAGE_ACCESS_KEY_ID", "STORAGE_SECRET_ACCESS_KEY",
                             "STORAGE_PUBLIC_URL")
                 if not os.environ.get(v)]
    if faltantes:
        sys.exit(f"ERROR: faltan variables de entorno: {', '.join(faltantes)}")
    return boto3.client(
        "s3",
        endpoint_url=os.environ["STORAGE_ENDPOINT"],
        aws_access_key_id=os.environ["STORAGE_ACCESS_KEY_ID"],
        aws_secret_access_key=os.environ["STORAGE_SECRET_ACCESS_KEY"],
        region_name=os.environ.get("STORAGE_REGION", "auto"),
        config=Config(
            s3={"addressing_style": "path"},
            retries={"max_attempts": 5, "mode": "standard"},
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
        ),
    )


def generar_sql(manifiesto: dict, usuario_email: str, esquema: str) -> str:
    email = sql_str(usuario_email)
    sch = esquema
    lineas = [
        "-- Generado por scripts/cargar_imagenes.py -- NO editar a mano.",
        "-- Vincula las fotos subidas al storage con PRODUCTO_IMAGEN (una principal activa",
        "-- por producto). Idempotente: se puede aplicar mas de una vez.",
        "begin;",
        "do $$ begin",
        f"  if not exists (select 1 from {sch}.usuario where corporate_email = {email}) then",
        f"    raise exception 'No existe el usuario %', {email};",
        "  end if;",
        "end $$;",
    ]
    for sku in sorted(manifiesto):
        e = manifiesto[sku]
        doc_id = sql_str(e["document_id"])
        s = sql_str(sku)
        nuevo = f"not exists (select 1 from {sch}.documento where document_id = {doc_id})"
        lineas += [
            f"-- {sku}",
            f"update {sch}.producto_imagen pi set active = false from {sch}.producto p "
            f"where pi.product_id = p.product_id and p.sku_base = {s} and pi.variant_id is null "
            f"and pi.is_primary and pi.active and {nuevo};",
            f"insert into {sch}.documento (document_id, document_type, file_name, file_url, mime_type, "
            "file_hash, uploaded_by, uploaded_at, status) "
            f"select {doc_id}, 'IMAGEN_PRODUCTO', {sql_str(e['file_name'])}, {sql_str(e['url'])}, "
            f"{sql_str(e['mime_type'])}, {sql_str(e['sha256'])}, u.user_id, now(), 'ACTIVO' "
            f"from {sch}.usuario u where u.corporate_email = {email} "
            f"and exists (select 1 from {sch}.producto where sku_base = {s}) and {nuevo};",
            f"insert into {sch}.producto_imagen (product_image_id, product_id, variant_id, document_id, "
            "is_primary, display_order, active, created_at, created_by) "
            f"select gen_random_uuid(), p.product_id, null, {doc_id}, true, 0, true, now(), u.user_id "
            f"from {sch}.producto p cross join {sch}.usuario u "
            f"where p.sku_base = {s} and u.corporate_email = {email} "
            f"and exists (select 1 from {sch}.documento where document_id = {doc_id}) "
            f"and not exists (select 1 from {sch}.producto_imagen where document_id = {doc_id});",
        ]
    lineas.append("commit;")
    return "\n".join(lineas) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Carga masiva de fotos de producto al storage S3 (Supabase/R2).")
    parser.add_argument("carpeta", type=Path, help="Carpeta con archivos <SKU>.<ext>")
    parser.add_argument("--usuario-email", required=True,
                        help="corporate_email del usuario que figura como autor de la carga")
    parser.add_argument("--esquema", default="logistica",
                        help="Esquema de LOGISTICA en la BD (DEC-L016, default: logistica)")
    parser.add_argument("--salida", type=Path, default=DEFAULT_SALIDA, help="Ruta del .sql a generar")
    parser.add_argument("--optimizar", action="store_true",
                        help="Redimensiona a max 1600px y convierte a WebP antes de subir (requiere pillow)")
    parser.add_argument("--dry-run", action="store_true", help="Solo valida nombres, no sube nada")
    parser.add_argument("--hilos", type=int, default=8)
    args = parser.parse_args()

    if not args.carpeta.is_dir():
        sys.exit(f"ERROR: no existe la carpeta {args.carpeta}")

    codigos = cargar_codigos_catalogo()
    total_productos = len(set(codigos.values()))
    archivos: dict[str, Path] = {}
    for f in sorted(args.carpeta.iterdir()):
        if not f.is_file():
            continue
        if f.suffix.lower() not in MIME_POR_EXTENSION:
            print(f"  [OMITIDO] formato no soportado: {f.name}", file=sys.stderr)
            continue
        sku = codigos.get(f.stem.strip().upper())
        if sku is None:
            print(f"  [OMITIDO] no es un SKU del catalogo: {f.name}", file=sys.stderr)
            continue
        if sku in archivos:
            print(f"  [OMITIDO] ya hay foto para {sku} ({archivos[sku].name}): {f.name}", file=sys.stderr)
            continue
        archivos[sku] = f

    manifiesto_path = args.salida.with_suffix(".manifest.json")
    manifiesto = json.loads(manifiesto_path.read_text(encoding="utf-8")) if manifiesto_path.exists() else {}

    pendientes = []
    for sku, f in archivos.items():
        sha = hashlib.sha256(f.read_bytes()).hexdigest()
        if manifiesto.get(sku, {}).get("sha256") != sha:
            pendientes.append((sku, f, sha))

    print(f"{len(archivos)} archivos validos, {len(pendientes)} nuevos/modificados, "
          f"{total_productos - len(archivos)} de {total_productos} productos sin foto en la carpeta.")
    if args.dry_run:
        return

    if pendientes:
        s3 = crear_cliente_s3()
        bucket = os.environ.get("STORAGE_BUCKET", "logistica-productos")
        public_url = os.environ["STORAGE_PUBLIC_URL"].rstrip("/")

        def subir(sku: str, f: Path, sha: str) -> tuple[str, dict]:
            contenido = f.read_bytes()
            extension, mime = f.suffix.lower(), MIME_POR_EXTENSION[f.suffix.lower()]
            if args.optimizar and mime != "image/gif":
                contenido, extension, mime = optimizar(contenido)
            key = f"sku/{sku}/{uuid.uuid4()}{extension}"
            s3.put_object(Bucket=bucket, Key=key, Body=contenido, ContentType=mime, CacheControl=CACHE_CONTROL)
            return sku, {
                "document_id": str(uuid.uuid4()),
                "sha256": sha,
                "file_name": f.name,
                "mime_type": mime,
                "url": f"{public_url}/{key}",
            }

        errores = 0
        with ThreadPoolExecutor(max_workers=args.hilos) as pool:
            futuros = [pool.submit(subir, *p) for p in pendientes]
            for i, fut in enumerate(as_completed(futuros), 1):
                try:
                    sku, entrada = fut.result()
                    manifiesto[sku] = entrada
                except Exception as e:  # noqa: BLE001 -- se reporta y se sigue con el resto
                    errores += 1
                    print(f"  [ERROR] {e}", file=sys.stderr)
                if i % 50 == 0 or i == len(futuros):
                    print(f"  subidos {i}/{len(futuros)}")
                    # Se guarda en cada tramo para no perder el avance si se corta.
                    manifiesto_path.parent.mkdir(parents=True, exist_ok=True)
                    manifiesto_path.write_text(json.dumps(manifiesto, indent=2, sort_keys=True), encoding="utf-8")
        if errores:
            print(f"ATENCION: {errores} archivos fallaron; re-ejecuta el script para reintentarlos.",
                  file=sys.stderr)

    args.salida.parent.mkdir(parents=True, exist_ok=True)
    args.salida.write_text(generar_sql(manifiesto, args.usuario_email, args.esquema), encoding="utf-8")
    print(f"OK: {len(manifiesto)} imagenes en {args.salida} (aplicar con psql o el SQL editor de Supabase)")


if __name__ == "__main__":
    main()
