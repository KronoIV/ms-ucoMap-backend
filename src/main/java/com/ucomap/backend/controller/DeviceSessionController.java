package com.ucomap.backend.controller;

import com.ucomap.backend.dto.StatsDTO;
import com.ucomap.backend.model.DeviceSession;
import com.ucomap.backend.service.DeviceSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
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

    private static final String COOKIE_NAME = "UCOMAP_DEVICE_ID";
    private static final int    COOKIE_MAX_AGE = 60 * 60 * 24 * 365 * 10; // 10 años

    private final DeviceSessionService sessionService;

    /**
     * El cliente solo necesita llamar este endpoint — sin body, sin configuracion.
     * El servidor:
     *  1. Lee la cookie UCOMAP_DEVICE_ID; si no existe, genera un UUID nuevo y la setea.
     *  2. Extrae User-Agent, IP y Accept-Language de los headers automaticamente.
     *  3. Crea o actualiza el registro en MongoDB.
     */
    @PostMapping("/ping")
    public ResponseEntity<DeviceSession> ping(
            HttpServletRequest request,
            HttpServletResponse response) {

        // ── 1. Resolver deviceId desde cookie ─────────────────
        String deviceId = readCookie(request);
        boolean isNew   = (deviceId == null);
        if (isNew) {
            deviceId = UUID.randomUUID().toString();
        }

        // ── 2. Refrescar/crear la cookie (siempre renovar TTL) ─
        Cookie cookie = new Cookie(COOKIE_NAME, deviceId);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setPath("/");
        cookie.setHttpOnly(true);   // no accesible desde JS del cliente
        response.addCookie(cookie);

        // ── 3. Extraer metadata de los headers HTTP ───────────
        String userAgent = header(request, "User-Agent");
        String ip        = extractIp(request);
        String language  = extractLanguage(request);

        // ── 4. Registrar en MongoDB ───────────────────────────
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

    // ── Helpers ───────────────────────────────────────────────

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

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
