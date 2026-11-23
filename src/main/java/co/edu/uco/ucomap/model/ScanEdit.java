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
 * Limpieza del modelo 3D: geometría del escaneo (personas, muebles, restos flotantes, escaleras deformadas…) que el
 * admin quitó y que ya no entra al generar el navmesh.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "scan_edits")
public class ScanEdit {

    @Id
    private String id;

    private String label;

    private List<ScanEditMesh> meshes;

    /** Centro de lo quitado, para ubicarlo en el visor. */
    private ArPoint center;

    private int triangleCount;

    private Instant createdAt;

    private String createdBy;
}
