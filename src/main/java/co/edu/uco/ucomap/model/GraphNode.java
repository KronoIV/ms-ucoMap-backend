package co.edu.uco.ucomap.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** Tipo del nodo: BUILDING, ENTRANCE, DOOR, WAYPOINT o POI. */
    @Indexed
    private NodeType nodeType;

    /**
     * Solo POI: clave de su clase ("AUDITORIO", "BANOS"…). Es libre a propósito: el catálogo con nombre e
     * icono vive en la app y el panel (poi-catalog.ts), así una clase nueva no requiere cambiar el backend.
     */
    private String poiType;

    /** Solo POI: edificio (buildingId) que lo contiene; la ruta llega a ese edificio. Vacío = al aire libre. */
    private String buildingId;

    /** Solo DOOR (piso por el que se sale) y POI dentro de un edificio. Negativo = sótano; null = sin definir. */
    @Min(-5)
    @Max(60)
    private Integer floor;

    /** Si el nodo está activo en el sistema de navegación. */
    @Builder.Default
    private boolean active = true;
}

