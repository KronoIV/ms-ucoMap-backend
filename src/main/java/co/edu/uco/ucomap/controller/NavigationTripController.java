package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.service.NavigationTripService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Recorridos de navegación (métricas de evaluación: tiempo de llegada y tasa de éxito).
 *
 * POST /api/trips  — público: la app reporta inicio y fin del recorrido
 * GET  /api/trips  — ADMIN: lista de recorridos
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
    public ResponseEntity<ApiSuccess<List<NavigationTrip>>> findAll() {
        return ResponseEntity.ok(ApiSuccess.of(tripService.findAll()));
    }
}
