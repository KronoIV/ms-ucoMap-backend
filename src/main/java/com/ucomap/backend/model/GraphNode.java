package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Nodo del grafo de caminos del campus UCO.
 * Colección MongoDB: graph_nodes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "graph_nodes")
public class GraphNode {

    /** ID legible del nodo: "EDC", "E1", "P1", etc. */
    @Id
    private String nodeId;

    /** Coordenadas GPS reales del nodo. */
    private GpsPoint gps;

    /** Posición en el render isométrico 500×800. */
    private PixelPoint pixel;

    /** Etiqueta visible (solo para BUILDING y ENTRANCE). */
    private String label;

    /** Tipo del nodo: BUILDING, ENTRANCE o WAYPOINT. */
    @Indexed
    private NodeType nodeType;

    /** Si el nodo está activo en el sistema de navegación. */
    @Builder.Default
    private boolean active = true;
}

