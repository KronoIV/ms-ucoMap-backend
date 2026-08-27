package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.MapConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MapConfigRepository extends MongoRepository<MapConfig, String> {
    // Singleton: siempre se accede con id "campus_config"
}

