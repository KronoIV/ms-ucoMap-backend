package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.TripStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NavigationTripRepository extends MongoRepository<NavigationTrip, String> {

    Optional<NavigationTrip> findByTripId(String tripId);

    List<NavigationTrip> findByStatus(TripStatus status);

    List<NavigationTrip> findAllByOrderByStartedAtDesc();
}
