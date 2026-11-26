---
name: architecture
description: Organización del código del backend UCO MAP (paquetes, capas, filtros, arranque, convenciones). Úsala al ubicar dónde va un cambio o al leer el flujo de una petición.
---

# Backend UCO MAP: arquitectura y organización

Paquete raíz: `co.edu.uco.ucomap` en `src/main/java/co/edu/uco/ucomap/`.

```
BackendApplication.java          @SpringBootApplication + @EnableScheduling
controller/      14 @RestController (una por recurso; rutas /api/**)
service/         Lógica de negocio, inicializadores (CommandLineRunner/ApplicationRunner) y tareas @Scheduled
  analytics/     AnalyticsMath, UserAgents (cálculos puros, probados aparte)
repository/      19 interfaces MongoRepository (consultas derivadas por nombre)
model/           @Document de MongoDB + enums + objetos embebidos (GpsPoint, ArPoint, ...)
dto/             Records y clases de entrada/salida (AuthDTO, CampusDataDTO, TripReportDTO, PageResponse...)
security/
  config/SecurityConfig.java      Reglas por ruta y método, sesión STATELESS
  filter/JwtAuthFilter.java       Valida "Authorization: Bearer"
  filter/RateLimitFilter.java     Límite por IP (ventana deslizante en memoria)
  jwt/JwtUtil.java                Firma/lee JWT HS256
  service/UserDetailsServiceImpl  Carga usuarios por email
common/
  config/CorsConfig.java          CORS (app.cors.allowed-origins)
  config/MongoIndexInitializer    Índices al arrancar
  dto/ApiSuccess, ApiError, PageResponse
  error/ErrorCode, GlobalExceptionHandler
  logging/RequestLoggingFilter    requestId en MDC y cabecera X-Request-Id
  web/ClientIpResolver            IP real detrás de proxies de confianza
```

## Capas y flujo de una petición

`RequestLoggingFilter` → `RateLimitFilter` → `JwtAuthFilter` → reglas de `SecurityConfig` → `@RestController` →
`@Service` → `MongoRepository` → MongoDB. Las excepciones se convierten en `ApiError` en `GlobalExceptionHandler`.

- **Controladores** delgados: validan con `@Valid`, delegan en el servicio y envuelven con `ApiSuccess.of(...)`
  dentro de `ResponseEntity`. Ejemplo: `controller/CampusEventController.java`.
- **Servicios** con `@Service @RequiredArgsConstructor @Slf4j`; inyección por constructor (campos `final`).
  Lanzan `ResponseStatusException(HttpStatus.X, ErrorCode.Y.getMessage())` o un mensaje propio en español.
- **Repositorios**: `extends MongoRepository<Entidad, String>` con métodos derivados
  (`findByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc`). Para actualizaciones parciales o
  condicionales se usa `MongoTemplate` (p. ej. heartbeats de viajes en `NavigationTripService`).
- **Modelos**: clases Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` + `@Document(collection=...)`.
  En varios casos el propio modelo es el cuerpo de entrada (`CampusEvent`, `Room`), con anotaciones de validación.
- **DTOs**: `record` para respuestas inmutables (`VpsTokenDTO`, `TripSummaryDTO`, `StatsDTO`...); clases con
  subclases estáticas `@Data` para pares petición/respuesta (`AuthDTO.LoginRequest`, `AuthDTO.Response`).

## Componentes destacados

| Componente | Archivo | Qué hace |
|---|---|---|
| Datos del campus en una llamada | `service/CampusService.java`, `dto/CampusDataDTO.java` | Respuesta de `GET /api/campus` que consume la app (salones, grafo, bloques). |
| Grafo exterior | `service/GraphService.java` | CRUD de nodos/aristas; normaliza POI (`poiType`, `buildingId`, `floor`); filtra aristas huérfanas. |
| Edificio ⇄ nodo | `service/BuildingNodeSync.java` | Mantiene 1:1 `Building` y nodo `BUILDING`; reconcilia al arrancar. |
| Navmesh y navegación interior | `service/NavigationService.java`, `service/MapEditService.java` | Guarda el navmesh publicado (≤ 5 MB, documento único), conexiones, parches, ascensores, ediciones del escaneo y bloqueos. |
| Viajes | `service/NavigationTripService.java` | Upsert público por `tripId`, heartbeats, expiración por inactividad, resumen y CSV. |
| Sesiones | `service/DeviceSessionService.java`, `SessionEventPublisher.java` | Ping de la app, estadísticas y stream SSE para el panel. |
| Analítica | `service/AnalyticsService.java`, `service/analytics/*` | Indicadores por periodo, zona horaria y plataforma. |
| MultiSet | `service/MultiSetService.java`, `MultiSetUsageService.java` | Tokens (caché), URLs de mallas, estimación de cuota y apagado automático de la AR. |
| Contraseña | `service/PasswordResetService.java`, `EmailService.java`, `templates/email/password-reset.html` | Token aleatorio guardado como hash, enfriamiento, correo Thymeleaf. |

## Convenciones

- Identificadores en inglés; comentarios, mensajes de error y logs en **español**.
- Logs con `@Slf4j` y parámetros `{}`; nunca registrar contraseñas, tokens ni URIs con credenciales. El patrón de
  log elimina saltos de línea (prevención de inyección en logs).
- Fechas como `Instant` (UTC). Coordenadas GPS en `GpsPoint {lat, lng}`; coordenadas AR en `ArPoint {x, y, z}`
  (espacio del map set de MultiSet).
- Borrado lógico frecuente con `active=false` (edificios, nodos, salones, usuarios) en lugar de eliminar.

## Dónde poner un cambio

| Necesito… | Toca |
|---|---|
| Un endpoint nuevo | `controller/` + `service/` (+ `dto/`), regla en `SecurityConfig` si es público |
| Un campo nuevo persistido | `model/` (tolerar `null` en documentos viejos) y, si lo usa la app, `CampusService`/`CampusDataDTO` |
| Una consulta | `repository/` (método derivado) o `MongoTemplate` en el servicio |
| Un índice | `common/config/MongoIndexInitializer.java` |
| Un parámetro configurable en caliente | `AppSettingInitializer` (semilla) + `AppSettingService` (el admin lo edita en Configuración) |
| Un valor de despliegue o secreto | `application.properties` con `${VAR}` + `.env.example` + README |
