package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavMeshData;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NavMeshRepository extends MongoRepository<NavMeshData, String> {
}
