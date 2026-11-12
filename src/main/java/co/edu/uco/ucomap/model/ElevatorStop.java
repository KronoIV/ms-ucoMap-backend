package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Parada de un ascensor en un piso: el punto sobre el piso, frente a su puerta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElevatorStop {

    /** Piso, con la misma numeración de los salones (negativo = sótano). */
    private Integer floor;

    /** Texto del botón en la cabina cuando no es el número del piso (PB, S1…); null = el número. */
    private String button;

    /** Frente a la puerta, sobre el piso, en el espacio del mapa MultiSet. */
    private ArPoint point;
}
