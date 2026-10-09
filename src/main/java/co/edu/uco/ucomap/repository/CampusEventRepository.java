package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.CampusEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CampusEventRepository extends MongoRepository<CampusEvent, String> {

    List<CampusEvent> findAllByOrderByStartsAtDesc();

    List<CampusEvent> findByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(Instant startedBy, Instant endsAfter);
}
