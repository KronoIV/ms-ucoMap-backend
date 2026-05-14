package com.ucomap.backend.repository;

import com.ucomap.backend.model.GraphEdge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphEdgeRepository extends MongoRepository<GraphEdge, String> {

    List<GraphEdge> findByActiveTrue();

    List<GraphEdge> findByNodeAOrNodeB(String nodeA, String nodeB);
}

