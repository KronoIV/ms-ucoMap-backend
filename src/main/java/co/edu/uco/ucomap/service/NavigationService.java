package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.NavConnection;
import co.edu.uco.ucomap.model.NavMeshData;
import co.edu.uco.ucomap.repository.NavConnectionRepository;
import co.edu.uco.ucomap.repository.NavMeshRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private static final int MAX_NAVMESH_BYTES = 5 * 1024 * 1024;
    // Cabecera de exportNavMesh (recast-navigation): "MSET" en little-endian
    private static final byte[] NAVMESH_MAGIC = "TESM".getBytes(StandardCharsets.US_ASCII);

    private final NavConnectionRepository connectionRepository;
    private final NavMeshRepository navMeshRepository;

    // ── Conexiones (escaleras) ─────────────────────────────────

    public List<NavConnection> findAllConnections() {
        return connectionRepository.findAll();
    }

    public NavConnection createConnection(NavConnection connection) {
        validate(connection);
        connection.setId(UUID.randomUUID().toString());
        NavConnection saved = connectionRepository.save(connection);
        log.info("Conexión de navegación creada — id={} label={}", saved.getId(), saved.getLabel());
        return saved;
    }

    public List<NavConnection> createConnections(List<NavConnection> connections) {
        connections.forEach(c -> { validate(c); c.setId(UUID.randomUUID().toString()); });
        List<NavConnection> saved = connectionRepository.saveAll(connections);
        log.info("Conexiones de navegación importadas — total={}", saved.size());
        return saved;
    }

    public NavConnection updateConnection(String id, NavConnection updated) {
        validate(updated);
        NavConnection existing = connectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        existing.setLabel(updated.getLabel());
        existing.setGroup(updated.getGroup());
        existing.setStart(updated.getStart());
        existing.setEnd(updated.getEnd());
        existing.setRadius(updated.getRadius());
        existing.setBidirectional(updated.isBidirectional());
        NavConnection saved = connectionRepository.save(existing);
        log.info("Conexión de navegación actualizada — id={}", id);
        return saved;
    }

    public void deleteConnection(String id) {
        if (!connectionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        connectionRepository.deleteById(id);
        log.info("Conexión de navegación eliminada — id={}", id);
    }

    private void validate(NavConnection c) {
        if (c.getStart() == null || c.getEnd() == null || c.getRadius() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
    }

    // ── Navmesh ────────────────────────────────────────────────

    public Optional<NavMeshData> findNavMesh() {
        return navMeshRepository.findById(NavMeshData.CURRENT_ID);
    }

    public NavMeshData saveNavMesh(byte[] data, String updatedBy) {
        if (data == null || data.length < NAVMESH_MAGIC.length || data.length > MAX_NAVMESH_BYTES
                || !Arrays.equals(Arrays.copyOf(data, NAVMESH_MAGIC.length), NAVMESH_MAGIC)) {
            log.warn("Navmesh rechazado — bytes={}", data == null ? 0 : data.length);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
        NavMeshData saved = navMeshRepository.save(NavMeshData.builder()
                .id(NavMeshData.CURRENT_ID)
                .data(data)
                .updatedAt(Instant.now())
                .updatedBy(updatedBy)
                .build());
        log.info("Navmesh publicado — bytes={} por={}", data.length, updatedBy);
        return saved;
    }

    public void deleteNavMesh() {
        navMeshRepository.deleteById(NavMeshData.CURRENT_ID);
        log.info("Navmesh publicado eliminado; la app vuelve a usar el de Mattercraft");
    }
}
