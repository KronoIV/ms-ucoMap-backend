---
name: project-context
description: Contexto del backend de UCO MAP (Spring Boot 3.5 + MongoDB). Úsala al empezar cualquier tarea en este repo para saber qué hace, cómo se ejecuta, cómo se configura y qué no tocar.
---

# Backend UCO MAP: contexto del proyecto

## Propósito

API REST (`/api/**`) que da servicio a dos clientes:

- **Panel admin** (`uco-map-admin`, React): CRUD de datos del campus, navmesh, eventos, usuarios, analítica. Usa JWT.
- **App** (`uco-map-app`, web móvil AR): lee datos públicos y envía telemetría (sesiones y recorridos). Sin login.

Además firma los tokens del VPS propio
(`vps-server`; no le reenvía fotos) y envía el correo de
restablecer contraseña por SMTP (Gmail).

## Stack verificado (`pom.xml`)

- Java 21, Spring Boot 3.5.14 (`spring-boot-starter-parent`), Maven Wrapper (Maven 3.9.15, `mvnw`).
- Starters: web, data-mongodb, validation, security, mail, thymeleaf (plantilla de correo), devtools (runtime).
- JWT: `io.jsonwebtoken:jjwt-*` 0.12.6. Lombok (anotaciones `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`).
- Pruebas: `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc).

## Comandos

```bash
./mvnw spring-boot:run          # arranca en :8080 (lee backend/.env si existe)
./mvnw test                     # 199 pruebas, sin MongoDB
./mvnw -DskipTests package      # target/backend-0.0.1-SNAPSHOT.jar
docker build -t ucomap-backend . # imagen (el Dockerfile corre ./mvnw package CON pruebas)
```

MongoDB local para desarrollo: `docker run -d --name ucomap-mongo -p 27017:27017 mongo:7`.

## Configuración

- `src/main/resources/application.properties` no contiene secretos: usa `${VAR}`.
  `spring.config.import=optional:file:.env[.properties]` carga `backend/.env` (git-ignorado) si existe.
- Obligatorias: `MONGODB_URI`, `JWT_SECRET` (≥ 32 caracteres, lo valida `JwtUtil.init()`), `MAIL_USERNAME`,
  `MAIL_PASSWORD`.
- Opcionales con valor por defecto: `RATE_LIMIT_MAX`
  (600), `RATE_LIMIT_AUTH_MAX` (10), `RATE_LIMIT_WINDOW` (60), `RATE_LIMIT_TRUSTED_PROXIES`, `LOG_LEVEL` (INFO).
- VPS propio (opcionales; sin `VPS_BASE_URL` o `VPS_TOKEN_SECRET`, `/api/vps/*` responde 503): `VPS_BASE_URL`,
  `VPS_TOKEN_SECRET` (≥ 32), `VPS_TOKEN_KID` (k1), `VPS_MAP_SET_ID` (uco), `VPS_TOKEN_TTL_SECONDS` (600),
  `VPS_ADMIN_TOKEN_TTL_SECONDS` (1800).
- Plantilla: `.env.example`. Valores reales de producción: los entrega el responsable (nunca al repo).
- Las pruebas usan `src/test/resources/application.properties`, que reemplaza al de `main` (Mongo local, secretos
  ficticios, `app.data.init-on-startup=false`, `app.mongo.ensure-indexes=false`).

## Qué pasa al arrancar

1. `MongoIndexInitializer` crea índices (si `app.mongo.ensure-indexes` no es `false`).
2. Si `app.data.init-on-startup=true` (lo está en `application.properties`):
   - `DataInitializerService` siembra nodos, aristas, edificios, salones y clips **solo si cada colección está vacía**.
   - `AppSettingInitializer` crea `enableIndoorARNavigation` y `arOnLimitedDevices` si faltan.
   - `AdminInitializer` crea `admin@admin.com` (contraseña fija en el código) si no existe.
   - `BuildingNodeSync` reconcilia edificios ⇄ nodos `BUILDING` del grafo.
3. Tareas programadas (`@EnableScheduling`): `SessionEventPublisher` (cada 25 s, SSE).

## Producción

- Railway: `https://ucomap-backend.up.railway.app` (URL usada por admin y app). Cómo se despliega exactamente:
  preguntar al responsable. Debe tener definidas las variables obligatorias.
- `server.port=8080` fijo. `Dockerfile` (multi-stage, `eclipse-temurin:21`) expone 8080.

## No hacer

- No poner valores reales en `application.properties`, `.env.example`, pruebas ni logs.
- No apuntar `MONGODB_URI` local a la base de producción (al arrancar escribe índices, semillas y sincronización).
- No cambiar rutas, campos ni formas de respuesta sin revisar `uco-map-admin/src/services` y `uco-map-app/src/services`.
- No llamar a servicios externos en pruebas: usa mocks como en `NavigationTripServiceTest`.

## Más contexto

`.skills/architecture`, `database`, `api-development`, `security`, `testing`, `ecosystem`; `README.md`;
`docs/ECOSISTEMA.md`; `docs/GUIA-ESTUDIANTES.md`.
