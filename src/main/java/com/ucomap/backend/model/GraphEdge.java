package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Arista no dirigida del grafo de caminos del campus.
 * Colección MongoDB: graph_edges
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "graph_edges")
public class GraphEdge {

    @Id
    private String id;

    /** ID del nodo origen (debe existir en graph_nodes). */
    @Indexed
    private String nodeA;

    /** ID del nodo destino (debe existir en graph_nodes). */
    @Indexed
    private String nodeB;

    /** Si la arista está activa (permite deshabilitar rutas sin borrarlas). */
    @Builder.Default
    private boolean active = true;
}

