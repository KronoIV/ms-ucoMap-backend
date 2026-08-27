package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserIdAndUsedFalseAndRevokedFalse(String userId);

    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(String userId);
}
