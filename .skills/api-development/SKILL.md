---
name: api-development
description: Cómo crear o modificar endpoints REST en el backend UCO MAP siguiendo sus convenciones (sobre ApiSuccess, errores, validación, seguridad, paginación). Incluye la referencia completa de endpoints. Úsala al tocar cualquier controller, DTO o contrato /api.
---

# Backend UCO MAP: desarrollo de API

## Contrato común

- Éxito: `ResponseEntity.ok(ApiSuccess.of(data))` → `{"data": ..., "succeeded": true}`.
  Solo mensaje: `ApiSuccess.ofMessage("...")`. Creación: `ResponseEntity.status(HttpStatus.CREATED)`.
- Error: lanza `ResponseStatusException(HttpStatus.X, mensaje)`; `GlobalExceptionHandler` responde
  `{"error": mensaje, "succeeded": false, "details"?: {...}}`. Mensajes estándar en `common/error/ErrorCode.java`
  (`RESOURCE_NOT_FOUND`, `CONFLICT`, `VALIDATION_ERROR`, ...), en español.
- Validación: `@Valid @RequestBody` + Jakarta Validation (`@NotBlank`, `@Size`, `@Email`...). Errores de campo →
  400 con `details`. JSON ilegible → 400. Tipo de parámetro inválido → 400. Ruta inexistente → 404. Otro → 500.
- Paginación: `PageResponse.of(content, page, size, totalElements)` con `PageResponse.safePage/safeSize`
  (tamaño acotado a `PageResponse.MAX_SIZE` = 100).
- Los clientes dependen del sobre: el admin lo quita en `uco-map-admin/src/services/api.ts`; la app con `unwrapApi()`.

## Ejemplo real de punta a punta: eventos del campus

1. **Modelo** `model/CampusEvent.java`: `@Document(collection = "campus_events")`, validaciones en campos y
   `withFieldsOf(src)` para copiar lo editable.
2. **Repositorio** `repository/CampusEventRepository.java`:
   ```java
   List<CampusEvent> findAllByOrderByStartsAtDesc();
   List<CampusEvent> findByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(Instant startedBy, Instant endsAfter);
   ```
3. **Servicio** `service/CampusEventService.java`: `findAll`, `visibleNow`, `create`, `update`, `delete`;
   `validate()` (duración ≤ 90 días, lugar existente) lanza `ResponseStatusException(BAD_REQUEST, ...)`.
4. **Controlador** `controller/CampusEventController.java`:
   ```java
   @GetMapping("/active")
   public ResponseEntity<ApiSuccess<List<CampusEvent>>> getActive() {
       // Depende de la hora: sin caché para que un evento que termina desaparezca a tiempo
       return ResponseEntity.ok().cacheControl(CacheControl.noStore())
               .body(ApiSuccess.of(eventService.visibleNow()));
   }

   @PostMapping
   public ResponseEntity<ApiSuccess<CampusEvent>> create(@Valid @RequestBody CampusEvent event) {
       return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccess.of(eventService.create(event)));
   }
   ```
5. **Seguridad** `security/config/SecurityConfig.java`: `GET /api/events/active` en la sección pública;
   `"/api/events", "/api/events/**"` con `hasRole("ADMIN")`.
6. **Índice** en `MongoIndexInitializer`: `campus_events.endsAt`.
7. **Prueba** `src/test/java/.../service/CampusEventServiceTest.java` (Mockito) y casos en `SecurityIntegrationTest`.
8. **Clientes**: admin `src/services/eventService.ts` + `src/pages/EventsPage.tsx`; app
   `src/services/campus-events.ts` + `src/ui/campus-events.ts`.

## Pasos para un endpoint nuevo

1. Modelo/DTO (record si es solo respuesta). Campos nuevos opcionales para no romper documentos existentes.
2. Método en el repositorio (derivado) o `MongoTemplate` en el servicio.
3. Lógica en el servicio con mensajes de error en español; registra con `log.info/warn` sin datos sensibles.
4. Controlador delgado que devuelva `ApiSuccess`.
5. Regla en `SecurityConfig`: si es público, agrégalo en el bloque «Public routes» (antes de las reglas ADMIN; el
   orden importa). Si no, queda protegido por las reglas generales.
6. Pruebas: servicio con Mockito y, si cambia la seguridad, un caso en `SecurityIntegrationTest`.
7. Actualiza el cliente (admin `src/services` + `src/types`, o app `src/services`) y la tabla de endpoints del README.

## Referencia de endpoints (verificada en `controller/` y `SecurityConfig`)

Público = sin token. ADMIN = `Authorization: Bearer <jwt>` con rol ADMIN.

| Método y ruta | Acceso | Controlador |
|---|---|---|
| `POST /api/auth/login`, `/forgot-password`, `/reset-password` | Público | `AuthController` |
| `GET /api/campus` | Público | `CampusController` |
| `GET /api/buildings`, `/api/buildings/{buildingId}` | Público | `BuildingController` |
| `POST /api/buildings`, `PUT/DELETE /api/buildings/{buildingId}` | ADMIN | `BuildingController` |
| `GET /api/poi-clips`, `/api/poi-clips/map` | Público | `BuildingController` |
| `POST /api/poi-clips`, `DELETE /api/poi-clips/{clipId}` | ADMIN | `BuildingController` |
| `GET /api/rooms` (`?category=`), `/api/rooms/{roomId}` | Público | `RoomController` |
| `POST /api/rooms`, `PUT/PATCH/DELETE /api/rooms/{roomId}` | ADMIN | `RoomController` |
| `GET /api/graph/nodes` (`?type=`), `/nodes/map`, `/nodes/{nodeId}`, `/edges`, `/edges/pairs` | Público | `GraphController` |
| `POST /api/graph/nodes`, `PUT/DELETE /nodes/{nodeId}`, `POST /edges`, `DELETE /edges/{edgeId}` | ADMIN | `GraphController` |
| `GET /api/settings`, `/api/settings/{key}` | Público | `AppSettingController` |
| `POST /api/settings`, `PUT/DELETE /api/settings/{key}` | ADMIN | `AppSettingController` |
| `GET /api/events/active` | Público | `CampusEventController` |
| `GET/POST /api/events`, `PUT/DELETE /api/events/{id}` | ADMIN | `CampusEventController` |
| `POST /api/sessions/ping` | Público | `DeviceSessionController` |
| `GET /api/sessions` (paginado, `q`, `status`), `/stats`, `/stream` (SSE) | ADMIN | `DeviceSessionController` |
| `POST /api/trips`, `GET /api/trips/popular` | Público | `NavigationTripController` |
| `GET /api/trips` (paginado, `from`, `to`, `building`), `/summary`, `/export` (CSV) | ADMIN | `NavigationTripController` |
| `GET /api/analytics`, `/api/analytics/trips` | ADMIN | `AnalyticsController` |
| `GET /api/navigation/navmesh` (binario), `/elevators` | Público | `NavigationController` |
| `GET /api/navigation/navmesh/info`, `PUT/DELETE /navmesh` | ADMIN | `NavigationController` |
| `GET/POST /api/navigation/connections`, `POST /connections/bulk`, `PUT/DELETE /connections/{id}` | ADMIN | `NavigationController` |
| `GET/POST /api/navigation/patches`, `PUT/DELETE /patches/{id}` | ADMIN | `NavigationController` |
| `POST /api/navigation/elevators`, `PUT/DELETE /elevators/{id}` | ADMIN | `NavigationController` |
| `GET/POST /api/navigation/scan-edits`, `DELETE /scan-edits/{id}` | ADMIN | `MapEditController` |
| `GET/POST /api/navigation/blocks`, `DELETE /blocks/{id}` | ADMIN | `MapEditController` |
| `POST /api/vps/token` (cuerpo opcional `{sessionId}`; 503 si no está configurado) | Público | `UcoVpsController` |
| `POST /api/vps/admin-token` | ADMIN | `UcoVpsController` |
| `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}` | ADMIN | `UserController` |

## Errores frecuentes

- Olvidar `ApiSuccess` → el admin y la app reciben otra forma y fallan al desenvolver.
- Nuevo GET público que queda protegido (401) por no agregarlo a «Public routes».
- Renombrar un campo JSON del modelo: rompe a los clientes y a los documentos existentes.
- Exponer `passwordHash` u otros campos internos: usa un DTO de respuesta (ver `UserDTO.Response`).
- Respuestas que dependen de la hora sin `CacheControl.noStore()`.
