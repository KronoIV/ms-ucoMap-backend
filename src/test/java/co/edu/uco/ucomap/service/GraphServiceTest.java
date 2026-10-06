package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.GraphEdge;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.repository.GraphEdgeRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock GraphNodeRepository nodeRepository;
    @Mock GraphEdgeRepository edgeRepository;
    @Mock BuildingNodeSync buildingSync;
    @InjectMocks GraphService service;

    private static GraphNode node(String id) {
        return GraphNode.builder().nodeId(id).nodeType(NodeType.WAYPOINT).build();
    }

    private static GraphEdge edge(String id, String a, String b) {
        return GraphEdge.builder().id(id).nodeA(a).nodeB(b).build();
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void edgesPointingToInactiveNodesAreNotReturned() {
        // Una arista hacia un nodo borrado rompería el Dijkstra de la app
        when(nodeRepository.findByActiveTrue()).thenReturn(List.of(node("E1"), node("P1"), node("EDC")));
        GraphEdge valid = edge("1", "E1", "P1");
        GraphEdge validToBuilding = edge("2", "P1", "EDC");
        GraphEdge orphan = edge("3", "P1", "P99");
        when(edgeRepository.findByActiveTrue()).thenReturn(List.of(valid, validToBuilding, orphan));

        assertThat(service.findAllEdges()).containsExactly(valid, validToBuilding);
        assertThat(service.findAllEdgesAsPairs()).containsExactly(List.of("E1", "P1"), List.of("P1", "EDC"));
    }

    @Test
    void deletingNodeSoftDeletesItAndItsEdges() {
        GraphNode p1 = node("P1");
        GraphEdge e1 = edge("1", "E1", "P1");
        GraphEdge e2 = edge("2", "P1", "EDC");
        when(nodeRepository.findById("P1")).thenReturn(Optional.of(p1));
        when(edgeRepository.findByNodeAOrNodeB("P1", "P1")).thenReturn(List.of(e1, e2));

        service.deleteNode("P1");

        assertThat(p1.isActive()).isFalse();
        assertThat(e1.isActive()).isFalse();
        assertThat(e2.isActive()).isFalse();
        verify(nodeRepository).save(p1);
        verify(edgeRepository).saveAll(List.of(e1, e2));
    }

    @Test
    void duplicateNodeIdIsRejected() {
        when(nodeRepository.existsById("E1")).thenReturn(true);

        assertStatus(() -> service.createNode(node("E1")), HttpStatus.CONFLICT);
        verify(nodeRepository, never()).save(any());
    }

    @Test
    void edgeToUnknownNodeIsRejected() {
        when(nodeRepository.findById("E1")).thenReturn(Optional.of(node("E1")));
        when(nodeRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertStatus(() -> service.createEdge(edge(null, "E1", "NOPE")), HttpStatus.NOT_FOUND);
        verify(edgeRepository, never()).save(any());
    }

    @Test
    void deletingUnknownEdgeReturnsNotFound() {
        when(edgeRepository.findById("x")).thenReturn(Optional.empty());

        assertStatus(() -> service.deleteEdge("x"), HttpStatus.NOT_FOUND);
    }

    @Test
    void updateKeepsNodeIdAndReplacesData() {
        GraphNode stored = node("P1");
        when(nodeRepository.findById("P1")).thenReturn(Optional.of(stored));
        when(nodeRepository.save(stored)).thenReturn(stored);
        GraphNode changes = GraphNode.builder().nodeId("OTRO").label("Entrada").nodeType(NodeType.ENTRANCE)
                .active(false).build();

        GraphNode result = service.updateNode("P1", changes);

        assertThat(result.getNodeId()).isEqualTo("P1");
        assertThat(result.getLabel()).isEqualTo("Entrada");
        assertThat(result.getNodeType()).isEqualTo(NodeType.ENTRANCE);
        assertThat(result.isActive()).isFalse();
    }
}
