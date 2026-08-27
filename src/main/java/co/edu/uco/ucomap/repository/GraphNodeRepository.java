package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraphNodeRepository extends MongoRepository<GraphNode, String> {

    List<GraphNode> findByNodeType(NodeType nodeType);

    List<GraphNode> findByActiveTrue();
}

