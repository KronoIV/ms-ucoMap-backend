package com.ucomap.backend.controller;

import com.ucomap.backend.model.GraphEdge;
import com.ucomap.backend.model.GraphNode;
import com.ucomap.backend.model.NodeType;
import com.ucomap.backend.service.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD del grafo de caminos del campus.
 *
 * GET    /api/graph/nodes              — todos los nodos activos
 * GET    /api/graph/nodes/map          — nodos como mapa nodeId → nodo
 * GET    /api/graph/nodes/{nodeId}     — nodo específico
 * GET    /api/graph/nodes?type=ENTRANCE — filtrar por tipo
 * POST   /api/graph/nodes              — crear nodo
 * PUT    /api/graph/nodes/{nodeId}     — actualizar nodo
 * DELETE /api/graph/nodes/{nodeId}     — desactivar nodo
 *
 * GET    /api/graph/edges              — todas las aristas activas
 * GET    /api/graph/edges/pairs        — aristas como lista de pares [nodeA, nodeB]
 * POST   /api/graph/edges              — crear arista
 * DELETE /api/graph/edges/{edgeId}     — desactivar arista
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    // ── Nodes ──────────────────────────────────────────────────

    @GetMapping("/nodes")
    public ResponseEntity<List<GraphNode>> getNodes(
            @RequestParam(required = false) NodeType type) {
        List<GraphNode> nodes = (type != null)
                ? graphService.findNodesByType(type)
                : graphService.findAllNodes();
        return ResponseEntity.ok(nodes);
    }

    @GetMapping("/nodes/map")
    public ResponseEntity<Map<String, GraphNode>> getNodesMap() {
        return ResponseEntity.ok(graphService.findAllNodesAsMap());
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<GraphNode> getNode(@PathVariable String nodeId) {
        return ResponseEntity.ok(graphService.findNodeById(nodeId));
    }

    @PostMapping("/nodes")
    public ResponseEntity<GraphNode> createNode(@Valid @RequestBody GraphNode node) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(graphService.createNode(node));
    }

    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<GraphNode> updateNode(
            @PathVariable String nodeId,
            @Valid @RequestBody GraphNode node) {
        return ResponseEntity.ok(graphService.updateNode(nodeId, node));
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String nodeId) {
        graphService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    // ── Edges ──────────────────────────────────────────────────

    @GetMapping("/edges")
    public ResponseEntity<List<GraphEdge>> getEdges() {
        return ResponseEntity.ok(graphService.findAllEdges());
    }

    @GetMapping("/edges/pairs")
    public ResponseEntity<List<List<String>>> getEdgePairs() {
        return ResponseEntity.ok(graphService.findAllEdgesAsPairs());
    }

    @PostMapping("/edges")
    public ResponseEntity<GraphEdge> createEdge(@Valid @RequestBody GraphEdge edge) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(graphService.createEdge(edge));
    }

    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<Void> deleteEdge(@PathVariable String edgeId) {
        graphService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }
}

