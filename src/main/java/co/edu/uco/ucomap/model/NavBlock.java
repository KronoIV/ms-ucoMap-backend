package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Bloqueo manual del navmesh: se aplica en cada generación, así que sobrevive a regenerar tras editar el modelo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nav_blocks")
public class NavBlock {

    public enum Kind {
        /** Zona no transitable: polígonos convexos (los del navmesh que se seleccionaron). */
        AREA,
        /** Corte de paso: una línea sobre el piso que las rutas no cruzan. */
        BARRIER,
        /** Escalera o conexión desactivada. */
        CONNECTION
    }

    @Id
    private String id;

    private String label;

    private Kind kind;

    /** AREA: polígonos convexos en el espacio del mapa. */
    private List<List<ArPoint>> polygons;

    /** BARRIER: puntos de la línea sobre el piso. */
    private List<ArPoint> line;

    /** CONNECTION: id de la NavConnection. */
    private String connectionId;

    private Instant createdAt;

    private String createdBy;
}
