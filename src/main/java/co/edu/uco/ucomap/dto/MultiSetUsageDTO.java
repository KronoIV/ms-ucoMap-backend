package co.edu.uco.ucomap.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Uso estimado del plan de MultiSet (sin consultar la API de MultiSet). */
public class MultiSetUsageDTO {

    /**
     * @param level unconfigured | ok | warning | critical | disabled
     */
    public record Response(
            String plan,
            long limit,
            boolean monthly,
            int resetDay,
            double autoDisablePercent,
            Instant periodStart,
            Instant periodEnd,
            long used,
            double percent,
            Long baselineUsed,
            Instant baselineAt,
            long appCalls,
            long backendCalls,
            boolean arEnabled,
            Instant autoDisabledAt,
            String level
    ) {}

    /**
     * @param portalUsed uso que muestra ahora el portal de MultiSet; null = mantener el anterior
     */
    public record Request(
            @NotNull @Pattern(regexp = "FREE|LITE|PLUS|CUSTOM") String plan,
            @Min(0) @Max(100_000_000) long limit,
            boolean monthly,
            @Min(1) @Max(28) int resetDay,
            @DecimalMin("50") @DecimalMax("100") double autoDisablePercent,
            @Min(0) @Max(100_000_000) Long portalUsed
    ) {}
}
