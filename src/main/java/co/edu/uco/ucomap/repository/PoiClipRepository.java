package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.PoiClip;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoiClipRepository extends MongoRepository<PoiClip, String> {

    List<PoiClip> findByActiveTrue();
}

