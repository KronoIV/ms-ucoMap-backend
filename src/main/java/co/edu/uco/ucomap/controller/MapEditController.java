package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.NavBlock;
import co.edu.uco.ucomap.model.ScanEdit;
import co.edu.uco.ucomap.service.MapEditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/** Edición del modelo 3D y de la malla de navegación desde el admin (solo ADMIN, como todo /api/navigation/**). */
@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class MapEditController {

    private final MapEditService mapEditService;

    @GetMapping("/scan-edits")
    public ResponseEntity<ApiSuccess<List<ScanEdit>>> getScanEdits() {
        return ResponseEntity.ok(ApiSuccess.of(mapEditService.findAllScanEdits()));
    }

    @PostMapping("/scan-edits")
    public ResponseEntity<ApiSuccess<ScanEdit>> createScanEdit(@RequestBody ScanEdit edit, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(mapEditService.createScanEdit(edit, principal != null ? principal.getName() : null)));
    }

    @DeleteMapping("/scan-edits/{id}")
    public ResponseEntity<ApiSuccess<Void>> deleteScanEdit(@PathVariable String id) {
        mapEditService.deleteScanEdit(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }

    @GetMapping("/blocks")
    public ResponseEntity<ApiSuccess<List<NavBlock>>> getBlocks() {
        return ResponseEntity.ok(ApiSuccess.of(mapEditService.findAllBlocks()));
    }

    @PostMapping("/blocks")
    public ResponseEntity<ApiSuccess<NavBlock>> createBlock(@RequestBody NavBlock block, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(mapEditService.createBlock(block, principal != null ? principal.getName() : null)));
    }

    @DeleteMapping("/blocks/{id}")
    public ResponseEntity<ApiSuccess<Void>> deleteBlock(@PathVariable String id) {
        mapEditService.deleteBlock(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
