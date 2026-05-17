package com.ucomap.backend.controller;

import com.ucomap.backend.dto.PingRequestDTO;
import com.ucomap.backend.dto.StatsDTO;
import com.ucomap.backend.model.DeviceSession;
import com.ucomap.backend.service.DeviceSessionService;
import com.ucomap.backend.service.SessionEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Tracking de dispositivos — 100% transparente para el cliente.
 *
 * El cliente NO envia body ni genera IDs.
 * Todo se extrae automaticamente de los headers HTTP.
 * El deviceId se gestiona como cookie HttpOnly en el servidor.
 *
 * POST /api/sessions/ping   — registrar/actualizar sesion (sin body)
 * GET  /api/sessions/stats  — estadisticas globales
 * GET  /api/sessions        — lista de dispositivos
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class DeviceSessionController {

    private final DeviceSessionService  sessionService;
    private final SessionEventPublisher eventPublisher;

    /**
     * El cliente envia su deviceId (UUID persistido en localStorage).
     * Si no lo envía (fallback), el servidor genera uno.
     * User-Agent, IP y Accept-Language se extraen de los headers HTTP.
     */
    @PostMapping("/ping")
    public ResponseEntity<DeviceSession> ping(
            @RequestBody(required = false) PingRequestDTO body,
            HttpServletRequest request) {

        // Leer deviceId del body; si no viene, generar uno (fallback)
        String deviceId = (body != null
                && body.deviceId() != null
                && !body.deviceId().isBlank())
                ? body.deviceId()
                : UUID.randomUUID().toString();

        String userAgent = header(request, "User-Agent");
        String ip        = extractIp(request);
        String language  = extractLanguage(request);

        DeviceSession session = sessionService.registerPing(deviceId, userAgent, ip, language);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsDTO> getStats() {
        return ResponseEntity.ok(sessionService.getStats());
    }

    @GetMapping
    public ResponseEntity<List<DeviceSession>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }

    /**
     * SSE — el frontend se suscribe aqui y recibe actualizaciones en tiempo real.
     * Emite dos tipos de eventos:
     *   "session" → DeviceSession (el dispositivo que acaba de hacer ping)
     *   "stats"   → StatsDTO (estadisticas globales actualizadas)
     *
     * Uso en frontend:
     *   const es = new EventSource('http://localhost:8080/api/sessions/stream');
     *   es.addEventListener('session', e => console.log(JSON.parse(e.data)));
     *   es.addEventListener('stats',   e => console.log(JSON.parse(e.data)));
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return eventPublisher.subscribe();
    }

    // ── Helpers ───────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Extrae el idioma principal del header Accept-Language (ej. "es-CO"). */
    private String extractLanguage(HttpServletRequest request) {
        String acceptLang = request.getHeader("Accept-Language");
        if (acceptLang == null || acceptLang.isBlank()) return null;
        // "es-CO,es;q=0.9,en;q=0.8" → "es-CO"
        return acceptLang.split("[,;]")[0].trim();
    }

    private String header(HttpServletRequest request, String name) {
        String val = request.getHeader(name);
        return (val != null && !val.isBlank()) ? val : "Unknown";
    }
}
