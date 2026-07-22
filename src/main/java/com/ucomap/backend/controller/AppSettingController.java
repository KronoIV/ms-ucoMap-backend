package com.ucomap.backend.controller;

import com.ucomap.backend.dto.AppSettingDTO;
import com.ucomap.backend.service.AppSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestión de parámetros de configuración de la aplicación móvil UCO Map.
 *
 * GET    /api/settings          — todos los parámetros activos (público — lo consume la app)
 * GET    /api/settings/{key}    — parámetro específico
 * POST   /api/settings          — crear parámetro (panel admin)
 * PUT    /api/settings/{key}    — actualizar parámetro (panel admin)
 * DELETE /api/settings/{key}    — eliminar parámetro (panel admin)
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final AppSettingService settingService;

    // ── Lectura (pública — necesaria para UCO Map sin autenticación) ──────────

    @GetMapping
    public ResponseEntity<List<AppSettingDTO.Response>> getAll() {
        return ResponseEntity.ok(settingService.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<AppSettingDTO.Response> getByKey(@PathVariable String key) {
        return ResponseEntity.ok(settingService.findByKey(key));
    }

    // ── Escritura (panel de administración) ───────────────────────────────────

    @PostMapping
    public ResponseEntity<AppSettingDTO.Response> create(
            @Valid @RequestBody AppSettingDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settingService.create(request));
    }

    @PutMapping("/{key}")
    public ResponseEntity<AppSettingDTO.Response> update(
            @PathVariable String key,
            @Valid @RequestBody AppSettingDTO.Request request) {
        return ResponseEntity.ok(settingService.update(key, request));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        settingService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
