package com.ucomap.backend.controller;

import com.ucomap.backend.model.Cafeteria;
import com.ucomap.backend.service.CafeteriaService;
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
    public ResponseEntity<List<Cafeteria>> getAll() {
        return ResponseEntity.ok(cafeteriaService.findAll());
    }

    @GetMapping("/{cafeteriaId}")
    public ResponseEntity<Cafeteria> getOne(@PathVariable String cafeteriaId) {
        return ResponseEntity.ok(cafeteriaService.findByCafeteriaId(cafeteriaId));
    }

    @PostMapping
    public ResponseEntity<Cafeteria> create(@Valid @RequestBody Cafeteria cafeteria) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cafeteriaService.create(cafeteria));
    }

    @PutMapping("/{cafeteriaId}")
    public ResponseEntity<Cafeteria> update(
            @PathVariable String cafeteriaId,
            @Valid @RequestBody Cafeteria cafeteria) {
        return ResponseEntity.ok(cafeteriaService.update(cafeteriaId, cafeteria));
    }

    @PatchMapping("/{cafeteriaId}")
    public ResponseEntity<Cafeteria> patch(
            @PathVariable String cafeteriaId,
            @RequestBody Map<String, Object> fields) {
        return ResponseEntity.ok(cafeteriaService.patch(cafeteriaId, fields));
    }

    @DeleteMapping("/{cafeteriaId}")
    public ResponseEntity<Void> delete(@PathVariable String cafeteriaId) {
        cafeteriaService.delete(cafeteriaId);
        return ResponseEntity.noContent().build();
    }
}
