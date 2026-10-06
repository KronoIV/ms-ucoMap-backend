package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.GpsPoint;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingNodeSyncTest {

    @Mock BuildingRepository buildingRepository;
    @Mock GraphNodeRepository nodeRepository;
    @InjectMocks BuildingNodeSync sync;

    private static final GpsPoint HERE = new GpsPoint(6.15, -75.366);

    private static GraphNode node(String id, NodeType type, String label) {
        return GraphNode.builder().nodeId(id).nodeType(type).label(label).gps(HERE).active(true).build();
    }

    private static Building building(String id, String category, String nodeId) {
        return Building.builder().buildingId(id).label(id).category(category).nodeId(nodeId)
                .gps(new GpsPoint(6.1, -75.3)).active(true).build();
    }

    private void noBuildings() {
        lenient().when(buildingRepository.findFirstByNodeId(any())).thenReturn(Optional.empty());
        lenient().when(buildingRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void buildingNodeCreatedOnTheMapAppearsInCampus() {
        noBuildings();
        when(buildingRepository.existsById("W1")).thenReturn(false);
        when(buildingRepository.existsByCategoryIgnoreCase("W1")).thenReturn(false);

        sync.nodeSaved(node("W1", NodeType.BUILDING, "Biblioteca"), true);

        ArgumentCaptor<Building> saved = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository).save(saved.capture());
        assertThat(saved.getValue().getBuildingId()).isEqualTo("W1");
        assertThat(saved.getValue().getNodeId()).isEqualTo("W1");
        assertThat(saved.getValue().getLabel()).isEqualTo("Biblioteca");
        assertThat(saved.getValue().getGps()).isEqualTo(HERE);
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void draggingTheNodeMovesTheBuildingWithoutRenamingIt() {
        Building existing = building("EF", "Fundacional", "Fundacional");
        existing.setLabel("Edificio Fundacional");
        when(buildingRepository.findFirstByNodeId("Fundacional")).thenReturn(Optional.of(existing));

        sync.nodeSaved(node("Fundacional", NodeType.BUILDING, "Fundacional"), false);

        assertThat(existing.getGps()).isEqualTo(HERE);
        assertThat(existing.getLabel()).isEqualTo("Edificio Fundacional");
        verify(buildingRepository).save(existing);
    }

    @Test
    void nodeThatStopsBeingABuildingHidesItsBuilding() {
        Building existing = building("J", "J", null);
        when(buildingRepository.findFirstByNodeId("J")).thenReturn(Optional.empty());
        when(buildingRepository.findById("J")).thenReturn(Optional.of(existing));

        sync.nodeSaved(node("J", NodeType.WAYPOINT, null), false);

        assertThat(existing.isActive()).isFalse();
        verify(buildingRepository).save(existing);
    }

    @Test
    void buildingCreatedInCampusGetsItsNodeOnTheMap() {
        when(nodeRepository.findById("BIB")).thenReturn(Optional.empty());
        Building b = building("BIB", "BIB", null);

        sync.buildingSaved(b, true);

        ArgumentCaptor<GraphNode> saved = ArgumentCaptor.forClass(GraphNode.class);
        verify(nodeRepository).save(saved.capture());
        assertThat(saved.getValue().getNodeId()).isEqualTo("BIB");
        assertThat(saved.getValue().getNodeType()).isEqualTo(NodeType.BUILDING);
        assertThat(saved.getValue().getGps()).isEqualTo(b.getGps());
        assertThat(b.getNodeId()).isEqualTo("BIB");
    }

    @Test
    void updatingABuildingWithoutPointDoesNotPutItOnTheMap() {
        when(nodeRepository.findById("OTROS")).thenReturn(Optional.empty());

        sync.buildingSaved(building("OTROS", "Otros", null), false);

        verify(nodeRepository, never()).save(any());
    }

    @Test
    void reconcileLinksByCategoryAndCreatesMissingBuildings() {
        Building ef = building("EF", "Fundacional", null);
        Building otros = building("OTROS", "Otros", null);
        when(nodeRepository.findByActiveTrue()).thenReturn(List.of(
                node("Fundacional", NodeType.BUILDING, "Fundacional"),
                node("INN", NodeType.BUILDING, "INN"),
                node("P1", NodeType.WAYPOINT, null)));
        when(buildingRepository.findAll()).thenReturn(List.of(ef, otros));
        when(buildingRepository.existsById("INN")).thenReturn(false);
        when(buildingRepository.existsByCategoryIgnoreCase("INN")).thenReturn(false);

        sync.reconcile();

        assertThat(ef.getNodeId()).isEqualTo("Fundacional");
        assertThat(ef.getGps()).isEqualTo(HERE);
        assertThat(otros.getNodeId()).isNull();
        ArgumentCaptor<Building> saved = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Building::getBuildingId).containsExactly("EF", "INN");
    }
}
