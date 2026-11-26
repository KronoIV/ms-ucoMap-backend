---
name: security
description: Seguridad del backend UCO MAP (JWT, roles, endpoints públicos, rate limit, CORS, restablecer contraseña, manejo de secretos y variables de entorno). Úsala antes de tocar SecurityConfig, filtros, autenticación, configuración o cualquier dato sensible.
---

# Backend UCO MAP: seguridad

## Autenticación y autorización

- Login `POST /api/auth/login` (`AuthController`) → `AuthenticationManager` + `UserDetailsServiceImpl` (busca por
  email; usuarios inactivos no entran) → `JwtUtil.generateToken(email, role)`.
- JWT HS256 con `app.jwt.secret` (`JWT_SECRET`, ≥ 32 caracteres, se valida al arrancar) y expiración
  `app.jwt.expiration-ms` (86 400 000 ms = 24 h). Claims: `sub` = email, `role`.
- `JwtAuthFilter` lee solo la cabecera `Authorization: Bearer <token>` (no cookies). Rechaza tokens inválidos,
  expirados, de usuarios inexistentes/inactivos o **emitidos antes de `User.passwordChangedAt`** (cambiar la
  contraseña invalida las sesiones anteriores).
- Un único rol: `Role.ADMIN`. Sesión `STATELESS`, CSRF desactivado (API con token, sin cookies de sesión).
- Contraseñas con `BCryptPasswordEncoder`.

## Reglas de acceso (`security/config/SecurityConfig.java`)

Orden: primero las rutas públicas, luego `/api/{sessions,trips,analytics,users,multiset,navigation,events}/**`
como ADMIN y al final **todo POST/PUT/PATCH/DELETE en `/api/**` como ADMIN**; cualquier otra ruta exige
autenticación. Públicas: login/forgot/reset, `POST /api/sessions/ping`, `POST /api/trips`, `GET /api/trips/popular`,
`GET /api/campus/**`, `/api/buildings/**`, `/api/rooms/**`, `/api/graph/**`, `/api/settings/**`,
`/api/navigation/navmesh`, `/api/navigation/elevators`, `/api/events/active`, `POST /api/multiset/token`.

Al agregar un endpoint público: insértalo en el bloque «Public routes» y agrega un caso en `SecurityIntegrationTest`.
Los endpoints públicos de escritura (`ping`, `trips`) reciben datos de cualquier cliente: valida tamaños y formatos.

## Rate limit y proxies

- `RateLimitFilter` (antes de `JwtAuthFilter`): ventana deslizante por IP en memoria (`ConcurrentHashMap`).
  General `RATE_LIMIT_MAX` (600) y `/api/auth/**` `RATE_LIMIT_AUTH_MAX` (10) por `RATE_LIMIT_WINDOW` (60 s).
  Responde 429. El límite general es alto porque muchos estudiantes comparten la IP del Wi-Fi del campus.
- `ClientIpResolver` solo confía en `X-Forwarded-For` si la conexión viene de `RATE_LIMIT_TRUSTED_PROXIES`
  (por defecto redes privadas y loopback).
- Al estar en memoria, el contador no se comparte entre réplicas.

## CORS (`common/config/CorsConfig.java`)

`app.cors.allowed-origins=*` (todos los orígenes) para `/api/**`, sin credenciales. Para restringir, cambia el
valor por una lista separada por comas con los dominios del admin y de la app.

## Restablecer contraseña

`PasswordResetService`: token de 32 bytes de `SecureRandom`; en base se guarda solo su SHA-256; expira en
`app.password-reset.token-expiry-minutes` (60); enfriamiento `cooldown-minutes` (1); revoca tokens previos; la
respuesta no revela si el email existe. Enlace: `app.password-reset.frontend-url` + `reset-path` + `?token=`.
Correo con `EmailService` y la plantilla `templates/email/password-reset.html` vía SMTP (`MAIL_*`).

## Secretos y variables de entorno

| Variable | Secreta | Uso |
|---|---|---|
| `MONGODB_URI` | Sí (incluye usuario y contraseña) | Conexión a MongoDB |
| `JWT_SECRET` | Sí | Firma de tokens del panel |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Sí (contraseña de aplicación de Gmail) | Envío de correos |
| `MULTISET_CLIENT_ID` / `MULTISET_CLIENT_SECRET` | Sí | Credencial principal de MultiSet |
| `MULTISET_QUERY_CLIENT_ID` / `_SECRET` | Sí | Credencial solo-consulta para tokens públicos (opcional) |

Reglas:

1. `application.properties` solo contiene `${VAR}` para estos valores. Nunca escribas valores reales en el repo, en
   pruebas, en `.env.example` ni en mensajes de log.
2. En local van en `backend/.env` (git-ignorado; lo importa `spring.config.import=optional:file:.env[.properties]`).
   En producción, como variables del servicio en Railway. `.env.*` también está ignorado por git y Docker.
3. La app recibe de `POST /api/multiset/token` un token corto (`VpsTokenDTO`), nunca el secreto.
4. **Historial:** hasta noviembre de 2026 los secretos estaban escritos en `application.properties` de este repo
   público. Deben **rotarse** (Atlas, Gmail, MultiSet, JWT); quitarlos del archivo no los borra del historial.

## Riesgos conocidos (no corregidos)

- `AdminInitializer` crea `admin@admin.com` con contraseña fija en el código cuando falta y
  `app.data.init-on-startup=true` (también en producción).
- CORS abierto a cualquier origen.
- El admin guarda el JWT en una cookie legible por JavaScript (`ucomap_token`).

## Lista de verificación antes de un PR

- [ ] ¿Endpoint nuevo con la regla correcta (público o ADMIN) y prueba en `SecurityIntegrationTest`?
- [ ] ¿Respuestas sin campos sensibles (`passwordHash`, tokens, secretos)?
- [ ] ¿Entradas públicas validadas (`@Valid`, tamaños máximos)?
- [ ] ¿Ningún secreto nuevo en archivos versionados? (`git diff` antes de commitear)
