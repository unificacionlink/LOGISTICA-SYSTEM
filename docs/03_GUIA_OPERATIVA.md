# Guia operativa — ERP Logistico Corporativo

## Entorno local

Requiere JDK 21 y Node 20+.

**Backend:**
0. **Ya hecho en LOGISTICA-TDEM** (DEC-L072). En un proyecto nuevo: correr
   `db_design_reference/setup/rrhh_db_esquema_logistica.sql` (SQL Editor del
   proyecto, como `postgres`, cambiando antes la contrasena). Crea el rol
   `logistica_app` y el esquema `logistica`.
1. Crear `backend/src/main/resources/application-local.yml` (gitignored) con
   `spring.datasource.url/username/password` apuntando a LOGISTICA-TDEM con el
   rol `logistica_app` (usuario del pooler: `logistica_app.uwaenymwvixuocwbavnz`), mas
   `app.jwt.secret`, `app.cors.allowed-origins` y `app.storage.*` — ver
   variables en `docs/02_GUIA_TECNICA.md`. **Nunca** conectar LOGISTICA con
   el usuario `postgres`.
   - **Usar el connection pooler de Supabase (Supavisor), puerto 5432**
     (`aws-0-us-east-2.pooler.supabase.com:5432` para LOGISTICA-TDEM), **no** la conexion directa
     (`db.<ref>.supabase.co:5432`): en 2026-09-17 se confirmo que el host
     directo resuelve solo a IPv6 y falla por timeout en redes sin ruta
     IPv6. El pooler en modo sesion (puerto 5432, no el 6543 de modo
     transaccion) soporta bien Flyway/Hibernate.
   - Si el pooler impone un `statement_timeout` agresivo, las migraciones
     grandes lo levantan con `set local statement_timeout = 0;` al inicio
     del script.
2. `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
   (Flyway aplica las migraciones pendientes automaticamente al arrancar).
3. Para crear los usuarios de prueba y asignarles contrasena (ver seccion
   mas abajo), agregar el profile `seed-test-data` una vez:
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed-test-data`.
   En **PowerShell** el argumento `-D...` va entre comillas (si no, PowerShell
   lo corta en el primer punto y Maven falla con "Unknown lifecycle phase"):
   `./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed-test-data"`.

**Frontend:**
1. Copiar `frontend/.env.example` a `frontend/.env.local` (`VITE_API_URL=http://localhost:8080`).
2. `cd frontend && npm install && npm run dev` (sirve en `:5173`).

## Despliegue

Render (`render.yaml`), un unico Web Service via Docker (`Dockerfile` en la
raiz): el build de `frontend/` se empaqueta dentro del jar de `backend/`
(`resources/static`), asi que en produccion no hay CORS ni dos servicios que
mantener sincronizados. `healthCheckPath: /api/health`. Al crear el servicio
en Render, verificar en el dashboard que el Health Check Path quede igual
que en el blueprint (aprendizaje de RRHH-SYSTEM: puede desincronizarse).

Variables de entorno a configurar en Render (nunca en el repo):
`DB_URL`, `DB_USERNAME` (`logistica_app.<ref>`), `DB_PASSWORD`,
`DB_SCHEMA=logistica`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` (no aplica si es
un unico servicio, pero se deja definible), `COOKIE_SECURE=true` y las
`STORAGE_*`.

Documentos de proveedores (DEC-L071): bucket **privado** `logistica-privado`
(o el nombre de `STORAGE_PRIVATE_BUCKET`) en el Storage de LOGISTICA-TDEM, ya
creado. Usa las mismas llaves S3; sin el bucket, solo la subida y descarga de
documentos de proveedores falla.

Pendiente (2026-09-24): el despliegue en Render se decide al acercarse la
marcha blanca. Render cobra por servicio y no se puede compartir el de RRHH
(es otra app, en Node).

## Correos automaticos (DEC-L066)

1. En Resend (la misma cuenta de RR.HH.), **API Keys → Create**: una key
   "logistica" con permiso *Sending access* al dominio `tdemperu.com`.
2. En Render, agregar `RESEND_API_KEY` con esa key (y `APP_PUBLIC_URL =
   https://logistica.tdemperu.com` si no estaba). El dominio ya esta verificado
   por RR.HH.: no hay que tocar el DNS.
3. Revisar envios en la tabla `correo_saliente` (estado `ENVIADO` / `ERROR`);
   los que fallan 5 veces aparecen en **Administracion → Registro → Errores**
   como `CorreoNoEnviado`. Sin la key, los correos quedan `OMITIDO`.

## Alta de usuarios reales (DEC-L024)

1. Configurar `RRHH_API_URL` y `RRHH_SYNC_SERVICE_TOKEN` (ver
   `02_GUIA_TECNICA.md`) y, como ADMIN, **Administracion → RR.HH. →
   Sincronizar ahora**. Llega todo el personal activo (con o sin correo) y
   los CECOs de las empresas que cubre RRHH. No se crean cuentas (DEC-L041).
2. **Administracion → Personal**: elegir la empresa, buscar a la persona y
   pulsar **Dar acceso** (correo de ingreso + roles). La contrasena temporal
   se muestra una sola vez; entregarla al usuario, que la cambia en su
   primer ingreso. Mas CECOs o roles, en **Usuarios**.
3. Empresas sin padron en RRHH (METRIN, GOYDEL): en **Personal**, elegir la
   empresa y **Carga por Excel**. Descargar la plantilla (trae al personal
   ya cargado), completar una fila por persona (empresa, DNI, nombre,
   correo opcional, cargo, CECO, activo), **Validar** y **Aplicar cambios**.
   El mismo archivo sirve para altas, correcciones y ceses (Activo = NO). Un
   CECO que no exista se crea si se escribe su nombre. Casos sueltos:
   **Alta manual** o **Editar datos** en Usuarios. Luego dar acceso (paso 2). Cuando RRHH incorpore la
   empresa, quitarla de `identidad_manual_empresa_habilitada`: el sync
   convierte esas filas a RRHH_SYNC por DNI/correo sin duplicarlas.

## Usuarios de prueba

6 usuarios (uno por rol, empresa TDEMSRL). El profile `seed-test-data`
(`TestDataSeeder`) los crea en `usuario`/`usuario_rol` si no existen y les
asigna/renueva `password_hash`:

| Correo | Rol | Password |
|---|---|---|
| solicitante@logistica.test | SOLICITANTE | Logistica2026! |
| logistica@logistica.test | LOGISTICA | Logistica2026! |
| aprobador@logistica.test | APROBADOR | Logistica2026! |
| admin@logistica.test | ADMIN | Logistica2026! |
| gerencia@logistica.test | GERENCIA | Logistica2026! |
| pyc@logistica.test | PYC | Logistica2026! |

### Sync con RR.HH. en local

Con RRHH-SYSTEM corriendo en local (`npm run dev`, puerto 3000), agregar en
`application-local.yml` bajo `app:` un bloque `rrhh:` con `api-url:
http://localhost:3000` y `service-token:` igual a `RRHH_SYNC_SERVICE_TOKEN`
del `.env.local` de RRHH. Administracion -> RR.HH. -> "Sincronizar ahora"
trae personal y el catalogo completo de CECO (DEC-L031). Ojo: escribe en el
esquema `logistica` de la BD compartida.

## Migraciones y seed

Flyway (`backend/src/main/resources/db/migration/`), ver
`docs/02_GUIA_TECNICA.md` para el detalle de V1..V7. Migraciones nuevas se
agregan como `V8__...sql`, etc., siempre con el prefijo `${schema}.` (nunca
`public.`), tomando como referencia de
diseno los archivos en `db_design_reference/migrations/` (las 33
migraciones originales de Jonathan, Lotes 2+ aun no construidos) — **sin**
las sentencias de RLS (DEC-L008).

Seed del catalogo de 725 SKU: ya aplicado (dentro de `V1__baseline_lote1.sql`).
Si hace falta regenerarlo desde cero, `scripts/seed_catalogo_sku.py` (Python
+ openpyxl) sigue leyendo
`docs_base/ERP_BASE_DATOS_FINAL_V38_MAESTRO_SKU_CONSOLIDADO.xlsx` y
generando SQL estatico en `db_design_reference/seed/`.

## BD propia en Supabase (DEC-L072)

LOGISTICA vive en el proyecto Supabase **LOGISTICA-TDEM**
(`uwaenymwvixuocwbavnz`), en el esquema `logistica` con el rol
`logistica_app`. Del 2026-09-24 al 2026-11-19 vivio en la BD de RRHH-SYSTEM
(DEC-L016); el corte esta documentado en `06_PLAN_MUDANZA_SUPABASE.md`.
Reglas que se mantienen:

- Nada de FKs, vistas, funciones ni queries hacia otros esquemas. Los datos
  de RRHH se consumen solo por su API HTTP (DEC-L002).
- No agregar `logistica` a los *Exposed schemas* de la Data API de Supabase.
- Toda migracion usa `${schema}.`; `beforeMigrate.sql` frena cualquier
  intento de migrar sobre `public`.

**Respaldo propio** del esquema (cliente PostgreSQL 17):

```
pg_dump "postgresql://logistica_app.uwaenymwvixuocwbavnz:<pass>@aws-0-us-east-2.pooler.supabase.com:5432/postgres" --schema=logistica --no-owner --no-privileges -Fc -f logistica_AAAAMMDD.dump
```

### Como mudar LOGISTICA a otro proyecto

Procedimiento probado en la mudanza RR-HH -> LOGISTICA-TDEM (detalle en
`06_PLAN_MUDANZA_SUPABASE.md`):

1. En el proyecto nuevo: `db_design_reference/setup/rrhh_db_esquema_logistica.sql`,
   buckets `logistica-productos` (publico) y `logistica-privado` (privado) y
   llaves S3 propias. Comprobar que su Postgres sea igual o mas nuevo.
2. Suspender el servicio de Render y esperar a que sus conexiones JDBC
   desaparezcan de `pg_stat_activity` en la BD vieja.
3. `pg_dump` del esquema (comando de arriba) y `pg_restore --no-owner
   --no-privileges --exit-on-error` como `logistica_app`, **sin** la entrada
   `SCHEMA - logistica` de la lista (`pg_restore -l` / `-L`): el rol no puede
   crear esquemas. Incluye `flyway_schema_history`.
4. Copiar los archivos de los dos buckets con la misma clave y reescribir las
   URLs publicas (`documento.file_url` y las columnas `*_url`) **antes** de
   arrancar: si no, `LimpiezaFotos` (03:30) borra los archivos nuevos por
   huerfanos.
5. Cambiar `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` y las `STORAGE_*`;
   `alter role logistica_app nologin;` en la BD vieja; reanudar. **Sin
   cambios de codigo.**
6. A las 2 semanas: respaldo, `drop schema logistica cascade; drop role
   logistica_app;`, borrar los buckets viejos y revocar sus llaves S3.
