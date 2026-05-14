package com.ucomap.backend.controller;

import com.ucomap.backend.model.Building;
import com.ucomap.backend.model.MapConfig;
import com.ucomap.backend.model.PoiClip;
import com.ucomap.backend.service.BuildingService;
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
 * PUT    /api/buildings/{id}         — actualizar edificio
 *
 * GET    /api/map/config             — configuración del mapa (bounds, umbrales)
 * PUT    /api/map/config             — actualizar configuración
 *
 * GET    /api/poi-clips              — todos los clips activos
 * GET    /api/poi-clips/map          — mapa clipId → displayName
 * POST   /api/poi-clips              — crear clip
 * DELETE /api/poi-clips/{clipId}     — desactivar clip
 */
@RestController
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // ── Buildings ──────────────────────────────────────────────

    @GetMapping("/api/buildings")
    public ResponseEntity<List<Building>> getBuildings() {
        return ResponseEntity.ok(buildingService.findAll());
    }

    @GetMapping("/api/buildings/{buildingId}")
    public ResponseEntity<Building> getBuilding(@PathVariable String buildingId) {
        return ResponseEntity.ok(buildingService.findById(buildingId));
    }

    @PutMapping("/api/buildings/{buildingId}")
    public ResponseEntity<Building> updateBuilding(
            @PathVariable String buildingId,
            @Valid @RequestBody Building building) {
        building.setBuildingId(buildingId);
        return ResponseEntity.ok(buildingService.save(building));
    }

    // ── Map Config ─────────────────────────────────────────────

    @GetMapping("/api/map/config")
    public ResponseEntity<MapConfig> getMapConfig() {
        return ResponseEntity.ok(buildingService.getMapConfig());
    }

    @PutMapping("/api/map/config")
    public ResponseEntity<MapConfig> updateMapConfig(@Valid @RequestBody MapConfig config) {
        return ResponseEntity.ok(buildingService.saveMapConfig(config));
    }

    // ── POI Clips ──────────────────────────────────────────────

    @GetMapping("/api/poi-clips")
    public ResponseEntity<List<PoiClip>> getClips() {
        return ResponseEntity.ok(buildingService.findAllClips());
    }

    @GetMapping("/api/poi-clips/map")
    public ResponseEntity<Map<String, String>> getClipsMap() {
        return ResponseEntity.ok(buildingService.findClipsAsMap());
    }

    @PostMapping("/api/poi-clips")
    public ResponseEntity<PoiClip> createClip(@Valid @RequestBody PoiClip clip) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildingService.saveClip(clip));
    }

    @DeleteMapping("/api/poi-clips/{clipId}")
    public ResponseEntity<Void> deleteClip(@PathVariable String clipId) {
        buildingService.deleteClip(clipId);
        return ResponseEntity.noContent().build();
    }
}

