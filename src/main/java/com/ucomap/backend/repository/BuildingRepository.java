package com.ucomap.backend.repository;

import com.ucomap.backend.model.Building;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends MongoRepository<Building, String> {

    Optional<Building> findByCategory(String category);

    List<Building> findByActiveTrue();
}

