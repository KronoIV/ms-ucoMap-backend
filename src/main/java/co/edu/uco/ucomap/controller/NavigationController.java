package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.NavConnection;
import co.edu.uco.ucomap.model.NavMeshData;
import co.edu.uco.ucomap.model.NavPatch;
import co.edu.uco.ucomap.service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;

    // ── Conexiones ─────────────────────────────────────────────

    @GetMapping("/connections")
    public ResponseEntity<ApiSuccess<List<NavConnection>>> getConnections() {
        return ResponseEntity.ok(ApiSuccess.of(navigationService.findAllConnections()));
    }

    @PostMapping("/connections")
    public ResponseEntity<ApiSuccess<NavConnection>> createConnection(@RequestBody NavConnection connection) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(navigationService.createConnection(connection)));
    }

    @PostMapping("/connections/bulk")
    public ResponseEntity<ApiSuccess<List<NavConnection>>> createConnections(@RequestBody List<NavConnection> connections) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(navigationService.createConnections(connections)));
    }

    @PutMapping("/connections/{id}")
    public ResponseEntity<ApiSuccess<NavConnection>> updateConnection(
            @PathVariable String id, @RequestBody NavConnection connection) {
        return ResponseEntity.ok(ApiSuccess.of(navigationService.updateConnection(id, connection)));
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<ApiSuccess<Void>> deleteConnection(@PathVariable String id) {
        navigationService.deleteConnection(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    // ── Parches de suelo ───────────────────────────────────────

    @GetMapping("/patches")
    public ResponseEntity<ApiSuccess<List<NavPatch>>> getPatches() {
        return ResponseEntity.ok(ApiSuccess.of(navigationService.findAllPatches()));
    }

    @PostMapping("/patches")
    public ResponseEntity<ApiSuccess<NavPatch>> createPatch(@RequestBody NavPatch patch) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(navigationService.createPatch(patch)));
    }

    @PutMapping("/patches/{id}")
    public ResponseEntity<ApiSuccess<NavPatch>> updatePatch(@PathVariable String id, @RequestBody NavPatch patch) {
        return ResponseEntity.ok(ApiSuccess.of(navigationService.updatePatch(id, patch)));
    }

    @DeleteMapping("/patches/{id}")
    public ResponseEntity<ApiSuccess<Void>> deletePatch(@PathVariable String id) {
        navigationService.deletePatch(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    // ── Navmesh ────────────────────────────────────────────────

    /** Público: la app AR lo descarga al iniciar. 404 = usar el navmesh incluido en la escena. */
    @GetMapping(value = "/navmesh", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getNavMesh(WebRequest request) {
        NavMeshData navMesh = navigationService.findNavMesh().orElse(null);
        if (navMesh == null) return ResponseEntity.notFound().build();

        String etag = "\"" + navMesh.getUpdatedAt().toEpochMilli() + "\"";
        if (request.checkNotModified(etag)) return null;
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(navMesh.getData());
    }

    @GetMapping("/navmesh/info")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> getNavMeshInfo() {
        return ResponseEntity.ok(ApiSuccess.of(navigationService.findNavMesh()
                .<Map<String, Object>>map(n -> Map.of(
                        "updatedAt", n.getUpdatedAt(),
                        "updatedBy", n.getUpdatedBy() != null ? n.getUpdatedBy() : "",
                        "sizeBytes", n.getData().length))
                .orElse(null)));
    }

    @PutMapping(value = "/navmesh", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiSuccess<Map<String, Object>>> saveNavMesh(@RequestBody byte[] data, Principal principal) {
        NavMeshData saved = navigationService.saveNavMesh(data, principal != null ? principal.getName() : null);
        Instant updatedAt = saved.getUpdatedAt();
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updatedAt", updatedAt, "sizeBytes", data.length)));
    }

    @DeleteMapping("/navmesh")
    public ResponseEntity<ApiSuccess<Void>> deleteNavMesh() {
        navigationService.deleteNavMesh();
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
