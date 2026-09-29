package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Enlace entre dos puntos del navmesh que no están unidos por suelo (escaleras, desniveles). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nav_connections")
public class NavConnection {

    @Id
    private String id;

    private String label;

    /** Grupo/edificio al que pertenece (COLEGIO, EDC, M…), solo informativo. */
    private String group;

    private ArPoint start;

    private ArPoint end;

    @Builder.Default
    private double radius = 1;

    @Builder.Default
    private boolean bidirectional = true;
}
