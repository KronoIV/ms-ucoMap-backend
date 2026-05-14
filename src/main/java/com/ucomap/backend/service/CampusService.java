package com.ucomap.backend.service;

import com.ucomap.backend.dto.CampusDataDTO;
import com.ucomap.backend.dto.CampusDataDTO.*;
import com.ucomap.backend.model.Building;
import com.ucomap.backend.model.GraphNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrega todos los datos del campus en un único DTO.
 * El frontend hace un solo GET /api/campus al inicializar.
 */
@Service
@RequiredArgsConstructor
public class CampusService {

    private final RoomService     roomService;
    private final GraphService    graphService;
    private final BuildingService buildingService;

    public CampusDataDTO getCampusData() {
        // Convertir GraphNodes al DTO esperado por el frontend
        Map<String, GraphNodeDTO> graphNodeDTOs = graphService.findAllNodesAsMap()
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> toGraphNodeDTO(e.getValue())
                ));

        // Convertir Buildings al DTO (BLOCKS en el frontend)
        Map<String, BuildingDTO> blockDTOs = buildingService.findAllAsMap()
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> toBuildingDTO(e.getValue())
                ));

        return CampusDataDTO.builder()
                .rooms(roomService.findAll())
                .poiClipNames(buildingService.findClipsAsMap())
                .graphNodes(graphNodeDTOs)
                .graphEdges(graphService.findAllEdgesAsPairs())
                .blocks(blockDTOs)
                .blockGps(buildingService.findBlockGpsMap())
                .mapConfig(buildingService.getMapConfig())
                .build();
    }

    private GraphNodeDTO toGraphNodeDTO(GraphNode node) {
        return GraphNodeDTO.builder()
                .id(node.getNodeId())
                .gps(GpsDTO.builder()
                        .lat(node.getGps().getLat())
                        .lng(node.getGps().getLng())
                        .build())
                .pixel(node.getPixel() != null
                        ? PixelDTO.builder()
                                .x(node.getPixel().getX())
                                .y(node.getPixel().getY())
                                .build()
                        : null)
                .label(node.getLabel())
                .nodeType(node.getNodeType() != null ? node.getNodeType().name() : null)
                .build();
    }

    private BuildingDTO toBuildingDTO(Building b) {
        return BuildingDTO.builder()
                .gps(GpsDTO.builder()
                        .lat(b.getGps().getLat())
                        .lng(b.getGps().getLng())
                        .build())
                .pixel(null)
                .label(b.getLabel())
                .color(b.getColor())
                .category(b.getCategory())
                .build();
    }
}

