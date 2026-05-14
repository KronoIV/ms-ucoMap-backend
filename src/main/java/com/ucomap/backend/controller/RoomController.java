package com.ucomap.backend.controller;

import com.ucomap.backend.model.Room;
import com.ucomap.backend.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CRUD de salones.
 *
 * GET    /api/rooms              — todos los activos
 * GET    /api/rooms?category=CO  — filtrar por categoría
 * GET    /api/rooms/{roomId}     — salón específico
 * POST   /api/rooms              — crear (requiere objeto completo)
 * PUT    /api/rooms/{roomId}     — reemplazar completo
 * PATCH  /api/rooms/{roomId}     — actualizar campos parciales: { "active": false }
 * DELETE /api/rooms/{roomId}     — soft delete
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<Room>> getAll(
            @RequestParam(required = false) String category) {
        List<Room> rooms = (category != null && !category.isBlank())
                ? roomService.findByCategory(category)
                : roomService.findAll();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getOne(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.findByRoomId(roomId));
    }

    @PostMapping
    public ResponseEntity<Room> create(@Valid @RequestBody Room room) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.create(room));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<Room> update(
            @PathVariable String roomId,
            @Valid @RequestBody Room room) {
        return ResponseEntity.ok(roomService.update(roomId, room));
    }

    /**
     * Actualiza solo los campos enviados en el body.
     * Ejemplos de uso:
     *   { "active": false }
     *   { "name": "Nuevo nombre", "category": "EDC" }
     *   { "stateId": "co205", "active": true }
     */
    @PatchMapping("/{roomId}")
    public ResponseEntity<Room> patch(
            @PathVariable String roomId,
            @RequestBody Map<String, Object> fields) {
        return ResponseEntity.ok(roomService.patch(roomId, fields));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable String roomId) {
        roomService.delete(roomId);
        return ResponseEntity.noContent().build();
    }
}

