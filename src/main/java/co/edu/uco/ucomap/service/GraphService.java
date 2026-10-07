package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.GraphEdge;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.repository.GraphEdgeRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.edu.uco.ucomap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private static final Pattern POI_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,39}");

    private final GraphNodeRepository nodeRepository;
    private final GraphEdgeRepository edgeRepository;
    private final BuildingRepository  buildingRepository;
    private final BuildingNodeSync    buildingSync;

    // ── Nodes ─────────────────────────────────────────────────

    public List<GraphNode> findAllNodes() {
        return nodeRepository.findByActiveTrue();
    }

    /** Devuelve nodos indexados por nodeId (estructura lista para el frontend). */
    public Map<String, GraphNode> findAllNodesAsMap() {
        return nodeRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(GraphNode::getNodeId, n -> n));
    }

    public List<GraphNode> findNodesByType(NodeType type) {
        return nodeRepository.findByNodeType(type);
    }

    public GraphNode findNodeById(String nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    public GraphNode createNode(GraphNode node) {
        if (nodeRepository.existsById(node.getNodeId())) {
            log.warn("Nodo duplicado — nodeId={}", node.getNodeId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        normalizePoi(node);
        GraphNode saved = nodeRepository.save(node);
        buildingSync.nodeSaved(saved, true);
        log.info("Nodo creado — nodeId={} type={}", saved.getNodeId(), saved.getNodeType());
        return saved;
    }

    public GraphNode updateNode(String nodeId, GraphNode updated) {
        GraphNode existing = findNodeById(nodeId);
        // Arrastrar el punto reenvía el nombre: solo un cambio real renombra el edificio
        boolean labelChanged = !Objects.equals(existing.getLabel(), updated.getLabel());
        existing.setGps(updated.getGps());
        existing.setPixel(updated.getPixel());
        existing.setLabel(updated.getLabel());
        existing.setNodeType(updated.getNodeType());
        existing.setPoiType(updated.getPoiType());
        existing.setBuildingId(updated.getBuildingId());
        existing.setFloor(updated.getFloor());
        existing.setActive(updated.isActive());
        normalizePoi(existing);
        GraphNode saved = nodeRepository.save(existing);
        buildingSync.nodeSaved(saved, labelChanged);
        log.info("Nodo actualizado — nodeId={}", nodeId);
        return saved;
    }

    public void deleteNode(String nodeId) {
        GraphNode node = findNodeById(nodeId);
        node.setActive(false);
        nodeRepository.save(node);
        buildingSync.nodeSaved(node, false);
        List<GraphEdge> edges = edgeRepository.findByNodeAOrNodeB(nodeId, nodeId);
        edges.forEach(e -> e.setActive(false));
        edgeRepository.saveAll(edges);
        log.info("Nodo desactivado — nodeId={} aristasDesactivadas={}", nodeId, edges.size());
    }

    /**
     * Solo los POI llevan clase y edificio; la clase se normaliza a una clave ("auditorio" → "AUDITORIO").
     * El piso solo aplica a puertas y a POI dentro de un edificio.
     */
    private void normalizePoi(GraphNode node) {
        if (node.getNodeType() != NodeType.POI) {
            node.setPoiType(null);
            node.setBuildingId(null);
            if (node.getNodeType() != NodeType.DOOR) node.setFloor(null);
            return;
        }
        String type = node.getPoiType() == null ? "" : node.getPoiType().trim().toUpperCase(Locale.ROOT);
        if (!POI_TYPE.matcher(type).matches()) {
            log.warn("Punto de interés sin clase válida — nodeId={} poiType={}", node.getNodeId(), node.getPoiType());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
        node.setPoiType(type);
        String building = node.getBuildingId() == null ? "" : node.getBuildingId().trim();
        if (building.isEmpty()) {
            node.setBuildingId(null);
            node.setFloor(null);
            return;
        }
        if (!buildingRepository.existsById(building)) {
            log.warn("Punto de interés en un edificio inexistente — nodeId={} buildingId={}", node.getNodeId(), building);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
        node.setBuildingId(building);
    }

    // ── Edges ─────────────────────────────────────────────────

    /** Solo aristas cuyos dos extremos siguen activos (evita aristas huérfanas de nodos borrados). */
    public List<GraphEdge> findAllEdges() {
        Set<String> activeIds = nodeRepository.findByActiveTrue().stream()
                .map(GraphNode::getNodeId)
                .collect(Collectors.toSet());
        return edgeRepository.findByActiveTrue().stream()
                .filter(e -> activeIds.contains(e.getNodeA()) && activeIds.contains(e.getNodeB()))
                .toList();
    }

    /** Devuelve aristas como lista de pares [nodeA, nodeB] (formato frontend). */
    public List<List<String>> findAllEdgesAsPairs() {
        return findAllEdges().stream()
                .map(e -> List.of(e.getNodeA(), e.getNodeB()))
                .toList();
    }

    public GraphEdge createEdge(GraphEdge edge) {
        findNodeById(edge.getNodeA());
        findNodeById(edge.getNodeB());
        GraphEdge saved = edgeRepository.save(edge);
        log.info("Arista creada — {} <-> {}", saved.getNodeA(), saved.getNodeB());
        return saved;
    }

    public void deleteEdge(String edgeId) {
        GraphEdge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        edge.setActive(false);
        edgeRepository.save(edge);
        log.info("Arista desactivada — edgeId={}", edgeId);
    }
}

