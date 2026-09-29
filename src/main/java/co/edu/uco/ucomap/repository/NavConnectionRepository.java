package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavConnection;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NavConnectionRepository extends MongoRepository<NavConnection, String> {
}
