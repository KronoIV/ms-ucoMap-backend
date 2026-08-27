package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.DeviceSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceSessionRepository extends MongoRepository<DeviceSession, String> {

    Optional<DeviceSession> findByDeviceId(String deviceId);

    long countByPlatform(String platform);

    List<DeviceSession> findAllByOrderByLastSeenDesc();
}

