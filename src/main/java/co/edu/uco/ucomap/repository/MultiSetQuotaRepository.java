package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.MultiSetQuota;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MultiSetQuotaRepository extends MongoRepository<MultiSetQuota, String> {
}
