package co.edu.uco.ucomap.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rendimiento medido en el teléfono durante la sesión AR de un recorrido (llega en el reporte final).
 * Sirve para validar en campo los umbrales de compatibilidad de la guía con cámara.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArPerfStats {

    /** Mediana y percentil 10 de los fps de cada segundo, sin el arranque. */
    @PositiveOrZero @Max(240)
    private Double fpsMedian;

    @PositiveOrZero @Max(240)
    private Double fpsP10;

    /** Mediana del procesamiento de una captura para el VPS (píxeles → JPEG, ms). */
    @PositiveOrZero @Max(60_000)
    private Double captureMsMedian;

    @PositiveOrZero @Max(100_000)
    private Integer captures;

    /** Segundos medidos de la sesión AR. */
    @PositiveOrZero @Max(21_600)
    private Integer seconds;

    /** Por qué la app cerró la AR por rendimiento (fps o capture); null si no la cerró. */
    @Pattern(regexp = "^(fps|capture)$")
    private String guard;
}
