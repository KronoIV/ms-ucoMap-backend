package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Ascensor: una parada por piso, frente a su puerta. Su interior no está escaneado, así que no entra al navmesh: la app
 * guía hasta la puerta, dice qué botón presionar y vuelve a ubicar al usuario cuando sale.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nav_elevators")
public class NavElevator {

    @Id
    private String id;

    private String label;

    /** Edificio: la categoría de sus salones (M, CO…). Vacío = sirve a cualquier destino cercano. */
    private String building;

    /** Ordenadas por piso, de abajo hacia arriba. */
    private List<ElevatorStop> stops;
}
