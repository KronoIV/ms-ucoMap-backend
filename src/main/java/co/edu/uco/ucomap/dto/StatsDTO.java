package co.edu.uco.ucomap.dto;

import java.util.Map;

/**
 * Estadisticas globales de dispositivos conectados.
 * GET /api/sessions/stats
 *
 * activeNow: con ping en los últimos 10 min; activeToday: en las últimas 24 h.
 */
public record StatsDTO(
        long totalDevices,
        long totalSessions,
        Map<String, Long> byPlatform,
        long activeNow,
        long activeToday
) {}

