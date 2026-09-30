package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.DeviceSession;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceSessionRepository extends MongoRepository<DeviceSession, String> {

    Optional<DeviceSession> findByDeviceId(String deviceId);

    long countByPlatform(String platform);

    long countByLastSeenGreaterThanEqual(Instant since);

    @Aggregation({
            "{ $group: { _id: '$platform', devices: { $sum: 1 }, sessions: { $sum: '$sessionCount' } } }",
            "{ $project: { _id: 0, platform: '$_id', devices: 1, sessions: 1 } }"
    })
    List<PlatformStats> aggregateByPlatform();

    record PlatformStats(String platform, long devices, long sessions) {}
}

