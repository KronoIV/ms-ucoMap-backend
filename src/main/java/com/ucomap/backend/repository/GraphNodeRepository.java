package com.ucomap.backend.repository;

import com.ucomap.backend.model.GraphNode;
import com.ucomap.backend.model.NodeType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphNodeRepository extends MongoRepository<GraphNode, String> {

    List<GraphNode> findByNodeType(NodeType nodeType);

    List<GraphNode> findByActiveTrue();
}

