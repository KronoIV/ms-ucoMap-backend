# UCO MAP — Backend (API)

API REST de **UCO MAP**, el sistema de navegación del campus de la Universidad Católica de Oriente. Guarda los datos
del campus en MongoDB y los sirve al panel de administración y a la app de los estudiantes.

> ¿Primera vez en el proyecto? Lee primero [docs/ECOSISTEMA.md](docs/ECOSISTEMA.md) y
> [docs/GUIA-ESTUDIANTES.md](docs/GUIA-ESTUDIANTES.md). Si usas un asistente de IA, apúntalo a [AGENTS.md](AGENTS.md).

## 1. Objetivo y responsabilidad

- Persistir y exponer edificios, salones, el grafo de caminos exteriores, puntos de interés, eventos y parámetros.
- Autenticar a los administradores (JWT) y proteger las operaciones de escritura.
- Recibir la telemetría de la app (sesiones y recorridos) y calcular la analítica del panel.
- Guardar el *navmesh* interior que genera el admin y entregarlo a la app.
- Firmar los tokens del **VPS propio** (`vps-server`) para la app y el panel. No le reenvía fotos: los clientes lo
  llaman directo.
- Enviar el correo de restablecer contraseña (SMTP).

| Cliente | Repo | Cómo usa este backend |
|---|---|---|
| Panel admin | `uco-map-admin` | Todas las rutas, con `Authorization: Bearer <jwt>` |
| App | `uco-map-app` | Solo rutas públicas (lectura del campus + telemetría + token VPS) |

## 2. Tecnologías

| Herramienta | Versión (verificada en `pom.xml` / wrapper) |
|---|---|
| Java (JDK) | 21 |
| Spring Boot | 3.5.14 (web, data-mongodb, security, validation, mail, thymeleaf, devtools) |
| Maven | 3.9.15 vía `./mvnw` (no hace falta instalarlo) |
| JWT | jjwt 0.12.6 |
| Lombok | gestionado por Spring Boot |
| Base de datos | MongoDB (Atlas en producción; `mongo:7` en Docker para local) |
| Pruebas | JUnit 5, Mockito, AssertJ, MockMvc; k6 para carga |
| Contenedor | `Dockerfile` multi-stage con `eclipse-temurin:21` |

## 3. Requisitos previos

- **JDK 21** (`java -version` debe mostrar 21). Verificado con OpenJDK 21.0.6.
- **Docker** (para MongoDB local y, opcionalmente, la imagen del backend).
- **Git**. Internet la primera vez (el wrapper descarga Maven y las dependencias).
- Opcional: IntelliJ IDEA con el plugin de Lombok.

## 4. Clonar y configurar

```bash
git clone https://github.com/KronoIV/ms-ucoMap-backend.git backend
cd backend
cp .env.example .env        # y edita los valores (ver sección 6)
```

Se recomienda clonar los repos como carpetas hermanas (`backend/`, `uco-map-admin/`, `uco-map-app/` y, si se usa el
VPS propio, `vps-server/`).

## 5. Instalar dependencias

```bash
./mvnw -DskipTests package   # descarga dependencias y compila (Windows: mvnw.cmd)
```

## 6. Variables de entorno

`src/main/resources/application.properties` **no contiene secretos**: los lee de variables de entorno. En local,
Spring carga automáticamente `backend/.env` si existe (`spring.config.import=optional:file:.env[.properties]`; hay que
arrancar desde la carpeta `backend/`). Formato `CLAVE=valor`, sin comillas. `.env` y `.env.*` están en `.gitignore`.
Si exportas la variable en la terminal o la defines en el IDE, esa tiene prioridad sobre el archivo.

| Variable | Obligatoria | Para qué | Cómo obtenerla |
|---|---|---|---|
| `MONGODB_URI` | **Sí** | Conexión a MongoDB | Local: `mongodb://localhost:27017/ucomap`. Producción: la entrega el responsable (Atlas). |
| `JWT_SECRET` | **Sí** (≥ 32 caracteres) | Firma de los JWT del panel | Local: cualquier texto largo (`openssl rand -base64 48`). Producción: responsable. |
| `MAIL_USERNAME` | **Sí** | Remitente SMTP (Gmail) | Local: un valor ficticio basta para arrancar. Real: responsable. |
| `MAIL_PASSWORD` | **Sí** | Contraseña de aplicación de Gmail | Ídem. Sin la real, «olvidé mi contraseña» falla al enviar. |
| `VPS_BASE_URL` | No | URL pública del VPS propio que reciben la app y el panel | Responsable / infraestructura. Vacía: `/api/vps/*` responde 503. |
| `VPS_TOKEN_SECRET` | No (≥ 32 caracteres si se define) | Firma de los tokens del VPS propio | El mismo par `kid:secreto` debe estar en `VPS_TOKEN_SECRETS` del VPS. Vacía: 503. |
| `VPS_TOKEN_KID` | No (`k1`) | Identificador del secreto (permite rotarlo) | Debe coincidir con el VPS. |
| `VPS_MAP_SET_ID` | No (`uco`) | Map set del campus en el VPS propio | Ajuste propio. |
| `VPS_TOKEN_TTL_SECONDS` / `VPS_ADMIN_TOKEN_TTL_SECONDS` | No (600 / 1800) | Vigencia de los tokens de la app y del panel | Ajuste propio. |
| `RATE_LIMIT_MAX` / `RATE_LIMIT_AUTH_MAX` / `RATE_LIMIT_WINDOW` | No | Peticiones por IP (600 / 10 por 60 s) | Ajuste propio. |
| `RATE_LIMIT_TRUSTED_PROXIES` | No | IPs/CIDR cuyo `X-Forwarded-For` se acepta | Por defecto redes privadas y loopback. |
| `LOG_LEVEL` | No | Nivel de log de `co.edu.uco.ucomap` | `INFO` por defecto. |

Variables que **debe proporcionar el responsable o infraestructura**: `MONGODB_URI` de producción, `JWT_SECRET` de
producción, `MAIL_USERNAME`/`MAIL_PASSWORD` y `VPS_TOKEN_SECRET`. Nunca las subas al
repositorio.

Otros valores fijos en `application.properties` (sin variable): puerto `8080`, `app.data.init-on-startup=true`,
`app.cors.allowed-origins=*`, expiración JWT 24 h, SMTP `smtp.gmail.com:587`, enlace de restablecer contraseña
`https://uco-map.netlify.app/reset-password`. Para cambiarlos en local sin editar el archivo usa argumentos:
`./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.password-reset.frontend-url=http://localhost:5173`.

## 7. Ejecutar en desarrollo

```bash
# 1) MongoDB local (una sola vez; luego: docker start ucomap-mongo)
docker run -d --name ucomap-mongo -p 27017:27017 mongo:7

# 2) Backend (desde la carpeta backend/, con .env configurado)
./mvnw spring-boot:run
```

- API en `http://localhost:8080`. Comprueba: `curl http://localhost:8080/api/settings`.
- Con una base vacía, el arranque **siembra datos de ejemplo** (20 nodos, 20 aristas, 4 edificios, 21 salones),
  dos parámetros y el usuario **`admin@admin.com`** cuya contraseña está en
  `src/main/java/co/edu/uco/ucomap/service/AdminInitializer.java`. Úsalo para entrar al panel admin local.
- Datos reales: pide al responsable un volcado de MongoDB. **No uses la URI de producción en local.**

## 8. Compilar / generar build

```bash
./mvnw package                     # con pruebas → target/backend-0.0.1-SNAPSHOT.jar
java -jar target/backend-0.0.1-SNAPSHOT.jar   # las variables deben estar en el entorno o en ./.env

docker build -t ucomap-backend .   # imagen; ejecuta las pruebas durante el build
docker run --rm -p 8080:8080 --env-file .env ucomap-backend
```

`docker-compose.yml` levanta solo el backend y toma las variables del entorno del shell (su comentario menciona
`infisical run -- docker-compose up`). Con Mongo en tu máquina usa `host.docker.internal` en vez de `localhost`
en `MONGODB_URI`.

**Producción:** Railway (`https://ucomap-backend.up.railway.app`). Antes de desplegar, el servicio debe tener definidas
todas las variables obligatorias; si falta una, el arranque falla con
`Could not resolve placeholder 'JWT_SECRET'` (o la que falte). Detalles del despliegue: preguntar al responsable.

## 9. Pruebas

```bash
./mvnw test        # 199 pruebas; no necesitan MongoDB ni Internet (después de la primera descarga)
```

Usan `src/test/resources/application.properties` (secretos ficticios, sin siembra). Pruebas de carga con k6 en
`load-tests/` (entorno Docker aislado): ver `.skills/testing/SKILL.md`.

## 10. Estructura de carpetas

```
backend/
├── src/main/java/co/edu/uco/ucomap/
│   ├── BackendApplication.java   Punto de entrada (@EnableScheduling)
│   ├── controller/               Endpoints REST (/api/**)
│   ├── service/                  Lógica, inicializadores al arrancar y tareas programadas
│   ├── repository/               Interfaces MongoRepository
│   ├── model/                    Documentos MongoDB, enums y objetos embebidos
│   ├── dto/                      Objetos de entrada/salida
│   ├── security/                 SecurityConfig, filtros JWT y rate limit, JwtUtil
│   └── common/                   CORS, índices, sobre de respuesta, errores, logging, IP del cliente
├── src/main/resources/
│   ├── application.properties    Configuración (secretos por variables de entorno)
│   └── templates/email/          Plantilla Thymeleaf del correo
├── src/test/                     Pruebas y su application.properties
├── load-tests/                   Pruebas de carga (Docker + k6)
├── docs/                         ECOSISTEMA.md y GUIA-ESTUDIANTES.md (compartidos con admin y app)
├── .skills/                      Contexto para asistentes de IA (ver AGENTS.md)
├── .env.example                  Plantilla de variables
├── Dockerfile, docker-compose.yml
└── pom.xml, mvnw, mvnw.cmd, .mvn/
```

## 11. Endpoints

Todas las respuestas van en `{ "data": ..., "succeeded": true }`; los errores en
`{ "error": "...", "succeeded": false, "details"?: {...} }`. La tabla completa (método, ruta, acceso) está en
[.skills/api-development/SKILL.md](.skills/api-development/SKILL.md). Resumen:

| Grupo | Rutas | Acceso |
|---|---|---|
| Autenticación | `/api/auth/login`, `/forgot-password`, `/reset-password` | Público |
| Campus | `/api/campus`, `/api/buildings`, `/api/rooms`, `/api/poi-clips`, `/api/graph/*` | Lectura pública, escritura ADMIN |
| Configuración | `/api/settings` | Lectura pública, escritura ADMIN |
| Eventos | `/api/events/active` (público), `/api/events` | ADMIN |
| Navegación interior | `/api/navigation/navmesh` y `/elevators` (lectura pública); conexiones, parches, ediciones, bloqueos | ADMIN |
| Telemetría | `POST /api/sessions/ping`, `POST /api/trips`, `GET /api/trips/popular` | Público |
| Analítica | `/api/sessions`, `/api/trips`, `/api/analytics` | ADMIN |
| VPS propio | `POST /api/vps/token` (público, alcance `localize`), `POST /api/vps/admin-token` (alcance `admin`) | ADMIN |
| Usuarios | `/api/users` | ADMIN |

## 12. Conexión con Admin y App

- **Admin** apunta aquí con `VITE_BACKEND_URL`. El enlace del correo de contraseña usa
  `app.password-reset.frontend-url` (la URL del admin).
- **App** apunta aquí con `VITE_API_BASE` (build) o `BACKEND_URL` (proxy de desarrollo).
- CORS permite cualquier origen, así que admin (`:5173`) y app (`:5174`) locales funcionan sin cambios.
- **VPS propio:** el backend no lo llama; solo firma tokens con el secreto compartido. Arranque local y variables
  del VPS en `vps-server/README.md`.
- Si cambias un contrato, actualiza `uco-map-admin/src/services` + `src/types` y `uco-map-app/src/services`.

## 13. Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` (u otra variable) | Falta la variable. Crea `.env` desde `.env.example` y arranca **desde la carpeta `backend/`**, o exporta la variable. |
| `JWT_SECRET must be at least 32 characters` | Usa un secreto más largo. |
| `Timed out after 30000 ms while waiting to connect` (Mongo) | MongoDB no está corriendo: `docker start ucomap-mongo`. Revisa `MONGODB_URI`. |
| `Port 8080 was already in use` | Otro proceso usa 8080 (`lsof -i :8080`). El puerto está fijo en `application.properties`; libera el puerto o usa `-Dspring-boot.run.arguments=--server.port=8081` y apunta los frontends a ese puerto. |
| `POST /api/vps/token` → 503 | Faltan `VPS_BASE_URL` o `VPS_TOKEN_SECRET` (normal si no usas el VPS propio). |
| El VPS propio responde 401 a los tokens | `VPS_TOKEN_KID`/`VPS_TOKEN_SECRET` no coinciden con `VPS_TOKEN_SECRETS` del VPS. |
| `VPS_TOKEN_SECRET must be at least 32 characters` | Usa un secreto más largo o deja la variable vacía. |
| «Olvidé mi contraseña» no llega | `MAIL_*` ficticias o contraseña de aplicación inválida. |
| 401 en endpoints del panel | Falta `Authorization: Bearer`, token vencido (24 h) o emitido antes de un cambio de contraseña. |
| 429 Too Many Requests | Rate limit por IP; en local sube `RATE_LIMIT_MAX`/`RATE_LIMIT_AUTH_MAX`. |
| `docker build` falla en `mvnw package` | Una prueba falla; corre `./mvnw test` y corrígela. |
| `./mvnw: Permission denied` | `chmod +x mvnw`. |
