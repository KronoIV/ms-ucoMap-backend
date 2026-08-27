package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.Cafeteria;
import co.edu.uco.ucomap.service.CafeteriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD de cafeterias.
 *
 * GET    /api/cafeterias              — todas
 * GET    /api/cafeterias/{cafeteriaId} — una especifica
 * POST   /api/cafeterias              — crear
 * PUT    /api/cafeterias/{cafeteriaId} — reemplazar
 * PATCH  /api/cafeterias/{cafeteriaId} — campos parciales
 * DELETE /api/cafeterias/{cafeteriaId} — eliminar
 */
@RestController
@RequestMapping("/api/cafeterias")
@RequiredArgsConstructor
public class CafeteriaController {

    private final CafeteriaService cafeteriaService;

    @GetMapping
    public ResponseEntity<ApiSuccess<List<Cafeteria>>> getAll() {
        return ResponseEntity.ok(ApiSuccess.of(cafeteriaService.findAll()));
    }

    @GetMapping("/{cafeteriaId}")
    public ResponseEntity<ApiSuccess<Cafeteria>> getOne(@PathVariable String cafeteriaId) {
        return ResponseEntity.ok(ApiSuccess.of(cafeteriaService.findByCafeteriaId(cafeteriaId)));
    }

    @PostMapping
    public ResponseEntity<ApiSuccess<Cafeteria>> create(@Valid @RequestBody Cafeteria cafeteria) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(cafeteriaService.create(cafeteria)));
    }

    @PutMapping("/{cafeteriaId}")
    public ResponseEntity<ApiSuccess<Cafeteria>> update(
            @PathVariable String cafeteriaId,
            @Valid @RequestBody Cafeteria cafeteria) {
        return ResponseEntity.ok(ApiSuccess.of(cafeteriaService.update(cafeteriaId, cafeteria)));
    }

    @PatchMapping("/{cafeteriaId}")
    public ResponseEntity<ApiSuccess<Cafeteria>> patch(
            @PathVariable String cafeteriaId,
            @RequestBody Map<String, Object> fields) {
        return ResponseEntity.ok(ApiSuccess.of(cafeteriaService.patch(cafeteriaId, fields)));
    }

    @DeleteMapping("/{cafeteriaId}")
    public ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String cafeteriaId) {
        cafeteriaService.delete(cafeteriaId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
