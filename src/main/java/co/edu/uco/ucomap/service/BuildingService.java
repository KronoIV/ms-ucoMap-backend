package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.GpsPoint;
import co.edu.uco.ucomap.model.MapConfig;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.model.PoiClip;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingService {

    private static final String CONFIG_ID = "campus_config";

    private final BuildingRepository  buildingRepository;
    private final MapConfigRepository mapConfigRepository;
    private final PoiClipRepository   poiClipRepository;
    private final GraphNodeRepository nodeRepository;
    private final BuildingNodeSync    buildingSync;

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
        requireLocation(building.getGps());

        // Uno borrado desde el mapa (oculto) se puede volver a crear con el mismo ID
        Building previous = buildingRepository.findById(buildingId).orElse(null);
        if (previous != null && previous.isActive()) {
            log.warn("Edificio duplicado — buildingId={}", buildingId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        if (categoryTaken(category, buildingId)) {
            log.warn("Categoría de edificio duplicada — category={}", category);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        // El ID también nombra su punto en el mapa: no puede ser el de otro punto que no sea edificio
        nodeRepository.findById(buildingId)
                .filter(n -> n.isActive() && n.getNodeType() != NodeType.BUILDING)
                .ifPresent(n -> {
                    log.warn("El ID del edificio ya es otro punto del mapa — nodeId={} type={}", buildingId, n.getNodeType());
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            ErrorCode.CONFLICT.getMessage());
                });

        building.setBuildingId(buildingId);
        building.setCategory(category);
        building.setNodeId(previous != null ? previous.getNodeId() : null);
        building.setActive(true);
        Building saved = buildingRepository.save(building);
        buildingSync.buildingSaved(saved, true);
        log.info("Edificio creado — buildingId={} category={}", buildingId, category);
        return saved;
    }

    public Building update(String buildingId, Building updated) {
        Building existing = findById(buildingId);

        String nextCategory = requireText(updated.getCategory(), "category");
        if (!nextCategory.equalsIgnoreCase(existing.getCategory()) && categoryTaken(nextCategory, buildingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        if (updated.getGps() != null) {
            requireLocation(updated.getGps());
            existing.setGps(updated.getGps());
        }

        existing.setLabel(updated.getLabel());
        existing.setColor(updated.getColor());
        existing.setCategory(nextCategory);
        existing.setActive(updated.isActive());
        Building saved = buildingRepository.save(existing);
        buildingSync.buildingSaved(saved, false);
        log.info("Edificio actualizado — buildingId={}", buildingId);
        return saved;
    }

    public void delete(String buildingId) {
        Building building = findById(buildingId);
        buildingRepository.delete(building);
        buildingSync.buildingDeleted(building);
        log.info("Edificio eliminado — buildingId={}", buildingId);
    }

    private boolean categoryTaken(String category, String exceptBuildingId) {
        return buildingRepository.findByCategoryIgnoreCase(category).stream()
                .anyMatch(b -> !b.getBuildingId().equals(exceptBuildingId));
    }

    /** Sin coordenadas el edificio no tendría punto en el mapa (0,0 es el valor vacío del formulario). */
    private void requireLocation(GpsPoint gps) {
        if (gps == null || (gps.getLat() == 0 && gps.getLng() == 0)
                || Math.abs(gps.getLat()) > 90 || Math.abs(gps.getLng()) > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.getMessage());
        }
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

