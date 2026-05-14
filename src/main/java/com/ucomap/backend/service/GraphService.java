package com.ucomap.backend.service;

import com.ucomap.backend.model.GraphEdge;
import com.ucomap.backend.model.GraphNode;
import com.ucomap.backend.model.NodeType;
import com.ucomap.backend.repository.GraphEdgeRepository;
import com.ucomap.backend.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphNodeRepository nodeRepository;
    private final GraphEdgeRepository edgeRepository;

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
                        HttpStatus.NOT_FOUND, "Nodo no encontrado: " + nodeId));
    }

    public GraphNode createNode(GraphNode node) {
        if (nodeRepository.existsById(node.getNodeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un nodo con id: " + node.getNodeId());
        }
        return nodeRepository.save(node);
    }

    public GraphNode updateNode(String nodeId, GraphNode updated) {
        GraphNode existing = findNodeById(nodeId);
        existing.setGps(updated.getGps());
        existing.setPixel(updated.getPixel());
        existing.setLabel(updated.getLabel());
        existing.setNodeType(updated.getNodeType());
        existing.setActive(updated.isActive());
        return nodeRepository.save(existing);
    }

    public void deleteNode(String nodeId) {
        GraphNode node = findNodeById(nodeId);
        node.setActive(false);
        nodeRepository.save(node);
    }

    // ── Edges ─────────────────────────────────────────────────

    public List<GraphEdge> findAllEdges() {
        return edgeRepository.findByActiveTrue();
    }

    /** Devuelve aristas como lista de pares [nodeA, nodeB] (formato frontend). */
    public List<List<String>> findAllEdgesAsPairs() {
        return edgeRepository.findByActiveTrue().stream()
                .map(e -> List.of(e.getNodeA(), e.getNodeB()))
                .toList();
    }

    public GraphEdge createEdge(GraphEdge edge) {
        findNodeById(edge.getNodeA());
        findNodeById(edge.getNodeB());
        return edgeRepository.save(edge);
    }

    public void deleteEdge(String edgeId) {
        GraphEdge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Arista no encontrada: " + edgeId));
        edge.setActive(false);
        edgeRepository.save(edge);
    }
}

