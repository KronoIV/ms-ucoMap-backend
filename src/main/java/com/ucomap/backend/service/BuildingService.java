package com.ucomap.backend.service;

import com.ucomap.backend.model.Building;
import com.ucomap.backend.model.MapConfig;
import com.ucomap.backend.model.PoiClip;
import com.ucomap.backend.repository.BuildingRepository;
import com.ucomap.backend.repository.MapConfigRepository;
import com.ucomap.backend.repository.PoiClipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private static final String CONFIG_ID = "campus_config";

    private final BuildingRepository  buildingRepository;
    private final MapConfigRepository mapConfigRepository;
    private final PoiClipRepository   poiClipRepository;

    // ── Buildings ──────────────────────────────────────────────

    public List<Building> findAll() {
        return buildingRepository.findByActiveTrue();
    }

    /** Mapa buildingId → Building (igual que BLOCKS en el frontend). */
    public Map<String, Building> findAllAsMap() {
        return buildingRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(Building::getBuildingId, b -> b));
    }

    /** Mapa category → GPS (igual que BLOCK_GPS en el frontend). */
    public Map<String, Object> findBlockGpsMap() {
        return buildingRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(
                        Building::getCategory,
                        b -> Map.of("lat", b.getGps().getLat(), "lng", b.getGps().getLng())
                ));
    }

    public Building findById(String buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Edificio no encontrado: " + buildingId));
    }

    public Building create(Building building) {
        String buildingId = requireText(building.getBuildingId(), "buildingId");
        String category = requireText(building.getCategory(), "category");

        if (buildingRepository.existsById(buildingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un edificio con buildingId: " + buildingId);
        }
        if (buildingRepository.existsByCategoryIgnoreCase(category)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un edificio con category: " + category);
        }

        building.setBuildingId(buildingId);
        building.setCategory(category);
        building.setActive(true);
        return buildingRepository.save(building);
    }

    public Building update(String buildingId, Building updated) {
        Building existing = findById(buildingId);

        String nextCategory = requireText(updated.getCategory(), "category");
        if (!Objects.equals(existing.getCategory(), nextCategory)
                && buildingRepository.existsByCategoryIgnoreCase(nextCategory)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un edificio con category: " + nextCategory);
        }

        existing.setLabel(updated.getLabel());
        existing.setColor(updated.getColor());
        existing.setCategory(nextCategory);
        existing.setGps(updated.getGps());
        existing.setActive(updated.isActive());
        return buildingRepository.save(existing);
    }

    public void delete(String buildingId) {
        Building building = findById(buildingId);
        buildingRepository.delete(building);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El campo " + fieldName + " es obligatorio");
        }
        return value.trim();
    }

    // ── Map Config ─────────────────────────────────────────────

    public MapConfig getMapConfig() {
        return mapConfigRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Configuración del mapa no encontrada"));
    }

    public MapConfig saveMapConfig(MapConfig config) {
        config.setId(CONFIG_ID);
        return mapConfigRepository.save(config);
    }

    // ── POI Clips ──────────────────────────────────────────────

    public List<PoiClip> findAllClips() {
        return poiClipRepository.findByActiveTrue();
    }

    /** Mapa clipId → displayName (igual que POI_CLIP_NAMES en el frontend). */
    public Map<String, String> findClipsAsMap() {
        return poiClipRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(PoiClip::getClipId, PoiClip::getDisplayName));
    }

    public PoiClip saveClip(PoiClip clip) {
        return poiClipRepository.save(clip);
    }

    public void deleteClip(String clipId) {
        PoiClip clip = poiClipRepository.findById(clipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PoiClip no encontrado: " + clipId));
        poiClipRepository.delete(clip);
    }
}

