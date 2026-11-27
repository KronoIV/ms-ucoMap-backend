# AGENTS.md — Backend UCO MAP

Instrucciones para asistentes de IA (Claude Code, Cursor, Copilot, ChatGPT, etc.) que trabajen en este repositorio.

## Contexto obligatorio

Antes de editar, lee la Skill que corresponda en `.skills/` (cada una es un `SKILL.md` con metadatos `name` y
`description`):

| Skill | Cuándo |
|---|---|
| [.skills/project-context](.skills/project-context/SKILL.md) | Siempre: qué es, comandos, configuración, qué no tocar |
| [.skills/ecosystem](.skills/ecosystem/SKILL.md) | Cambios que afecten al admin o a la app (contratos, auth, navmesh) |
| [.skills/architecture](.skills/architecture/SKILL.md) | Ubicar código, capas, convenciones |
| [.skills/database](.skills/database/SKILL.md) | Modelos, colecciones, índices, siembra |
| [.skills/api-development](.skills/api-development/SKILL.md) | Endpoints, DTOs, errores; tabla completa de rutas |
| [.skills/security](.skills/security/SKILL.md) | JWT, SecurityConfig, rate limit, CORS, secretos |
| [.skills/testing](.skills/testing/SKILL.md) | Pruebas unitarias, de seguridad y de carga |

Documentación humana: `README.md`, `docs/ECOSISTEMA.md`, `docs/GUIA-ESTUDIANTES.md`.

## Reglas

1. Nunca escribas secretos en archivos versionados, pruebas, logs o respuestas. Usa `${VAR}` en
   `application.properties` y documenta la variable en `.env.example` y el README. No leas ni muestres `.env` ni
   `.env.railway`.
2. Mantén el sobre `ApiSuccess` / `ApiError` y la compatibilidad de contratos con `uco-map-admin` y `uco-map-app`.
3. Todo endpoint nuevo necesita su regla en `SecurityConfig` (público o ADMIN) y pruebas.
4. No apuntes a la base de producción ni llames a servicios externos en pruebas.
5. Comentarios, mensajes de error y logs en español; identificadores en inglés.
6. Verifica con `./mvnw test` antes de terminar.
