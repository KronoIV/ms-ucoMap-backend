package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Navmesh binario (formato recast-navigation) generado desde el admin. Documento único "current". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nav_meshes")
public class NavMeshData {

    public static final String CURRENT_ID = "current";

    @Id
    private String id;

    private byte[] data;

    private Instant updatedAt;

    private String updatedBy;
}
