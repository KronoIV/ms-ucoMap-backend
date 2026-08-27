package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.Building;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuildingRepository extends MongoRepository<Building, String> {

    Optional<Building> findByCategory(String category);

    boolean existsByCategoryIgnoreCase(String category);

    List<Building> findByActiveTrue();
}

