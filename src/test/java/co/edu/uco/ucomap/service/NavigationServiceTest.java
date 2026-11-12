package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.ArPoint;
import co.edu.uco.ucomap.model.ElevatorStop;
import co.edu.uco.ucomap.model.NavConnection;
import co.edu.uco.ucomap.model.NavElevator;
import co.edu.uco.ucomap.model.NavMeshData;
import co.edu.uco.ucomap.model.NavPatch;
import co.edu.uco.ucomap.repository.NavConnectionRepository;
import co.edu.uco.ucomap.repository.NavElevatorRepository;
import co.edu.uco.ucomap.repository.NavMeshRepository;
import co.edu.uco.ucomap.repository.NavPatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationServiceTest {

    @Mock NavConnectionRepository connectionRepository;
    @Mock NavMeshRepository navMeshRepository;
    @Mock NavPatchRepository patchRepository;
    @Mock NavElevatorRepository elevatorRepository;
    @InjectMocks NavigationService service;

    private static byte[] navmesh(int size) {
        byte[] data = new byte[size];
        byte[] magic = "TESM".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }

    private void assertRejectedNavMesh(byte[] data) {
        assertThatThrownBy(() -> service.saveNavMesh(data, "admin@uco.edu.co"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(navMeshRepository, never()).save(any());
    }

    @Test
    void validNavMeshIsStoredAsTheCurrentOne() {
        when(navMeshRepository.save(any(NavMeshData.class))).thenAnswer(inv -> inv.getArgument(0));

        NavMeshData saved = service.saveNavMesh(navmesh(1024), "admin@uco.edu.co");

        assertThat(saved.getId()).isEqualTo(NavMeshData.CURRENT_ID);
        assertThat(saved.getData()).hasSize(1024);
        assertThat(saved.getUpdatedBy()).isEqualTo("admin@uco.edu.co");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void navMeshWithoutRecastHeaderIsRejected() {
        byte[] png = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, 1024);
        assertRejectedNavMesh(png);
    }

    @Test
    void emptyOrTruncatedNavMeshIsRejected() {
        assertRejectedNavMesh(null);
        assertRejectedNavMesh(new byte[0]);
        assertRejectedNavMesh(new byte[]{'T', 'E'});
    }

    @Test
    void navMeshOverFiveMegabytesIsRejected() {
        assertRejectedNavMesh(navmesh(5 * 1024 * 1024 + 1));
    }

    @Test
    void connectionWithoutEndpointsOrRadiusIsRejected() {
        NavConnection invalid = new NavConnection();

        assertThatThrownBy(() -> service.createConnection(invalid))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void deletingUnknownConnectionReturnsNotFound() {
        when(connectionRepository.existsById("x")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteConnection("x"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(connectionRepository, never()).deleteById(any());
    }

    private static NavPatch patch(double... xz) {
        List<ArPoint> points = new java.util.ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) points.add(new ArPoint(xz[i], 1.5, xz[i + 1]));
        return NavPatch.builder().label("Hueco").points(points).build();
    }

    private void assertRejectedPatch(NavPatch p) {
        assertThatThrownBy(() -> service.createPatch(p))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(patchRepository, never()).save(any());
    }

    @Test
    void floorPatchIsStoredWithNewId() {
        when(patchRepository.save(any(NavPatch.class))).thenAnswer(inv -> inv.getArgument(0));

        NavPatch saved = service.createPatch(patch(0, 0, 2, 0, 2, 1, 0, 1));

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getPoints()).hasSize(4);
    }

    @Test
    void patchWithFewerThanThreePointsIsRejected() {
        assertRejectedPatch(patch(0, 0, 1, 1));
        assertRejectedPatch(NavPatch.builder().label("x").build());
    }

    @Test
    void degenerateOrHugePatchIsRejected() {
        assertRejectedPatch(patch(0, 0, 1, 0, 2, 0));
        assertRejectedPatch(patch(0, 0, 80, 0, 80, 2, 0, 2));
    }

    @Test
    void patchWithNonFiniteCoordinatesIsRejected() {
        assertRejectedPatch(patch(0, 0, Double.NaN, 0, 1, 1));
    }

    /** Parada frente a la puerta del bloque M: los pisos están a 3,6 m. */
    private static ElevatorStop stop(int floor, double y) {
        return new ElevatorStop(floor, null, new ArPoint(10, y, 5));
    }

    private static NavElevator elevator(ElevatorStop... stops) {
        return NavElevator.builder().label("Ascensor M").building("M").stops(List.of(stops)).build();
    }

    private void assertRejectedElevator(NavElevator e) {
        assertThatThrownBy(() -> service.createElevator(e))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(elevatorRepository, never()).save(any());
    }

    @Test
    void elevatorIsStoredWithStopsSortedByFloorAndBlanksCleared() {
        when(elevatorRepository.save(any(NavElevator.class))).thenAnswer(inv -> inv.getArgument(0));
        ElevatorStop ground = new ElevatorStop(1, "  PB ", new ArPoint(10, 0, 5));
        ElevatorStop third = new ElevatorStop(3, " ", new ArPoint(10.4, 7.2, 5));
        NavElevator input = NavElevator.builder().label("  ").building(" M ").stops(List.of(third, stop(2, 3.6), ground)).build();

        NavElevator saved = service.createElevator(input);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getLabel()).isEqualTo("Ascensor");
        assertThat(saved.getBuilding()).isEqualTo("M");
        assertThat(saved.getStops()).extracting(ElevatorStop::getFloor).containsExactly(1, 2, 3);
        assertThat(saved.getStops()).extracting(ElevatorStop::getButton).containsExactly("PB", null, null);
    }

    @Test
    void elevatorNeedsTwoDifferentFloors() {
        assertRejectedElevator(elevator(stop(1, 0)));
        assertRejectedElevator(elevator(stop(2, 0), stop(2, 3.6)));
        assertRejectedElevator(NavElevator.builder().label("x").build());
    }

    @Test
    void elevatorFloorsMustFollowTheHeightOfTheirStops() {
        // El piso 3 marcado por debajo del 2, o dos pisos en la misma planta
        assertRejectedElevator(elevator(stop(1, 0), stop(2, 7.2), stop(3, 3.6)));
        assertRejectedElevator(elevator(stop(1, 0), stop(2, 0.4)));
    }

    @Test
    void elevatorStopsFarApartOrInvalidAreRejected() {
        assertRejectedElevator(elevator(stop(1, 0), new ElevatorStop(2, null, new ArPoint(60, 3.6, 5))));
        assertRejectedElevator(elevator(stop(1, 0), new ElevatorStop(2, null, new ArPoint(10, Double.NaN, 5))));
        assertRejectedElevator(elevator(stop(1, 0), new ElevatorStop(99, null, new ArPoint(10, 3.6, 5))));
        assertRejectedElevator(elevator(stop(1, 0), new ElevatorStop(2, "PISO-DOS", new ArPoint(10, 3.6, 5))));
    }

    @Test
    void updatingUnknownElevatorReturnsNotFound() {
        when(elevatorRepository.findById("x")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.updateElevator("x", elevator(stop(1, 0), stop(2, 3.6))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(elevatorRepository, never()).save(any());
    }
}
