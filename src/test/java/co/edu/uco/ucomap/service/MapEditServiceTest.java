package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.ArPoint;
import co.edu.uco.ucomap.model.NavBlock;
import co.edu.uco.ucomap.model.ScanEdit;
import co.edu.uco.ucomap.model.ScanEditMesh;
import co.edu.uco.ucomap.repository.NavBlockRepository;
import co.edu.uco.ucomap.repository.NavConnectionRepository;
import co.edu.uco.ucomap.repository.ScanEditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapEditServiceTest {

    @Mock ScanEditRepository scanEditRepository;
    @Mock NavBlockRepository blockRepository;
    @Mock NavConnectionRepository connectionRepository;
    @InjectMocks MapEditService service;

    private static final ArPoint ORIGIN = new ArPoint(0, 0, 0);

    private static ScanEdit edit(List<Integer> ranges, int triangles) {
        return ScanEdit.builder()
                .meshes(List.of(ScanEditMesh.builder().map("M-1A").mesh(0).triangles(triangles).ranges(ranges).build()))
                .center(ORIGIN)
                .build();
    }

    private void assertBadRequest(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void scanEditCountsTrianglesServerSide() {
        when(scanEditRepository.save(any(ScanEdit.class))).thenAnswer(inv -> inv.getArgument(0));
        ScanEdit e = edit(List.of(0, 10, 20, 5), 100);
        e.setTriangleCount(999_999);

        ScanEdit saved = service.createScanEdit(e, "admin@uco.edu.co");

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getTriangleCount()).isEqualTo(15);
        assertThat(saved.getLabel()).isEqualTo("Limpieza del modelo");
        assertThat(saved.getCreatedBy()).isEqualTo("admin@uco.edu.co");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void scanEditRejectsBadRanges() {
        assertBadRequest(() -> service.createScanEdit(edit(List.of(0, 10, 5, 2), 100), null)); // solapados
        assertBadRequest(() -> service.createScanEdit(edit(List.of(95, 10), 100), null));      // fuera de la malla
        assertBadRequest(() -> service.createScanEdit(edit(List.of(3), 100), null));           // impar
        assertBadRequest(() -> service.createScanEdit(edit(List.of(3, 0), 100), null));        // vacío
        assertBadRequest(() -> service.createScanEdit(edit(List.of(), 100), null));
        ScanEdit noCenter = edit(List.of(0, 1), 100);
        noCenter.setCenter(null);
        assertBadRequest(() -> service.createScanEdit(noCenter, null));
        verify(scanEditRepository, never()).save(any());
    }

    @Test
    void areaBlockKeepsOnlyItsPolygons() {
        when(blockRepository.save(any(NavBlock.class))).thenAnswer(inv -> inv.getArgument(0));
        NavBlock b = NavBlock.builder()
                .kind(NavBlock.Kind.AREA)
                .polygons(List.of(List.of(ORIGIN, new ArPoint(1, 0, 0), new ArPoint(1, 0, 1))))
                .line(List.of(ORIGIN, new ArPoint(1, 0, 0)))
                .connectionId("x")
                .build();

        NavBlock saved = service.createBlock(b, "admin");

        assertThat(saved.getLine()).isNull();
        assertThat(saved.getConnectionId()).isNull();
        assertThat(saved.getLabel()).isEqualTo("Zona bloqueada");
    }

    @Test
    void invalidBlocksAreRejected() {
        assertBadRequest(() -> service.createBlock(NavBlock.builder().build(), null));
        assertBadRequest(() -> service.createBlock(NavBlock.builder().kind(NavBlock.Kind.AREA)
                .polygons(List.of(List.of(ORIGIN, new ArPoint(1, 0, 0)))).build(), null));
        assertBadRequest(() -> service.createBlock(NavBlock.builder().kind(NavBlock.Kind.AREA)
                .polygons(List.of(List.of(ORIGIN, new ArPoint(100, 0, 0), new ArPoint(100, 0, 1)))).build(), null));
        assertBadRequest(() -> service.createBlock(NavBlock.builder().kind(NavBlock.Kind.BARRIER)
                .line(List.of(ORIGIN, new ArPoint(0.01, 0, 0))).build(), null));
        assertBadRequest(() -> service.createBlock(NavBlock.builder().kind(NavBlock.Kind.BARRIER)
                .line(List.of(ORIGIN, new ArPoint(Double.NaN, 0, 0))).build(), null));
        verify(blockRepository, never()).save(any());
    }

    @Test
    void connectionBlockNeedsAnExistingConnection() {
        when(connectionRepository.existsById("missing")).thenReturn(false);
        assertBadRequest(() -> service.createBlock(NavBlock.builder().kind(NavBlock.Kind.CONNECTION)
                .connectionId("missing").build(), null));

        when(connectionRepository.existsById("c1")).thenReturn(true);
        when(blockRepository.save(any(NavBlock.class))).thenAnswer(inv -> inv.getArgument(0));
        NavBlock saved = service.createBlock(NavBlock.builder().kind(NavBlock.Kind.CONNECTION).connectionId("c1")
                .label("  Escalera norte  ").build(), null);
        assertThat(saved.getLabel()).isEqualTo("Escalera norte");
    }
}
