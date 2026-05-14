package com.ucomap.backend.dto;

import java.util.Map;

/**
 * Estadisticas globales de dispositivos conectados.
 * GET /api/sessions/stats
 */
public record StatsDTO(
        long totalDevices,
        long totalSessions,
        Map<String, Long> byPlatform
) {}

