package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.NavElevator;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NavElevatorRepository extends MongoRepository<NavElevator, String> {
}
