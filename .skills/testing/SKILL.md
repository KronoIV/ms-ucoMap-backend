---
name: testing
description: Cómo están hechas y cómo se ejecutan las pruebas del backend UCO MAP (JUnit 5, Mockito, WebMvcTest, configuración de test sin MongoDB, pruebas de carga k6). Úsala al escribir o ejecutar pruebas o al validar un cambio.
---

# Backend UCO MAP: pruebas

## Ejecutar

```bash
./mvnw test                                   # todas (189 a nov-2026), ~10 s, no requieren MongoDB
./mvnw test -Dtest=CampusEventServiceTest     # una clase
./mvnw test -Dtest='SecurityIntegrationTest#*login*'   # métodos por patrón
```

Reportes en `target/surefire-reports/`. El `Dockerfile` ejecuta `./mvnw package` **con** pruebas: si una falla,
la imagen no se construye (y el despliegue tampoco).

## Configuración de pruebas

`src/test/resources/application.properties` **reemplaza** al de `main` en el classpath de pruebas:
Mongo `localhost` (nunca producción), `app.data.init-on-startup=false`, `app.mongo.ensure-indexes=false`, secretos
ficticios (`app.jwt.secret`, `spring.mail.*`, `app.multiset.*`) y límites de rate altos. Si agregas una propiedad
nueva con `@Value` sin valor por defecto, agrégala también aquí o fallará el contexto de las pruebas.

## Tipos de prueba existentes (`src/test/java/co/edu/uco/ucomap/`)

| Tipo | Ejemplos | Patrón |
|---|---|---|
| Unitarias de servicio | `service/CampusEventServiceTest`, `GraphServiceTest`, `NavigationTripServiceTest`, `MultiSetUsageServiceTest`, `PasswordResetServiceTest`, `UserServiceTest`, `AppSettingServiceTest`, `BuildingNodeSyncTest`, `NavigationServiceTest`, `MapEditServiceTest`, `DeviceSessionServiceTest`, `AnalyticsServiceTest` | `@ExtendWith(MockitoExtension.class)`, repositorios con `@Mock`, aserciones AssertJ (`assertThat`, `assertThatThrownBy`) |
| Cálculo puro | `service/analytics/AnalyticsMathTest` | Sin mocks |
| Seguridad (filtros) | `security/filter/JwtAuthFilterTest`, `RateLimitFilterTest`, `security/jwt/JwtUtilTest` | Unitarias sobre filtros y JWT |
| Integración web + seguridad | `security/SecurityIntegrationTest` | `@WebMvcTest(controllers = {...})` + `@Import({SecurityConfig.class, CorsConfig.class, JwtUtil.class, UserDetailsServiceImpl.class, ClientIpResolver.class})`; servicios con `@MockitoBean`; MockMvc |
| Contexto | `BackendApplicationTests` | `@SpringBootTest` (carga el contexto; Mongo se conecta de forma diferida) |

`spring-security-test` no está en el `pom.xml`: los tokens de prueba se generan con `JwtUtil` real dentro de
`SecurityIntegrationTest`.

## Cómo escribir una prueba nueva

1. Servicio: copia la estructura de `CampusEventServiceTest` (mocks de repositorios, construir el servicio, `when(...)`,
   `verify(...)`). Prueba reglas de negocio y errores (`ResponseStatusException` con su estado).
2. Endpoint o regla de acceso: agrega el controlador a `@WebMvcTest(controllers = ...)` si no está, mockea su
   servicio con `@MockitoBean` y comprueba 200/401/403 con y sin token.
3. Nunca llames a servicios externos (MultiSet, SMTP, Atlas) en pruebas.

## Pruebas de carga (`load-tests/`)

Entorno aislado con su propio MongoDB (no toca producción) y k6:

```bash
docker compose -f load-tests/docker-compose.yml up -d --build --wait mongo backend
docker compose -f load-tests/docker-compose.yml run --rm -e TYPE=smoke k6 run /scripts/app-traffic.js   # TYPE=smoke|load|stress|spike
docker compose -f load-tests/docker-compose.yml down -v
```

El servicio `backend` recibe sus propiedades por argumentos (`command:`) con valores ficticios. El script
`k6/app-traffic.js` simula el tráfico de la app (`TYPE` por defecto `load`; también acepta `breakpoint`);
`monitor.sh` registra CPU/memoria de los contenedores cada 10 s. **Cuidado:** la protección del script contra
producción solo reconoce `onrender.com` y `netlify.app`; **no** detecta Railway. Nunca pongas `BASE_URL` apuntando
al backend real.

## Verificación manual rápida (backend local en :8080)

- `curl -s localhost:8080/api/settings` → 200 con `{"data":[...],"succeeded":true}`.
- `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/users` → 401 sin token.
- `curl -s -o /dev/null -w '%{http_code}' -X POST localhost:8080/api/multiset/token` → 503 si no hay credenciales MultiSet.
