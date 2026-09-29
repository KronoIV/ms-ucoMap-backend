package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Posición en metros dentro del mapa MultiSet (mismo espacio que la escena de Mattercraft). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArPoint {
    private double x;
    private double y;
    private double z;
}
