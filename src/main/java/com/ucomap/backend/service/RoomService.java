package com.ucomap.backend.service;

import com.ucomap.backend.model.Room;
import com.ucomap.backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
        // Soft delete — no borrar físicamente
        room.setActive(false);
        roomRepository.save(room);
    }
}

