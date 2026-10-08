package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavPatch;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NavPatchRepository extends MongoRepository<NavPatch, String> {
}
