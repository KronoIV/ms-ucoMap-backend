package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Posición en metros dentro del espacio del mapa del VPS (el mismo de los escaneos del campus). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArPoint {
    private double x;
    private double y;
    private double z;
}
