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

    public Building save(Building building) {
        return buildingRepository.save(building);
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
        clip.setActive(false);
        poiClipRepository.save(clip);
    }
}

