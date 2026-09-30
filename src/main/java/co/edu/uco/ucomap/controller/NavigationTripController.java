package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.TripFilter;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.dto.TripSummaryDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.service.NavigationTripService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Recorridos de navegación (métricas de evaluación: tiempo de llegada y tasa de éxito).
 *
 * POST /api/trips          — público: la app reporta inicio y fin del recorrido
 * GET  /api/trips          — ADMIN: página de recorridos (más recientes primero)
 * GET  /api/trips/summary  — ADMIN: métricas del rango filtrado
 * GET  /api/trips/export   — ADMIN: CSV con todos los recorridos del rango
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class NavigationTripController {

    private final NavigationTripService tripService;

    @PostMapping
    public ResponseEntity<ApiSuccess<Void>> report(@Valid @RequestBody TripReportDTO body,
                                                   HttpServletRequest request) {
        tripService.report(body, request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    @GetMapping
    public ResponseEntity<ApiSuccess<PageResponse<NavigationTrip>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String building) {
        return ResponseEntity.ok(ApiSuccess.of(tripService.findPage(new TripFilter(from, to, building), page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiSuccess<TripSummaryDTO>> summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String building) {
        return ResponseEntity.ok(ApiSuccess.of(tripService.summarize(new TripFilter(from, to, building))));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String building) {
        // BOM para que Excel reconozca UTF-8 (tildes)
        byte[] csv = ("\uFEFF" + tripService.exportCsv(new TripFilter(from, to, building)))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("recorridos-ucomap.csv").build().toString())
                .body(csv);
    }
}
