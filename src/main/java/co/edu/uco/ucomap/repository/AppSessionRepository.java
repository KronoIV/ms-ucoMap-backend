package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.AppSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppSessionRepository extends MongoRepository<AppSession, String> {

    Optional<AppSession> findBySessionId(String sessionId);
}
