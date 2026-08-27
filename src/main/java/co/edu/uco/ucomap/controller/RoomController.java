package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.model.Room;
import co.edu.uco.ucomap.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<ApiSuccess<List<Room>>> getAll(
            @RequestParam(required = false) String category) {
        List<Room> rooms = (category != null && !category.isBlank())
                ? roomService.findByCategory(category)
                : roomService.findAll();
        return ResponseEntity.ok(ApiSuccess.of(rooms));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiSuccess<Room>> getOne(@PathVariable String roomId) {
        return ResponseEntity.ok(ApiSuccess.of(roomService.findByRoomId(roomId)));
    }

    @PostMapping
    public ResponseEntity<ApiSuccess<Room>> create(@Valid @RequestBody Room room) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(roomService.create(room)));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<ApiSuccess<Room>> update(
            @PathVariable String roomId,
            @Valid @RequestBody Room room) {
        return ResponseEntity.ok(ApiSuccess.of(roomService.update(roomId, room)));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiSuccess<Room>> patch(
            @PathVariable String roomId,
            @RequestBody Map<String, Object> fields) {
        return ResponseEntity.ok(ApiSuccess.of(roomService.patch(roomId, fields)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String roomId) {
        roomService.delete(roomId);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}

