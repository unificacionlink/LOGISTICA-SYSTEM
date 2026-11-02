# Instrucciones del proyecto

- Considerar siempre los archivos de la carpeta `docs_base/` como referencia base del proyecto (documentación maestra, catálogo de SKU, metodología, matriz de funcionalidades) antes de tomar decisiones de diseño o implementación.

## Qué es

ERP Logístico Corporativo para TDEM, GOYDEL y METRIN. Reemplaza el prototipo V38 de Jonathan (HTML + localStorage, en `docs_base/`) por un sistema productivo. Flujo principal:
Requerimiento → Aprobación → Stock/Compra (RFQ → Cotización → OC) → Recepción → Despacho (Nota de Inventario) → Envío → Entrega → Tracking.

Roles: `SOLICITANTE`, `LOGISTICA`, `APROBADOR`, `GERENCIA`, `PYC`, `ADMIN` (DEC-L030). No hay perfil Compras ni Almacén: los opera LOGISTICA/ADMIN. Cada CECO puede tener un aprobador asignado (`ceco.approver_user_id`, se asigna desde la ficha del usuario en Administración, DEC-L037); la regla de quién decide/ve está en `modules/aprobaciones/AmbitoAprobacion`.

**En curso: adopción de la V38.7** (entrega del 06/10/2026 en `docs_base/V38_7_ENTREGA_2026-10-06/`, ver DEC-L030). Lotes: 12 roles/aprobador por CECO (hecho) → 13 precios de venta + plazo de atención (hecho, DEC-L035) → 14 Presupuestos (hecho, DEC-L036) → 15 control presupuestal + cumplimiento → 16 necesidad/consolidación/RFQ multiproducto → 17 OC con aprobación, pago y liberación → 18 almacén V38.7 → 19 proveedores + dashboard.

## Documentación (leer antes de cambios no triviales)

- `docs_base/` — material fuente de Jonathan (gitignored): diccionario de datos y catálogo de 725 SKU (xlsx), matriz de funcionalidades (RF-xxx), informe de metodología, prototipo HTML V38. `docs_base/V38_7_ENTREGA_2026-10-06/` — la V38.7 (módulos JS en `codigo/`, base demo, matriz de 24 funcionalidades).
- `docs/01_GUIA_GENERAL.md` — qué es, roles.
- `docs/02_GUIA_TECNICA.md` — stack, estructura, capas, migraciones, variables de entorno.
- `docs/03_GUIA_OPERATIVA.md` — arranque local, usuarios de prueba, despliegue, BD compartida con RRHH.
- `docs/04_DECISIONES.md` — decisiones DEC-L001..L037 y pendientes de negocio PD-01..PD-08. **Toda decisión de arquitectura/negocio nueva se registra aquí (DEC-L0xx) y en `docs/CHANGELOG.md`** (versión semver, la más reciente arriba).
- `db_design_reference/migrations/` — las 33 migraciones originales (diseño de referencia, con RLS). **No se aplican**: al construir un lote nuevo se traducen a un `V#__*.sql` de Flyway sin RLS.

## Stack

- **Backend** (`backend/`): Java 21 + Spring Boot 4.1 (Maven), JPA/Hibernate, Flyway, Spring Security + JWT propio en cookie httpOnly `auth_token`, WebSocket/STOMP (`/ws`, sin SockJS), AWS SDK S3, Tess4j + PDFBox (OCR de cotizaciones), Apache POI (Excel), Lombok.
- **Frontend** (`frontend/`): React 19 + TypeScript + Vite + react-router-dom 7 + Tailwind v4, `lucide-react`, `@stomp/stompjs`. Sin librería de componentes (componentes propios en `components/ui/`).
- **BD**: Postgres de Supabase del proyecto **RRHH-SYSTEM**, esquema propio `logistica`, rol `logistica_app` (DEC-L016). Postgres puro vía JDBC, **sin RLS**.
- **Archivos**: Supabase Storage (API S3) del proyecto RRHH, bucket público `logistica-productos` (DEC-L015). La BD solo guarda URLs.
- **Despliegue**: Render, un único Web Service Docker (`Dockerfile` raíz): el build de React se copia a `backend/src/main/resources/static` y lo sirve Spring (sin CORS en prod). Health: `/api/health`.

## Comandos

Backend (desde `backend/`; en PowerShell el `-D...` va entre comillas):
```
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"                 # arranca, Flyway migra solo
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local,seed-test-data"  # crea usuarios de prueba
./mvnw -q compile
./mvnw test                                   # usa Postgres 17 embebido (zonky), sin Docker
./mvnw test -Dtest=AlmacenFlujoTest           # un solo test
./mvnw package
```
Config local en `backend/src/main/resources/application-local.yml` (gitignored). Conectar por el **pooler de Supabase, puerto 5432 (modo sesión)**, nunca la conexión directa (solo IPv6) ni el usuario `postgres`.

Frontend (desde `frontend/`, sirve en `:5173`, `VITE_API_URL` en `.env.local`):
```
npm run dev
npm run build     # tsc -b && vite build
npm run lint      # oxlint
```

Usuarios de prueba (password `Logistica2026!`): `solicitante@`, `logistica@`, `aprobador@`, `gerencia@`, `pyc@`, `admin@` + `logistica.test`.

Scripts Python en `scripts/`: `seed_catalogo_sku.py` (regenera seed de 725 SKU desde el Excel) y `cargar_imagenes.py` (carga masiva de fotos a S3).

## Arquitectura backend (`backend/src/main/java/com/tdem/logistica/`)

- `config/` — `SecurityConfig` (incluye CORS y lista `permitAll()`), `SpaController`, `WebSocketConfig`, `HealthController`.
- `security/` — JWT (`JwtService`, `JwtAuthFilter`), `UsuarioPrincipal`, interceptores de handshake/STOMP.
- `common/` — `ApiResponse<T>` `{ok, result, message, code, error}` (contrato de toda respuesta), `ApiException`, `GlobalExceptionHandler`, `AppConstants` (estados, fieles a los CHECK de la BD), `Auditoria` (acciones criticas a `log_cambios`, RF-064).
- `entity/` + `repository/` — entidades JPA 1:1 con las tablas (columnas snake_case vía `@Column`), Spring Data JPA.
- `realtime/RealtimeNotifier` — único punto que publica eventos WebSocket.
- `storage/` — `StorageClient`/`S3StorageClient`, `OcrExtractor`.
- `modules/identidad/` — sync de solo lectura con RRHH-SYSTEM (`PersonalSyncService`), padrón manual de GOYDEL/METRIN por Excel (`PersonalManualService`, DEC-L028) y Administración (`/api/admin`, solo ADMIN). `security/AccesoVigente` da estado y roles vigentes en cada request (no los del JWT).
- `documentos/` — PDFs corporativos con PDFBox (`PdfCorporativo` = maqueta, `DocumentosPdf` = contenido; cada PDF se arma desde el DTO de su pantalla para heredar permisos) y `Descargas` para respuestas de archivo.
- `modules/<modulo>/` — `requerimientos`, `aprobaciones`, `presupuestos`, `stock`, `proveedores`, `compras`, `almacen`, `tracking`, `dashboard`, `auth`. Cada uno: `*Controller` (`@RestController`) → `*Service` (lógica + autorización, devuelve `ApiResponse<T>`) → repositorios; `*Mapper` con métodos estáticos a mano (sin MapStruct); `dto/` con Bean Validation + `@Valid`.
- `TestDataSeeder` — profile `seed-test-data`.

Reglas clave:
- **La autorización vive 100% en los `*Service`** (no hay RLS de respaldo): validar rol/ownership al inicio de cada método con `UsuarioPrincipal`. Reutilizar `RequerimientoService.exigirAcceso` para la visibilidad de un REQ. El dueño solo puede pasar su REQ de `BORRADOR` a `PENDIENTE_APROBACION`; otras transiciones requieren LOGISTICA/ADMIN.
- `spring.jpa.open-in-view=false`: servicios que tocan asociaciones LAZY necesitan `@Transactional` (o `readOnly = true`).
- La BD está lejos (~150 ms por ida y vuelta): evitar N+1; preferir una consulta (el dashboard es una sola SQL que devuelve JSON).
- El SOLICITANTE solo pide para sus CECOs asignados (`usuario_ceco`, de cualquier empresa); la empresa del REQ sale del CECO (DEC-L029). LOGISTICA/ADMIN, cualquier CECO activo.
- Al aprobar un REQ (`AprobacionService.decidir`) se reserva stock disponible y se crean RFQ automáticas al proveedor habitual (DEC-L018). El estado de atención (PARCIAL/COMPLETA) se calcula solo desde envíos recepcionados (`AtencionCalculator`). El Tracking se reconstruye desde los datos, no tiene tabla propia (DEC-L019).
- **Nueva ruta del frontend** (`frontend/src/App.tsx`) ⇒ agregarla también a `SpaController` (lista explícita, no regex catch-all) y a `permitAll()` en `SecurityConfig`.

## Migraciones (Flyway, `backend/src/main/resources/db/migration/`)

- Siguiente migración: `V19__descripcion.sql` (existen V1..V18).
- Siempre usar el placeholder `${schema}.` — **nunca `public.`**. `beforeMigrate.sql` aborta si el esquema fuera `public`. Sin RLS.
- Nada de FKs, vistas ni queries hacia otros esquemas: la integración con RRHH es solo por su API HTTP (DEC-L002/DEC-L016).
- Hibernate está en `ddl-auto: validate`: toda columna nueva en una entidad requiere su migración.
- `MigracionesFlywayTest` y `LogisticaApplicationTests` corren contra `BdRrhhSimulada` (Postgres embebido que imita la BD de RRHH); `AlmacenFlujoTest` y `AtencionAutomaticaTest` cubren el flujo end-to-end.

## Frontend (`frontend/src/`)

- `api/` — un archivo por módulo; `http.ts` es el cliente fetch (siempre `credentials: "include"`, captura errores de red y devuelve `ApiResponse`), `types.ts` con los tipos compartidos, `progreso.ts` alimenta la barra de carga.
- `auth/AuthContext.tsx` — sesión y `tieneAlgunRol`. `components/ProtectedLayout` llama `GET /api/auth/me` y redirige a `/login`; contiene el sidebar colapsable.
- `pages/` — una por pantalla; `components/` — badges de estado, buscador de productos, dashboard, tracking, escena animada del login; `components/ui/` — primitivas (Button, Card, Field, Alert, etc.).
- `realtime/` — cliente STOMP.
- Estilos: tokens en `styles/globals.css` (paleta azul corporativa, DEC-L021); fuentes Geist / Newsreader. Respetar `prefers-reduced-motion` (excepto la escena del login).

## Convenciones

- Idioma: código de dominio, UI, comentarios y docs en español (sin tildes en identificadores/código; la UI sí lleva tildes). Columnas de BD en inglés snake_case según el diccionario de datos.
- El usuario prefiere pantallas sin textos de ayuda largos (sin subtítulos/párrafos explicativos).
- **Skeletons para toda carga de datos**: cada pantalla, tarjeta o sección que espera datos muestra esqueletos (`components/ui/Skeleton`) con la forma del contenido final, nunca un "Cargando..." ni una capa que tape la pantalla. La capa bloqueante (`CapaCarga`) es solo para guardar; lecturas y descargas usan la barra fina superior.
- **Acciones sensibles se confirman** con `useConfirmar()` (`components/ui/Confirmacion.tsx`), nunca `window.confirm`; las irreversibles piden motivo y el backend lo exige y lo audita con `Auditoria`.
- **No violar la ley de Hick**: pocas opciones visibles a la vez. Una acción principal por pantalla; acciones secundarias agrupadas o en menú; formularios largos por pasos o secciones plegables; selects con muchas opciones → buscador/combobox; detalle secundario en desplegables (como las tarjetas de Requerimientos) en vez de todo expuesto. Al agregar un botón o filtro, preguntarse qué se puede quitar u ocultar.
- Los requisitos se referencian por su código de la matriz V38 (RF-xxx, RN-xxx) en comentarios y decisiones.
- Nunca versionar secretos (`application-local.yml`, `.env*`, `.mcp.json`).
