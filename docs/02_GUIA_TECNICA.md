# Guia tecnica — ERP Logistico Corporativo

Stack migrado de Next.js+Supabase a Spring Boot + React el 2026-09-18
(pedido explicito del usuario, ver DEC-L008 en `docs/04_DECISIONES.md`).
Convenciones de capas por modulo tomadas del proyecto hermano `RRHH-SYSTEM`
y traducidas a Java/React.

## Stack

- **Backend**: Java 21 LTS + Spring Boot 4.1 (Maven) — `backend/`.
- **Frontend**: React 19 + TypeScript + Vite + react-router-dom — `frontend/`.
- **Base de datos**: la BD Postgres de Supabase de **RRHH-SYSTEM**, en un
  esquema propio `logistica` con rol dedicado `logistica_app` (DEC-L016;
  antes usaba el proyecto `LOGISTICA-TDEM`, que se retira). Postgres puro via
  JDBC/JPA, sin RLS (DEC-L008): la autorizacion por rol vive 100% en la capa
  de servicio de Spring. Nada de FKs/vistas/queries hacia tablas de RRHH: la
  integracion con RRHH es solo por su API HTTP.
- **Archivos** (fotos de producto, cotizaciones): Supabase Storage del
  proyecto de RRHH via su API S3 (`storage/S3StorageClient`), bucket publico
  `logistica-productos`. Cloudflare R2 queda como alternativa por
  configuracion (DEC-L015).
- **Autenticacion**: Spring Security + JWT propio en cookie httpOnly
  (`auth_token`). Supabase Auth ya no se usa.
- **Tiempo real**: WebSockets/STOMP (`/ws`, WebSocket nativo, sin SockJS —
  ver DEC-L010) para que la bandeja de Aprobaciones y el detalle de un
  requerimiento se actualicen solos, sin refrescar.
- **Despliegue**: Render, Web Service unico via Docker (`Dockerfile` en la
  raiz) — el build de React se empaqueta dentro del jar de Spring Boot
  (`backend/src/main/resources/static`) para evitar CORS en produccion.

## Backend (`backend/src/main/java/com/tdem/logistica/`)

```
config/       SecurityConfig, CorsConfig (dentro de SecurityConfig), HealthController, SpaController, WebSocketConfig
security/     JwtService, JwtAuthFilter, JsonAuthEntryPoint, UsuarioPrincipal,
              WebSocketAuthHandshakeInterceptor, StompChannelInterceptor, StompPrincipal
common/       ApiResponse<T>, ApiException, GlobalExceptionHandler, AppConstants
entity/       @Entity JPA, 1:1 con las tablas ya desplegadas (columnas snake_case via @Column)
repository/   Spring Data JPA
realtime/     RealtimeNotifier (unico punto desde donde se publican eventos WebSocket)
modules/
  auth/           AuthController, AuthService, LoginRequest, UsuarioDto
  requerimientos/ RequerimientoController, RequerimientoService, RequerimientoMapper, dto/
  aprobaciones/   AprobacionController, AprobacionService, dto/ (Lote 2 — ver DEC-L009)
  almacen/        AlmacenController, RecepcionService, DespachoService,
                  AtencionCalculator (RF-079), AlmacenMapper, dto/ (Lote 6 — ver DEC-L017)
  tracking/       TrackingController (/api/requerimientos/{id}/tracking[/excel]),
                  TrackingService, TrackingExcel (Apache POI), dto/ (Lote 7 — ver DEC-L019)
  dashboard/      DashboardController (/api/dashboard), DashboardService (una
                  sola consulta SQL -> JSON), DashboardDto (ver DEC-L020)
  almacen/CargaMasivaService  plantilla/validacion/confirmacion de ingresos por Excel (RF-031, DEC-L022)
modules/cajachica/  CajaChicaService/Controller (/api/caja-chica): regla, sugerencia, compra y regularizacion — DEC-L026
modules/maestro/    MaestroService/Controller (/api/maestro): alta de SKU y solicitudes de alta — DEC-L025
modules/identidad/  RrhhClient, PersonalSyncService (sync RRHH), SyncProgramado (diario),
              AdminService/Controller (/api/admin) — DEC-L024
documentos/   PdfCorporativo (maqueta PDFBox), DocumentosPdf (contenido por documento),
              DocumentosService/Controller (PDFs de REQ, RFQ, OC, NI y Tracking), Descargas (DEC-L022)
```

**Rutas del SPA en `SpaController`**: lista explicita, no un regex
catch-all — uno demasiado abierto puede matchear su propio destino de
forward o rutas reservadas como `/ws` y causar un `StackOverflowError`
recursivo (paso de verdad en este proyecto, ver DEC-L010). Cualquier ruta
nueva del frontend (`frontend/src/App.tsx`) debe agregarse tambien aqui y
en `SecurityConfig` (lista de `permitAll()`).

**Capas por modulo** (equivalente Java del patron `validator/mapper/data/service/actions` de Next.js):

| Antes (Next.js)              | Ahora (Spring Boot)                                             |
|-------------------------------|-------------------------------------------------------------------|
| `*.validator.ts` (Zod)        | Bean Validation (`@NotNull`, `@Size`...) en el DTO + `@Valid`     |
| `*.mapper.ts`                 | Metodos estaticos en `*Mapper` (a mano, sin MapStruct)            |
| `*.data.ts` (Supabase client) | Spring Data JPA `Repository` + `@Query` donde haga falta          |
| `*.service.ts`                | `@Service`, misma logica + autorizacion, devuelve `ApiResponse<T>`|
| `*.actions.ts`                | `@RestController`                                                 |
| `CONFIG.TABLES.*`             | Ya no aplica — las entidades JPA mapean la tabla via `@Table`      |

**Autorizacion**: cada `*Service` valida rol/ownership al inicio de cada
metodo usando `SecurityContextHolder` (via `@AuthenticationPrincipal
UsuarioPrincipal`). Ya no hay RLS de respaldo — toda regla de negocio sobre
quien puede ver/crear/editar vive aqui. Regla notable heredada de un bug real
encontrado en RLS (DEC-L007): el dueno de un requerimiento puede pasarlo de
`BORRADOR` a `PENDIENTE_APROBACION`; cualquier otra transicion requiere
LOGISTICA/ADMIN — reimplementada en `RequerimientoService.enviar()`.

**Lazy loading**: los `@Service` que leen asociaciones `FetchType.LAZY` fuera
de queries con `join fetch` necesitan `@Transactional` (u
`@Transactional(readOnly = true)` a nivel de clase); si no, Hibernate lanza
`LazyInitializationException` porque `spring.jpa.open-in-view=false`.

## Frontend (`frontend/src/`)

```
api/          http.ts (cliente fetch con cookies), auth.ts, requerimientos.ts, types.ts
auth/         AuthContext.tsx (sesion, roles, tieneAlgunRol)
components/   ProtectedLayout, EstadoBadge, ProductoCombobox
pages/        Login, Inicio, Requerimientos, RequerimientosNuevo, RequerimientoDetalle
styles/       globals.css (mismo sistema de estilos propio, sin libreria de UI)
```

`ProtectedLayout` llama `GET /api/auth/me` al montar y redirige a `/login` si
no hay sesion (equivalente a `middleware.ts` + `app/(panel)/layout.tsx` de
Next.js). El cliente HTTP (`api/http.ts`) siempre manda `credentials:
"include"` para que la cookie `auth_token` viaje con cada request.

## Base de datos / Migraciones

Flyway, `backend/src/main/resources/db/migration/`:

Todo se crea en el esquema `${schema}` (placeholder de Flyway, default
`logistica`, variable `DB_SCHEMA`) — **nunca escribir `public.`** en una
migracion nueva. `beforeMigrate.sql` (callback de Flyway) aborta cualquier
migrate si el esquema fuera `public` (DEC-L016). Sin `baseline-on-migrate`:
el esquema nace vacio y se aplican V1..Vn completas.

- `V1__baseline_lote1.sql` — snapshot de las 17 migraciones + seeds de
  Next.js (Lote 1: Requerimientos, catalogo de 725 SKU, roles, empresas,
  CECOs `ADM`/`OPS`), sin las funciones/policies de Supabase Auth (DEC-L016).
- `V2__disable_rls.sql` — no-op (antes borraba policies de todo `public`).
- `V3__usuario_password_hash.sql` — agrega `usuario.password_hash` (BCrypt).
- `V4`..`V7` — Aprobaciones, Stock, imagenes de producto, Compras.
- `V8__almacen_recepcion_despacho.sql` — Lote 6 (DEC-L017): recepcion de OC,
  incidencias, guia, salida, nota de inventario, envio, origen de reservas.
- `V9__proveedor_habitual_producto.sql` — proveedor habitual por producto,
  destino de la RFQ automatica al aprobar (DEC-L018).

Cobertura: `MigracionesFlywayTest` corre todas las migraciones contra un
Postgres 17 real embebido (sin Docker) que imita la BD de RRHH y verifica
que no se toque `public`; `LogisticaApplicationTests` arranca la app completa
contra esa misma BD (Hibernate `validate` + usuarios de prueba).

`db_design_reference/migrations/` (ex `supabase/migrations/`) conserva las
33 migraciones originales de Jonathan (incluye los Lotes 2+ aun no
construidos: Cotizaciones, Compras, Caja Chica, etc.) como referencia de
diseno — **no se auto-aplican**; cuando se construya el siguiente lote, se
traducen a un nuevo `V#__...sql` de Flyway sin las sentencias RLS.

## Variables de entorno

Backend (`backend/src/main/resources/application-local.yml`, gitignored):
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (Postgres via pooler de Supabase —
**usar el pooler, no la conexion directa**: ver nota en 03_GUIA_OPERATIVA.md),
`DB_SCHEMA` (default `logistica`), `DB_POOL_SIZE` (default 5),
`JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `COOKIE_SECURE`.
Storage S3 (DEC-L015/DEC-L016): `STORAGE_ENDPOINT`, `STORAGE_REGION`,
`STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY`, `STORAGE_BUCKET`
(default `logistica-productos`), `STORAGE_PUBLIC_URL`.
- Supabase (actual): endpoint `https://<ref>.storage.supabase.co/storage/v1/s3`,
  region del proyecto (p. ej. `us-east-2`), llaves de *Storage -> S3
  Connection* (**no** la service_role key: esa da acceso total a la BD de
  RRHH), URL publica `https://<ref>.supabase.co/storage/v1/object/public/logistica-productos`.
- Cloudflare R2 (opcional): endpoint `https://<account>.r2.cloudflarestorage.com`,
  region `auto`, token de API de R2, URL publica del bucket (dominio propio o r2.dev).

Sincronizacion con RRHH-SYSTEM (DEC-L024): `RRHH_API_URL` (URL base de
RRHH, sin la ruta), `RRHH_SYNC_SERVICE_TOKEN` (el mismo valor que la
variable homonima de RRHH-SYSTEM) y `RRHH_SYNC_CRON` (default
`0 0 6 * * *`, hora de Lima). Sin `RRHH_API_URL` el sync queda apagado.

Frontend (`frontend/.env.local`, gitignored): `VITE_API_URL`.

## Scripts

- Backend: `./mvnw spring-boot:run` / `./mvnw -q compile` / `./mvnw package`.
- Frontend: `npm run dev` / `npm run build`.
- `scripts/seed_catalogo_sku.py` (no depende de Supabase):
  regenera `db_design_reference/seed/0001_catalogo_producto.sql` desde el
  Excel de Jonathan si hace falta re-generar el seed de los 725 SKU.
- `scripts/cargar_imagenes.py`: carga masiva de fotos de producto al
  storage S3 (archivos nombrados `<SKU>.<ext>` o `<codigo-variante>.<ext>`) y
  genera `db_design_reference/seed/0002_imagenes_producto.sql` para aplicar
  en la BD. Requiere `pip install boto3 pillow` y las variables `STORAGE_*`.
