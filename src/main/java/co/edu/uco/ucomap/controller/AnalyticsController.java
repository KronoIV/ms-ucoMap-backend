package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO;
import co.edu.uco.ucomap.dto.TripAnalyticsDTO;
import co.edu.uco.ucomap.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Analítica del panel (solo ADMIN).
 *
 * GET /api/analytics        — usuarios, visitas, tiempo de uso, dispositivos, funciones, permisos y problemas
 * GET /api/analytics/trips  — rutas, orígenes, destinos, abandonos y mapa de actividad
 *
 * from/to: ISO-8601 (por defecto, los últimos 30 días); tz: zona para agrupar por día y hora.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<ApiSuccess<AnalyticsOverviewDTO>> overview(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String tz,
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(ApiSuccess.of(
                analyticsService.overview(AnalyticsService.range(from, to, tz, platform, null))));
    }

    @GetMapping("/trips")
    public ResponseEntity<ApiSuccess<TripAnalyticsDTO>> trips(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String tz,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String building) {
        return ResponseEntity.ok(ApiSuccess.of(
                analyticsService.trips(AnalyticsService.range(from, to, tz, platform, building))));
    }
}
