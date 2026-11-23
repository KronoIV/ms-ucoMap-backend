package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.ArPoint;
import co.edu.uco.ucomap.model.NavBlock;
import co.edu.uco.ucomap.model.ScanEdit;
import co.edu.uco.ucomap.model.ScanEditMesh;
import co.edu.uco.ucomap.repository.NavBlockRepository;
import co.edu.uco.ucomap.repository.NavConnectionRepository;
import co.edu.uco.ucomap.repository.ScanEditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ediciones manuales que entran a la generación del navmesh: limpieza del modelo 3D y bloqueos de la malla. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapEditService {

    private static final int MAX_LABEL_LENGTH = 120;

    private static final int MAX_EDIT_MESHES = 64;
    private static final int MAX_MESH_ORDINAL = 10_000;
    private static final int MAX_MESH_TRIANGLES = 20_000_000;
    // Pares [inicio, cantidad] en toda la edición: mantiene el documento muy por debajo del límite de Mongo
    private static final int MAX_RANGE_INTS = 400_000;

    private static final int MAX_AREA_POLYGONS = 5_000;
    private static final int MAX_POLYGON_POINTS = 16;
    private static final double MAX_POLYGON_EXTENT_M = 60;
    private static final int MAX_BARRIER_POINTS = 64;
    private static final double MIN_BARRIER_SEGMENT_M = 0.05;
    private static final double MAX_BARRIER_SEGMENT_M = 100;

    private final ScanEditRepository scanEditRepository;
    private final NavBlockRepository blockRepository;
    private final NavConnectionRepository connectionRepository;

    // ── Limpieza del modelo ────────────────────────────────────

    public List<ScanEdit> findAllScanEdits() {
        return scanEditRepository.findAll();
    }

    public ScanEdit createScanEdit(ScanEdit edit, String createdBy) {
        int triangles = validate(edit);
        edit.setId(UUID.randomUUID().toString());
        edit.setTriangleCount(triangles);
        edit.setLabel(labelOr(edit.getLabel(), "Limpieza del modelo"));
        edit.setCreatedAt(Instant.now());
        edit.setCreatedBy(createdBy);
        ScanEdit saved = scanEditRepository.save(edit);
        log.info("Limpieza del modelo guardada — id={} triángulos={} por={}", saved.getId(), triangles, createdBy);
        return saved;
    }

    public void deleteScanEdit(String id) {
        if (!scanEditRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        scanEditRepository.deleteById(id);
        log.info("Limpieza del modelo restaurada — id={}", id);
    }

    /** Devuelve la cantidad de triángulos quitados; rechaza rangos desordenados, solapados o fuera de la malla. */
    private int validate(ScanEdit e) {
        List<ScanEditMesh> meshes = e.getMeshes();
        if (meshes == null || meshes.isEmpty() || meshes.size() > MAX_EDIT_MESHES || !isFinite(e.getCenter())) {
            throw badRequest();
        }
        long total = 0;
        long ints = 0;
        for (ScanEditMesh m : meshes) {
            List<Integer> ranges = m == null ? null : m.getRanges();
            if (ranges == null || ranges.isEmpty() || ranges.size() % 2 != 0 || m.getMap() == null || m.getMap().isBlank()
                    || m.getMap().length() > MAX_LABEL_LENGTH || m.getMesh() < 0 || m.getMesh() > MAX_MESH_ORDINAL
                    || m.getTriangles() <= 0 || m.getTriangles() > MAX_MESH_TRIANGLES) {
                throw badRequest();
            }
            ints += ranges.size();
            long next = 0;
            for (int i = 0; i < ranges.size(); i += 2) {
                Integer start = ranges.get(i), count = ranges.get(i + 1);
                if (start == null || count == null || start < next || count < 1 || (long) start + count > m.getTriangles()) {
                    throw badRequest();
                }
                next = (long) start + count;
                total += count;
            }
        }
        if (ints > MAX_RANGE_INTS) throw badRequest();
        for (int i = 0; i < meshes.size(); i++) {
            for (int j = i + 1; j < meshes.size(); j++) {
                if (meshes.get(i).getMap().equals(meshes.get(j).getMap()) && meshes.get(i).getMesh() == meshes.get(j).getMesh()) {
                    throw badRequest();
                }
            }
        }
        return (int) total;
    }

    // ── Bloqueos del navmesh ───────────────────────────────────

    public List<NavBlock> findAllBlocks() {
        return blockRepository.findAll();
    }

    public NavBlock createBlock(NavBlock block, String createdBy) {
        normalize(block);
        block.setId(UUID.randomUUID().toString());
        block.setCreatedAt(Instant.now());
        block.setCreatedBy(createdBy);
        NavBlock saved = blockRepository.save(block);
        log.info("Bloqueo de navmesh creado — id={} tipo={} por={}", saved.getId(), saved.getKind(), createdBy);
        return saved;
    }

    public void deleteBlock(String id) {
        if (!blockRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        blockRepository.deleteById(id);
        log.info("Bloqueo de navmesh quitado — id={}", id);
    }

    /** Valida según el tipo y deja solo los campos de ese tipo. */
    private void normalize(NavBlock b) {
        if (b.getKind() == null) throw badRequest();
        switch (b.getKind()) {
            case AREA -> {
                List<List<ArPoint>> polygons = b.getPolygons();
                if (polygons == null || polygons.isEmpty() || polygons.size() > MAX_AREA_POLYGONS
                        || !polygons.stream().allMatch(MapEditService::validPolygon)) {
                    throw badRequest();
                }
                b.setLine(null);
                b.setConnectionId(null);
                b.setLabel(labelOr(b.getLabel(), "Zona bloqueada"));
            }
            case BARRIER -> {
                List<ArPoint> line = b.getLine();
                boolean valid = line != null && line.size() >= 2 && line.size() <= MAX_BARRIER_POINTS
                        && line.stream().allMatch(MapEditService::isFinite);
                for (int i = 1; valid && i < line.size(); i++) {
                    double len = Math.hypot(line.get(i).getX() - line.get(i - 1).getX(), line.get(i).getZ() - line.get(i - 1).getZ());
                    valid = len >= MIN_BARRIER_SEGMENT_M && len <= MAX_BARRIER_SEGMENT_M;
                }
                if (!valid) throw badRequest();
                b.setPolygons(null);
                b.setConnectionId(null);
                b.setLabel(labelOr(b.getLabel(), "Corte de paso"));
            }
            case CONNECTION -> {
                String connectionId = b.getConnectionId();
                if (connectionId == null || connectionId.isBlank() || !connectionRepository.existsById(connectionId)) {
                    throw badRequest();
                }
                b.setPolygons(null);
                b.setLine(null);
                b.setLabel(labelOr(b.getLabel(), "Conexión bloqueada"));
            }
        }
    }

    private static boolean validPolygon(List<ArPoint> pts) {
        if (pts == null || pts.size() < 3 || pts.size() > MAX_POLYGON_POINTS || !pts.stream().allMatch(MapEditService::isFinite)) {
            return false;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (ArPoint p : pts) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return maxX - minX <= MAX_POLYGON_EXTENT_M && maxZ - minZ <= MAX_POLYGON_EXTENT_M;
    }

    private static boolean isFinite(ArPoint p) {
        return p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY()) && Double.isFinite(p.getZ());
    }

    private static String labelOr(String label, String fallback) {
        if (label == null || label.isBlank()) return fallback;
        String trimmed = label.trim();
        return trimmed.length() > MAX_LABEL_LENGTH ? trimmed.substring(0, MAX_LABEL_LENGTH) : trimmed;
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
    }
}
