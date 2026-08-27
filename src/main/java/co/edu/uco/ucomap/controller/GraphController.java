package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.GraphEdge;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.service.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    // ── Nodes ──────────────────────────────────────────────────

    @GetMapping("/nodes")
    public ResponseEntity<ApiSuccess<List<GraphNode>>> getNodes(
            @RequestParam(required = false) NodeType type) {
        List<GraphNode> nodes = (type != null)
                ? graphService.findNodesByType(type)
                : graphService.findAllNodes();
        return ResponseEntity.ok(ApiSuccess.of(nodes));
    }

    @GetMapping("/nodes/map")
    public ResponseEntity<ApiSuccess<Map<String, GraphNode>>> getNodesMap() {
        return ResponseEntity.ok(ApiSuccess.of(graphService.findAllNodesAsMap()));
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiSuccess<GraphNode>> getNode(@PathVariable String nodeId) {
        return ResponseEntity.ok(ApiSuccess.of(graphService.findNodeById(nodeId)));
    }

    @PostMapping("/nodes")
    public ResponseEntity<ApiSuccess<GraphNode>> createNode(@Valid @RequestBody GraphNode node) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(graphService.createNode(node)));
    }

    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiSuccess<GraphNode>> updateNode(
            @PathVariable String nodeId,
            @Valid @RequestBody GraphNode node) {
        return ResponseEntity.ok(ApiSuccess.of(graphService.updateNode(nodeId, node)));
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiSuccess<Void>> deleteNode(@PathVariable String nodeId) {
        graphService.deleteNode(nodeId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    // ── Edges ──────────────────────────────────────────────────

    @GetMapping("/edges")
    public ResponseEntity<ApiSuccess<List<GraphEdge>>> getEdges() {
        return ResponseEntity.ok(ApiSuccess.of(graphService.findAllEdges()));
    }

    @GetMapping("/edges/pairs")
    public ResponseEntity<ApiSuccess<List<List<String>>>> getEdgePairs() {
        return ResponseEntity.ok(ApiSuccess.of(graphService.findAllEdgesAsPairs()));
    }

    @PostMapping("/edges")
    public ResponseEntity<ApiSuccess<GraphEdge>> createEdge(@Valid @RequestBody GraphEdge edge) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(graphService.createEdge(edge)));
    }

    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<ApiSuccess<Void>> deleteEdge(@PathVariable String edgeId) {
        graphService.deleteEdge(edgeId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}

