package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.MapConfig;
import co.edu.uco.ucomap.model.PoiClip;
import co.edu.uco.ucomap.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestión de edificios, configuración del mapa y POI clips.
 *
 * GET    /api/buildings              — todos los edificios activos
 * GET    /api/buildings/{id}         — edificio específico
 * POST   /api/buildings              — crear edificio
 * PUT    /api/buildings/{id}         — actualizar edificio
 * DELETE /api/buildings/{id}         — eliminar edificio (físico)
 *
 * GET    /api/map/config             — configuración del mapa (bounds, umbrales)
 * PUT    /api/map/config             — actualizar configuración
 *
 * GET    /api/poi-clips              — todos los clips activos
 * GET    /api/poi-clips/map          — mapa clipId → displayName
 * POST   /api/poi-clips              — crear clip
 * DELETE /api/poi-clips/{clipId}     — eliminar clip (físico)
 */
@RestController
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // ── Buildings ──────────────────────────────────────────────

    @GetMapping("/api/buildings")
    public ResponseEntity<ApiSuccess<List<Building>>> getBuildings() {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.findAll()));
    }

    @GetMapping("/api/buildings/{buildingId}")
    public ResponseEntity<ApiSuccess<Building>> getBuilding(@PathVariable String buildingId) {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.findById(buildingId)));
    }

    @PostMapping("/api/buildings")
    public ResponseEntity<ApiSuccess<Building>> createBuilding(@Valid @RequestBody Building building) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(buildingService.create(building)));
    }

    @PutMapping("/api/buildings/{buildingId}")
    public ResponseEntity<ApiSuccess<Building>> updateBuilding(
            @PathVariable String buildingId,
            @Valid @RequestBody Building building) {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.update(buildingId, building)));
    }

    @DeleteMapping("/api/buildings/{buildingId}")
    public ResponseEntity<ApiSuccess<Void>> deleteBuilding(@PathVariable String buildingId) {
        buildingService.delete(buildingId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    // ── Map Config ─────────────────────────────────────────────

    @GetMapping("/api/map/config")
    public ResponseEntity<ApiSuccess<MapConfig>> getMapConfig() {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.getMapConfig()));
    }

    @PutMapping("/api/map/config")
    public ResponseEntity<ApiSuccess<MapConfig>> updateMapConfig(@Valid @RequestBody MapConfig config) {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.saveMapConfig(config)));
    }

    // ── POI Clips ──────────────────────────────────────────────

    @GetMapping("/api/poi-clips")
    public ResponseEntity<ApiSuccess<List<PoiClip>>> getClips() {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.findAllClips()));
    }

    @GetMapping("/api/poi-clips/map")
    public ResponseEntity<ApiSuccess<Map<String, String>>> getClipsMap() {
        return ResponseEntity.ok(ApiSuccess.of(buildingService.findClipsAsMap()));
    }

    @PostMapping("/api/poi-clips")
    public ResponseEntity<ApiSuccess<PoiClip>> createClip(@Valid @RequestBody PoiClip clip) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(buildingService.saveClip(clip)));
    }

    @DeleteMapping("/api/poi-clips/{clipId}")
    public ResponseEntity<ApiSuccess<Void>> deleteClip(@PathVariable String clipId) {
        buildingService.deleteClip(clipId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}

