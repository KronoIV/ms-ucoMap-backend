package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavBlock;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NavBlockRepository extends MongoRepository<NavBlock, String> {
}
