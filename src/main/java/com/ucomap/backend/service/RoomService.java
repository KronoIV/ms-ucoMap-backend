package com.ucomap.backend.service;

import com.ucomap.backend.model.Room;
import com.ucomap.backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    public List<Room> findAll() {
        return roomRepository.findByActiveTrue();
    }

    public List<Room> findByCategory(String category) {
        return roomRepository.findByCategoryAndActiveTrue(category);
    }

    public Room findByRoomId(String roomId) {
        return roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Salón no encontrado: " + roomId));
    }

    public Room create(Room room) {
        if (roomRepository.existsByRoomId(room.getRoomId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un salón con roomId: " + room.getRoomId());
        }
        return roomRepository.save(room);
    }

    public Room update(String roomId, Room updated) {
        Room existing = findByRoomId(roomId);
        existing.setName(updated.getName());
        existing.setCategory(updated.getCategory());
        existing.setStateId(updated.getStateId());
        existing.setModelUrl(updated.getModelUrl());
        existing.setActive(updated.isActive());
        return roomRepository.save(existing);
    }

    public void delete(String roomId) {
        Room room = findByRoomId(roomId);
        room.setActive(false);
        roomRepository.save(room);
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

        return roomRepository.save(room);
    }
}

