package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.ArPoint;
import co.edu.uco.ucomap.model.Room;
import co.edu.uco.ucomap.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.edu.uco.ucomap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    /** Admin: devuelve TODOS los salones (activos e inactivos). */
    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    /** Admin: filtra por categoria sin importar estado active. */
    public List<Room> findByCategory(String category) {
        return roomRepository.findByCategory(category);
    }

    /** Campus: solo los salones activos (usado internamente por CampusService). */
    public List<Room> findAllActive() {
        return roomRepository.findByActiveTrue();
    }

    public Room findByRoomId(String roomId) {
        return roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    public Room create(Room room) {
        if (roomRepository.existsByRoomId(room.getRoomId())) {
            log.warn("Salón duplicado — roomId={}", room.getRoomId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        Room saved = roomRepository.save(room);
        log.info("Salón creado — roomId={} category={}", saved.getRoomId(), saved.getCategory());
        return saved;
    }

    public Room update(String roomId, Room updated) {
        Room existing = findByRoomId(roomId);
        existing.setName(updated.getName());
        existing.setCategory(updated.getCategory());
        existing.setStateId(updated.getStateId());
        existing.setModelUrl(updated.getModelUrl());
        existing.setArPosition(updated.getArPosition());
        existing.setFloor(updated.getFloor());
        existing.setActive(updated.isActive());
        Room saved = roomRepository.save(existing);
        log.info("Salón actualizado — roomId={}", roomId);
        return saved;
    }

    public void delete(String roomId) {
        Room room = findByRoomId(roomId);
        roomRepository.delete(room);
        log.info("Salón eliminado — roomId={}", roomId);
    }

    /**
     * Actualiza solo los campos presentes en el mapa (PATCH semantics).
     * Campos soportados: name, category, stateId, modelUrl, active.
     * Los campos ausentes en el mapa NO se modifican.
     */
    public Room patch(String roomId, Map<String, Object> fields) {
        Room room = findByRoomId(roomId);

        if (fields.containsKey("name"))     room.setName((String) fields.get("name"));
        if (fields.containsKey("category")) room.setCategory((String) fields.get("category"));
        if (fields.containsKey("stateId"))  room.setStateId((String) fields.get("stateId"));
        if (fields.containsKey("modelUrl")) room.setModelUrl((String) fields.get("modelUrl"));
        if (fields.containsKey("active"))   room.setActive((Boolean) fields.get("active"));
        if (fields.containsKey("arPosition")) room.setArPosition(toArPoint(fields.get("arPosition")));
        if (fields.containsKey("floor"))    room.setFloor(toFloor(fields.get("floor")));

        Room saved = roomRepository.save(room);
        log.info("Salón modificado — roomId={} campos={}", roomId, fields.keySet());
        return saved;
    }

    private Integer toFloor(Object value) {
        if (value == null) return null;
        if (value instanceof Number n && n.doubleValue() == n.intValue() && n.intValue() >= -5 && n.intValue() <= 60) {
            return n.intValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
    }

    private ArPoint toArPoint(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> m
                && m.get("x") instanceof Number x
                && m.get("y") instanceof Number y
                && m.get("z") instanceof Number z) {
            return new ArPoint(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getMessage());
    }
}

