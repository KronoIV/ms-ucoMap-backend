package co.edu.uco.ucomap.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cómo decidió la app el paso exterior → interior en un recorrido (motor de transición por niveles de confianza).
 * Sirve para medir aciertos, cambios prematuros y rechazos. Llega dentro del reporte final del recorrido.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionStats {

    /** start, user, fallback, auto-door, confirmed-gps-degraded… (modo y evidencia del primer paso a interior). */
    @Size(max = 40)
    @Pattern(regexp = "^[a-z-]*$")
    private String trigger;

    /** Puntuación de confianza 0–100 al cambiar. */
    @PositiveOrZero @Max(100)
    private Integer score;

    /** Distancia a la entrada y precisión GPS al cambiar (m). */
    @PositiveOrZero @Max(100_000)
    private Double distanceM;

    @PositiveOrZero @Max(100_000)
    private Double accuracyM;

    /** Desde que empezó a acercarse a la entrada hasta el cambio (ms). */
    @PositiveOrZero @Max(21_600_000)
    private Long approachMs;

    /** Desde el cambio a interior hasta la primera localización VPS (ms). */
    @PositiveOrZero @Max(21_600_000)
    private Long switchToVpsMs;

    /** El token VPS ya estaba listo al abrir la cámara. */
    private Boolean prewarmed;

    /** Se acercó a la entrada y se alejó sin entrar. */
    @PositiveOrZero @Max(1000)
    private Integer cancellations;

    /** Rechazó el cambio propuesto. */
    @PositiveOrZero @Max(1000)
    private Integer rejections;

    @PositiveOrZero @Max(1000)
    private Integer returnsToOutdoor;

    /** Cambios automáticos a interior revertidos sin llegar a ubicarse con el VPS. */
    @PositiveOrZero @Max(1000)
    private Integer falseIndoor;
}
