package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.ScanEdit;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScanEditRepository extends MongoRepository<ScanEdit, String> {
}
