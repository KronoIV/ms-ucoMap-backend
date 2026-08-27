package co.edu.uco.ucomap.repository;
import co.edu.uco.ucomap.model.Cafeteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface CafeteriaRepository extends MongoRepository<Cafeteria, String> {
    Optional<Cafeteria> findByCafeteriaId(String cafeteriaId);
    List<Cafeteria> findByActiveTrue();
    boolean existsByCafeteriaId(String cafeteriaId);
}
