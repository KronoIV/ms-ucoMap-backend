package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
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

    private static final int MAX_PATCH_POINTS = 64;
    // Los parches son para huecos pequeños del escaneo; evita uno gigante por un clic equivocado
    private static final double MAX_PATCH_EXTENT_M = 50;
    private static final double MIN_PATCH_AREA_M2 = 0.05;

    private static final int MAX_ELEVATOR_STOPS = 60;
    // Mismo rango que el piso de los salones
    private static final int MIN_FLOOR = -5;
    private static final int MAX_FLOOR = 60;
    private static final int MAX_BUTTON_LENGTH = 6;
    // Paradas de un mismo ascensor: cada piso más arriba que el anterior y todas las puertas cerca en planta
    private static final double MIN_STOP_RISE_M = 1.5;
    private static final double MAX_STOP_SPREAD_M = 20;

    private final NavConnectionRepository connectionRepository;
    private final NavMeshRepository navMeshRepository;
    private final NavPatchRepository patchRepository;
    private final NavElevatorRepository elevatorRepository;

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

    // ── Parches de suelo ───────────────────────────────────────

    public List<NavPatch> findAllPatches() {
        return patchRepository.findAll();
    }

    public NavPatch createPatch(NavPatch patch) {
        validate(patch);
        patch.setId(UUID.randomUUID().toString());
        NavPatch saved = patchRepository.save(patch);
        log.info("Parche de navmesh creado — id={} puntos={}", saved.getId(), saved.getPoints().size());
        return saved;
    }

    public NavPatch updatePatch(String id, NavPatch updated) {
        validate(updated);
        NavPatch existing = patchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        existing.setLabel(updated.getLabel());
        existing.setPoints(updated.getPoints());
        NavPatch saved = patchRepository.save(existing);
        log.info("Parche de navmesh actualizado — id={}", id);
        return saved;
    }

    public void deletePatch(String id) {
        if (!patchRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        patchRepository.deleteById(id);
        log.info("Parche de navmesh eliminado — id={}", id);
    }

    private void validate(NavPatch p) {
        List<ArPoint> pts = p.getPoints();
        boolean valid = pts != null && pts.size() >= 3 && pts.size() <= MAX_PATCH_POINTS
                && pts.stream().allMatch(q -> q != null
                        && Double.isFinite(q.getX()) && Double.isFinite(q.getY()) && Double.isFinite(q.getZ()));
        if (valid) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            double area2 = 0;
            for (int i = 0; i < pts.size(); i++) {
                ArPoint a = pts.get(i);
                ArPoint b = pts.get((i + 1) % pts.size());
                area2 += a.getX() * b.getZ() - b.getX() * a.getZ();
                minX = Math.min(minX, a.getX()); maxX = Math.max(maxX, a.getX());
                minZ = Math.min(minZ, a.getZ()); maxZ = Math.max(maxZ, a.getZ());
            }
            valid = Math.abs(area2) / 2 >= MIN_PATCH_AREA_M2
                    && maxX - minX <= MAX_PATCH_EXTENT_M && maxZ - minZ <= MAX_PATCH_EXTENT_M;
        }
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
    }

    // ── Ascensores ─────────────────────────────────────────────

    public List<NavElevator> findAllElevators() {
        return elevatorRepository.findAll();
    }

    public NavElevator createElevator(NavElevator elevator) {
        normalize(elevator);
        elevator.setId(UUID.randomUUID().toString());
        NavElevator saved = elevatorRepository.save(elevator);
        log.info("Ascensor creado — id={} label={} paradas={}", saved.getId(), saved.getLabel(), saved.getStops().size());
        return saved;
    }

    public NavElevator updateElevator(String id, NavElevator updated) {
        normalize(updated);
        NavElevator existing = elevatorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        existing.setLabel(updated.getLabel());
        existing.setBuilding(updated.getBuilding());
        existing.setStops(updated.getStops());
        NavElevator saved = elevatorRepository.save(existing);
        log.info("Ascensor actualizado — id={}", id);
        return saved;
    }

    public void deleteElevator(String id) {
        if (!elevatorRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        elevatorRepository.deleteById(id);
        log.info("Ascensor eliminado — id={}", id);
    }

    /**
     * Valida y deja listo para guardar: paradas ordenadas por piso, botones vacíos como null, nombre y edificio sin
     * espacios sobrantes. Los números de piso deben seguir la altura y las puertas estar cerca en planta: así una parada
     * marcada en el piso o en el ascensor equivocado no llega a la app.
     */
    private void normalize(NavElevator e) {
        List<ElevatorStop> stops = e.getStops();
        boolean valid = stops != null && stops.size() >= 2 && stops.size() <= MAX_ELEVATOR_STOPS
                && stops.stream().allMatch(s -> s != null && s.getFloor() != null
                        && s.getFloor() >= MIN_FLOOR && s.getFloor() <= MAX_FLOOR && isFinite(s.getPoint())
                        && (s.getButton() == null || s.getButton().trim().length() <= MAX_BUTTON_LENGTH));
        List<ElevatorStop> sorted = valid
                ? stops.stream().sorted(Comparator.comparing(ElevatorStop::getFloor)).toList()
                : List.of();
        for (int i = 1; valid && i < sorted.size(); i++) {
            ElevatorStop lower = sorted.get(i - 1), upper = sorted.get(i);
            valid = !upper.getFloor().equals(lower.getFloor())
                    && upper.getPoint().getY() - lower.getPoint().getY() >= MIN_STOP_RISE_M;
        }
        for (ElevatorStop a : sorted) {
            for (ElevatorStop b : sorted) {
                valid &= Math.hypot(a.getPoint().getX() - b.getPoint().getX(),
                        a.getPoint().getZ() - b.getPoint().getZ()) <= MAX_STOP_SPREAD_M;
            }
        }
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
        }
        e.setStops(sorted.stream().map(s -> new ElevatorStop(s.getFloor(), blankToNull(s.getButton()), s.getPoint())).toList());
        String label = blankToNull(e.getLabel());
        e.setLabel(label != null ? label : "Ascensor");
        e.setBuilding(blankToNull(e.getBuilding()));
    }

    private static boolean isFinite(ArPoint p) {
        return p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY()) && Double.isFinite(p.getZ());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
