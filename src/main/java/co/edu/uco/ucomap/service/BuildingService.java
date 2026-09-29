package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.MapConfig;
import co.edu.uco.ucomap.model.PoiClip;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.repository.MapConfigRepository;
import co.edu.uco.ucomap.repository.PoiClipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.edu.uco.ucomap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
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
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    public Building create(Building building) {
        String buildingId = requireText(building.getBuildingId(), "buildingId");
        String category = requireText(building.getCategory(), "category");

        if (buildingRepository.existsById(buildingId)) {
            log.warn("Edificio duplicado — buildingId={}", buildingId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        if (buildingRepository.existsByCategoryIgnoreCase(category)) {
            log.warn("Categoría de edificio duplicada — category={}", category);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }

        building.setBuildingId(buildingId);
        building.setCategory(category);
        building.setActive(true);
        Building saved = buildingRepository.save(building);
        log.info("Edificio creado — buildingId={} category={}", buildingId, category);
        return saved;
    }

    public Building update(String buildingId, Building updated) {
        Building existing = findById(buildingId);

        String nextCategory = requireText(updated.getCategory(), "category");
        if (!Objects.equals(existing.getCategory(), nextCategory)
                && buildingRepository.existsByCategoryIgnoreCase(nextCategory)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }

        existing.setLabel(updated.getLabel());
        existing.setColor(updated.getColor());
        existing.setCategory(nextCategory);
        existing.setGps(updated.getGps());
        existing.setActive(updated.isActive());
        Building saved = buildingRepository.save(existing);
        log.info("Edificio actualizado — buildingId={}", buildingId);
        return saved;
    }

    public void delete(String buildingId) {
        Building building = findById(buildingId);
        buildingRepository.delete(building);
        log.info("Edificio eliminado — buildingId={}", buildingId);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.getMessage());
        }
        return value.trim();
    }

    // ── Map Config ─────────────────────────────────────────────

    public MapConfig getMapConfig() {
        return mapConfigRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    public MapConfig saveMapConfig(MapConfig config) {
        config.setId(CONFIG_ID);
        MapConfig saved = mapConfigRepository.save(config);
        log.info("Configuración del mapa actualizada");
        return saved;
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
        PoiClip saved = poiClipRepository.save(clip);
        log.info("POI clip guardado — clipId={}", saved.getClipId());
        return saved;
    }

    public void deleteClip(String clipId) {
        PoiClip clip = poiClipRepository.findById(clipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        poiClipRepository.delete(clip);
        log.info("POI clip eliminado — clipId={}", clipId);
    }
}

