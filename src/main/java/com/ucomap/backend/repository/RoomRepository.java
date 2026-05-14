package com.ucomap.backend.repository;

import com.ucomap.backend.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    Optional<Room> findByRoomId(String roomId);

    List<Room> findByCategory(String category);

    List<Room> findByActiveTrue();

    List<Room> findByCategoryAndActiveTrue(String category);

    boolean existsByRoomId(String roomId);
}

