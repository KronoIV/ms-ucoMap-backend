package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/** Polígono de suelo caminable que se suma al escaneo al generar el navmesh (huecos que MultiSet no capturó). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nav_patches")
public class NavPatch {

    @Id
    private String id;

    private String label;

    /** Vértices en orden sobre el piso, en el espacio del mapa MultiSet. */
    private List<ArPoint> points;
}
