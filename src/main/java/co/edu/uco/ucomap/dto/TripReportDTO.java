package co.edu.uco.ucomap.dto;

import co.edu.uco.ucomap.model.TransitionStats;
import co.edu.uco.ucomap.model.TripStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Estado de un recorrido enviado por la app (público, sin autenticación).
 * Se envía al iniciar (IN_PROGRESS) y al terminar (ARRIVED / ABANDONED) con todos los datos,
 * así el cierre crea el recorrido aunque el aviso de inicio se haya perdido.
 */
public record TripReportDTO(

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$")
        String tripId,

        @NotBlank
        @Size(max = 64)
        String deviceId,

        @NotNull
        TripStatus status,

        @Size(max = 64)
        String roomId,

        @NotBlank
        @Size(max = 120)
        String roomName,

        @Size(max = 60)
        String building,

        @Pattern(regexp = "^(indoor|outdoor|ask)$")
        String startMode,

        @PositiveOrZero @Max(100_000)
        Double startDistanceM,

        @PositiveOrZero @Max(100_000)
        Double startAccuracyM,

        @Size(max = 40)
        @Pattern(regexp = "^[a-z-]*$")
        String endReason,

        // Máximo 6 h: ningún recorrido real dentro del campus dura más
        @PositiveOrZero @Max(21_600_000)
        Long durationMs,

        @PositiveOrZero @Max(21_600_000)
        Long buildingReachedMs,

        @PositiveOrZero @Max(21_600_000)
        Long localizedMs,

        @PositiveOrZero @Max(100_000)
        Double outdoorRouteM,

        @PositiveOrZero @Max(100_000)
        Double indoorRouteM,

        @PositiveOrZero @Max(1000)
        Integer modeSwitches,

        @PositiveOrZero @Max(1000)
        Integer vpsFailures,

        Boolean usedAR,

        // Punto de partida redondeado por la app a 4 decimales (~11 m); solo si hay permiso de ubicación
        @DecimalMin("-90") @DecimalMax("90")
        Double startLat,

        @DecimalMin("-180") @DecimalMax("180")
        Double startLng,

        // Cómo se decidió el paso a interior (solo en el reporte final)
        @Valid
        TransitionStats transition
) {
    /** Reporte de versiones anteriores de la app (sin punto de partida). */
    public TripReportDTO(String tripId, String deviceId, TripStatus status, String roomId, String roomName,
                         String building, String startMode, Double startDistanceM, Double startAccuracyM,
                         String endReason, Long durationMs, Long buildingReachedMs, Long localizedMs,
                         Double outdoorRouteM, Double indoorRouteM, Integer modeSwitches, Integer vpsFailures,
                         Boolean usedAR) {
        this(tripId, deviceId, status, roomId, roomName, building, startMode, startDistanceM, startAccuracyM,
                endReason, durationMs, buildingReachedMs, localizedMs, outdoorRouteM, indoorRouteM, modeSwitches,
                vpsFailures, usedAR, null, null);
    }

    /** Reporte de versiones anteriores de la app (sin datos de la transición). */
    public TripReportDTO(String tripId, String deviceId, TripStatus status, String roomId, String roomName,
                         String building, String startMode, Double startDistanceM, Double startAccuracyM,
                         String endReason, Long durationMs, Long buildingReachedMs, Long localizedMs,
                         Double outdoorRouteM, Double indoorRouteM, Integer modeSwitches, Integer vpsFailures,
                         Boolean usedAR, Double startLat, Double startLng) {
        this(tripId, deviceId, status, roomId, roomName, building, startMode, startDistanceM, startAccuracyM,
                endReason, durationMs, buildingReachedMs, localizedMs, outdoorRouteM, indoorRouteM, modeSwitches,
                vpsFailures, usedAR, startLat, startLng, null);
    }
}
