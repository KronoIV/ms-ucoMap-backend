package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.NavConnection;
import co.edu.uco.ucomap.model.NavMeshData;
import co.edu.uco.ucomap.repository.NavConnectionRepository;
import co.edu.uco.ucomap.repository.NavMeshRepository;
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
}
