package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Triángulos quitados de una malla de un mapa del escaneo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanEditMesh {

    /** Nombre del mapa MultiSet. */
    private String map;

    /** Posición de la malla dentro del GLB del mapa (orden de recorrido). */
    private int mesh;

    /** Triángulos que tenía la malla al editarla: si el mapa cambia, la edición deja de aplicarse. */
    private int triangles;

    /** Pares [inicio, cantidad] de triángulos quitados, ordenados y sin solaparse. */
    private List<Integer> ranges;
}
