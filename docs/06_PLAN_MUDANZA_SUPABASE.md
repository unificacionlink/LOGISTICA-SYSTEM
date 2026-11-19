# Plan — Mudar Logística a su propio proyecto Supabase

## Estado (2026-11-19): corte hecho, falta el retiro en RR-HH (DEC-L072)

| Etapa | Estado |
|---|---|
| 0. Preparar LOGISTICA-TDEM | Hecho. Postgres 17.6. Se borraron los restos de prueba (46 tablas en `public` y 4 usuarios de Auth). Rol `logistica_app` creado con contraseña nueva, enviada solo como huella SCRAM. Esquema `logistica`, buckets y llaves S3 listos. El pooler es `aws-0-us-east-2` (RR-HH usa `aws-1`). |
| 1. Ensayo | Hecho. Misma huella de conteos (85 tablas, 2617 filas, Flyway V39). 19 archivos copiados con SHA-256 verificado. El bucket privado escribe y lee con las llaves, y no se abre desde afuera. |
| 2. Corte | Hecho el 2026-11-19, de 10:15 a 10:27 en hora de Lima, con el servicio suspendido. Se esperó a que se cerraran las 5 conexiones JDBC en RR-HH antes de copiar. Dump y restore, 0 URLs viejas y huella idéntica. 7 variables cambiadas en Render. `logistica_app` en `nologin` en RR-HH. |
| Prueba de humo | Hecha. `/api/health` responde 200, la app abre 5 conexiones en LOGISTICA-TDEM y 0 en RR-HH, y las fotos se sirven desde el bucket nuevo. El usuario subió un documento de proveedor: quedó en `logistica-privado`, y en RR-HH no se escribió nada después del corte. |
| 4. Retiro en RR-HH | **Pendiente, desde el 2026-12-03.** Sacar un respaldo final, borrar el esquema, el rol y el bucket, y revocar las llaves S3 que usaba Logística. |

Las credenciales y los dumps están en `C:\Users\User\.logistica-migracion\`,
una carpeta de la PC del usuario, solo legible por su cuenta y fuera del repo.
Ahí está el dump del corte, `logistica_corte.dump`, como respaldo del estado de
RR-HH en el momento de la mudanza. Para operar el proyecto nuevo, `.mcp.json`
tiene dos conexiones: `supabase-logistica`, con escritura, y `supabase`, la de
RR-HH, en solo lectura.

Decisión del usuario (2026-11-19): Logística deja el proyecto **RR-HH** y pasa al
proyecto **LOGISTICA-TDEM**, con base, archivos y llaves propias. Esto revierte
DEC-L016 y DEC-L015, y se registrará como DEC-L072 cuando termine el corte.

Hoy:
- Producción usa el proyecto RR-HH (`zcukpokttrteshtjzjja`, Postgres 17.6,
  us-east-2) a través del esquema `logistica` y el rol `logistica_app`.
- LOGISTICA-TDEM está encendido (MICRO) y no se usa.

## Inventario (consultado el 2026-11-19, solo lectura)

| Qué | Dato |
|---|---|
| Esquema `logistica` | 85 tablas. La más grande pesa 280 kB (`correo_saliente`). Flyway está en V39. |
| Extensiones | Ninguna tabla del esquema depende de extensiones: `gen_random_uuid()` es nativa en PG 13+ y no hay columnas `citext` ni defaults de `uuid-ossp`. |
| Funciones propias | 1, dentro del esquema; el dump la lleva. |
| Storage | Bucket público `logistica-productos` con 19 archivos (5,6 MB). El privado `logistica-privado` todavía no existe. |
| URLs guardadas en la BD | `documento.file_url`: 19 filas, todas con la URL pública de RR-HH. Las columnas `packing_evidence_url`, `conformity_url`, `evidence_url` y `return_evidence_url` están vacías. |
| Integración con RR.HH. | Solo por HTTP (`RRHH_API_URL`, DEC-L002): no se toca. |
| Procesos programados | Sync de personal, envío de correos, recordatorio de las 8:00, registro de la plataforma y `LimpiezaFotos` (03:30 Lima). |

**Cambios de código: ninguno.** Todo se mueve con las variables de Render y,
en la máquina local, con `application-local.yml`.

## Riesgo principal: `LimpiezaFotos`

Este proceso lista el bucket y borra los archivos que ninguna fila de
`documento` referencia por su URL pública. Si la app arranca con el Storage
nuevo pero `documento.file_url` todavía apunta a RR-HH, **todas las fotos se
ven huérfanas y se borran** en la corrida siguiente. Para evitarlo:
1. La actualización de URLs va **en el mismo corte**, antes de arrancar.
2. El corte no se hace cerca de las 03:30, hora de Lima.
3. Antes de arrancar, se verifica que no queden URLs con `zcukpokttrteshtjzjja`.

## Etapas

### 0. Preparar LOGISTICA-TDEM (sin afectar producción)
- Revisar qué dejó el proyecto viejo: tablas de prueba en `public`, usuarios
  de Auth y buckets con fotos de prueba. Recomendación: vaciarlo, o borrarlo
  y crear uno limpio del mismo nombre. Comprobar que su Postgres sea **17 o
  superior**, porque un dump de 17 no se restaura en una versión anterior.
- En su SQL Editor, como `postgres`, correr
  `db_design_reference/setup/rrhh_db_esquema_logistica.sql` con una
  contraseña nueva. Crea el rol `logistica_app` y el esquema `logistica`.
- No agregar `logistica` a los *Exposed schemas* de la Data API.
- Storage: crear `logistica-productos` (**público**) y `logistica-privado`
  (**privado**), y generar un par de llaves S3 propias del proyecto.
- En **Connect**, anotar el host del pooler en modo sesión, puerto 5432. Puede
  ser `aws-0-…` o `aws-1-…`.
- Herramientas locales: el cliente de PostgreSQL 17 (`pg_dump`, `pg_restore`,
  `psql`), que hoy no está instalado, y Python con `boto3` para copiar el
  Storage.

### 1. Ensayo (producción sigue funcionando)
- `pg_dump` del esquema `logistica` desde RR-HH, que solo lee, y restauración
  en LOGISTICA-TDEM:
  ```
  pg_dump "postgresql://logistica_app.<ref_rrhh>:<pass>@aws-1-us-east-2.pooler.supabase.com:5432/postgres" --schema=logistica --no-owner --no-privileges -Fc -f logistica_ensayo.dump
  pg_restore --no-owner --role=logistica_app -d "postgresql://logistica_app.<ref_nuevo>:<pass>@<pooler_nuevo>:5432/postgres" logistica_ensayo.dump
  ```
- Comparar el conteo de filas por tabla entre origen y destino, y que
  `flyway_schema_history` llegue a V39.
- Copiar los 19 archivos de `logistica-productos` al bucket nuevo con la
  misma clave, por la API S3.
- Al terminar, vaciar el esquema de ensayo (`drop schema logistica cascade`
  y volver a crearlo) para que el corte parta limpio.

### 2. Corte (ventana de unos 30 minutos, de día y avisando a los usuarios)
1. **Suspender** el servicio `erp-logistica-tdem` en Render. Así nadie escribe
   y no corren los procesos programados.
2. `pg_dump` final desde RR-HH y `pg_restore` en LOGISTICA-TDEM.
3. Copiar los archivos del Storage que hayan cambiado desde el ensayo.
4. Actualizar las URLs en la BD nueva:
   ```sql
   update logistica.documento
      set file_url = replace(file_url,
          'https://zcukpokttrteshtjzjja.supabase.co/storage/v1/object/public/logistica-productos',
          'https://<ref_nuevo>.supabase.co/storage/v1/object/public/logistica-productos')
    where file_url like 'https://zcukpokttrteshtjzjja.supabase.co/%';
   ```
   Repetir para las cuatro columnas `*_url` si para entonces tienen datos.
   Después, verificar que queden **0** filas con `zcukpokttrteshtjzjja`.
5. Cambiar en Render:
   - `DB_URL`, `DB_USERNAME` (`logistica_app.<ref_nuevo>`) y `DB_PASSWORD`.
   - `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` y
     `STORAGE_PUBLIC_URL`.
   - `JWT_SECRET` **no cambia**: las sesiones abiertas siguen valiendo.
6. En RR-HH, `alter role logistica_app nologin;`. Así nada vuelve a escribir
   en la copia vieja por error.
7. Reanudar el servicio. Comprobar `/api/health`, el inicio de sesión, el
   catálogo con fotos, un requerimiento, la subida y descarga de un documento
   de proveedor y la bandeja de correos.

### 3. Vuelta atrás (si algo falla en las primeras horas)
- Restaurar en Render las variables de RR-HH y, en RR-HH, `alter role
  logistica_app login;`. La copia de RR-HH quedó intacta.
- Lo que se haya escrito en la base nueva después del corte se pierde, o se
  copia a mano. Por eso conviene decidir rápido.

### 4. Retiro en RR-HH (a las 2 semanas, con respaldo propio)
- Guardar un `pg_dump` del esquema viejo como respaldo.
- En RR-HH: `drop schema logistica cascade; drop role logistica_app;`, y
  borrar el bucket `logistica-productos`.
- **Revocar las llaves S3 de RR-HH que usaba Logística.** Hoy dan acceso a
  todos los buckets de RR-HH (`cvs`, `cese-docs`, `tdr`…). Esta es la mayor
  ganancia de seguridad de la mudanza.

### 5. Documentación y herramientas
- Registrar DEC-L072 (revierte DEC-L015 y DEC-L016) y su entrada en el
  CHANGELOG.
- Actualizar `CLAUDE.md` (la sección de BD y Archivos) y las guías 02 y 03.
- Renombrar la sección "BD compartida con RRHH". El script de setup sigue
  sirviendo tal cual.
- Actualizar `application-local.yml` local y el `.mcp.json` de Supabase con el
  ref nuevo. Son archivos que no se versionan.
- `BdRrhhSimulada` y las pruebas no cambian: el esquema sigue llamándose
  `logistica`.

## Quién hace qué

| Paso | Usuario | Claude |
|---|---|---|
| Revisar y limpiar LOGISTICA-TDEM, crear buckets y llaves, ver el host del pooler | ✔ (panel de Supabase) | Le dice exactamente qué revisar |
| Correr el script del rol y el esquema (con su contraseña) | ✔ | — |
| Instalar el cliente de PostgreSQL 17 | ✔, o Claude con autorización | Puede hacerlo con `winget` |
| Ensayo: dump, restore, conteos y copia de Storage | Autoriza | ✔ (dump de solo lectura sobre producción) |
| Suspender y reanudar Render, cambiar variables | ✔ (panel de Render) | — |
| Corte: dump, restore, URLs, verificación | Autoriza la ventana | ✔ |
| Retiro en RR-HH y revocar llaves | ✔ | Prepara el SQL |
| Documentación (DEC-L072, guías, CLAUDE.md) | Autoriza el commit | ✔ |

Las contraseñas y las llaves **no se pegan en el chat**. Se escriben
directamente en Render y en un archivo local no versionado, del que los
comandos las leen.
