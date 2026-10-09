package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.CampusEvent;
import co.edu.uco.ucomap.service.CampusEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Eventos del campus.
 *
 * GET    /api/events/active  — público: los que la app muestra ahora
 * GET    /api/events         — todos (panel)
 * POST   /api/events         — crear
 * PUT    /api/events/{id}    — actualizar
 * DELETE /api/events/{id}    — eliminar
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class CampusEventController {

    private final CampusEventService eventService;

    @GetMapping("/active")
    public ResponseEntity<ApiSuccess<List<CampusEvent>>> getActive() {
        // Depende de la hora: sin caché para que un evento que termina desaparezca a tiempo
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiSuccess.of(eventService.visibleNow()));
    }

    @GetMapping
    public ResponseEntity<ApiSuccess<List<CampusEvent>>> getAll() {
        return ResponseEntity.ok(ApiSuccess.of(eventService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiSuccess<CampusEvent>> create(@Valid @RequestBody CampusEvent event) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccess.of(eventService.create(event)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccess<CampusEvent>> update(@PathVariable String id, @Valid @RequestBody CampusEvent event) {
        return ResponseEntity.ok(ApiSuccess.of(eventService.update(id, event)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String id) {
        eventService.delete(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
