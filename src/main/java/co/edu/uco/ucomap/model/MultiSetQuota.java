package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Plan de MultiSet y estado del apagado automático de la guía con cámara.
 * Colección MongoDB: multiset_quota (un solo documento).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "multiset_quota")
public class MultiSetQuota {

    public static final String ID = "quota";

    @Id
    private String id;

    /** FREE | LITE | PLUS | CUSTOM: solo para el panel. */
    private String plan;

    /** Llamadas incluidas en el plan; 0 = sin configurar (no se apaga nada). */
    private long limit;

    /** true: el cupo se renueva cada mes el día {@link #resetDay}; false: cupo total (plan gratis). */
    private boolean monthly;

    private int resetDay;

    /** Porcentaje del cupo a partir del cual se apaga la guía con cámara. */
    private double autoDisablePercent;

    /** Uso que mostraba el portal de MultiSet en {@link #baselineAt}; la estimación cuenta desde ahí. */
    private Long baselineUsed;
    private Instant baselineAt;

    /** Cuándo se apagó la guía por cupo; null si no está apagada por esto. */
    private Instant autoDisabledAt;

    private Instant updatedAt;

    public static MultiSetQuota defaults() {
        return MultiSetQuota.builder()
                .id(ID)
                .plan("CUSTOM")
                .limit(0)
                .monthly(true)
                .resetDay(1)
                .autoDisablePercent(98)
                .build();
    }
}
