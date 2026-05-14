package com.ucomap.backend.dto;

import com.ucomap.backend.model.MapConfig;
import com.ucomap.backend.model.Room;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Payload único que el frontend consume al inicializar la app.
 * GET /api/campus → devuelve este objeto.
 *
 * Las estructuras usan las mismas claves que el frontend espera,
 * para minimizar cambios en el código TypeScript.
 */
@Data
@Builder
public class CampusDataDTO {

    /**
     * Lista de salones activos.
     * Reemplaza: const rooms = [...] en index.ts
     */
    private List<Room> rooms;

    /**
     * Mapa clipId → displayName.
     * Reemplaza: const POI_CLIP_NAMES = {...} en index.ts
     */
    private Map<String, String> poiClipNames;

    /**
     * Mapa nodeId → GraphNodeDTO.
     * Reemplaza: const GRAPH_NODES = {...} en outdoor-map.ts
     */
    private Map<String, GraphNodeDTO> graphNodes;

    /**
     * Lista de pares [nodeA, nodeB].
     * Reemplaza: const GRAPH_EDGES = [...] en outdoor-map.ts
     */
    private List<List<String>> graphEdges;

    /**
     * Mapa buildingId → BuildingDTO.
     * Reemplaza: const BLOCKS = {...} en outdoor-map.ts
     */
    private Map<String, BuildingDTO> blocks;

    /**
     * Mapa category → {lat, lng}.
     * Reemplaza: const BLOCK_GPS = {...} en index.ts
     */
    private Map<String, Object> blockGps;

    /**
     * Límites del mapa y umbrales de distancia.
     * Reemplaza: const MAP_BOUNDS, ARRIVAL_THRESHOLD_METERS, OUTDOOR_THRESHOLD_METERS
     */
    private MapConfig mapConfig;

    // ── DTOs anidados ────────────────────────────────────────────

    @Data
    @Builder
    public static class GraphNodeDTO {
        private String   id;
        private GpsDTO   gps;
        private PixelDTO pixel;
        private String   label;
        private String   nodeType;
    }

    @Data
    @Builder
    public static class BuildingDTO {
        private GpsDTO   gps;
        private PixelDTO pixel;
        private String   label;
        private String   color;
        private String   category;
    }

    @Data
    @Builder
    public static class GpsDTO {
        private double lat;
        private double lng;
    }

    @Data
    @Builder
    public static class PixelDTO {
        private int x;
        private int y;
    }
}

