package co.edu.uco.ucomap.dto;

import java.util.List;

/** Métricas de recorridos calculadas en el servidor para el rango filtrado. */
public record TripSummaryDTO(
        long total,
        long finished,
        long arrived,
        long inProgress,
        Double avgArrivalMs,
        Double medianArrivalMs,
        Double avgLocalizedMs,
        Double avgVpsFailures,
        List<DestinationStats> byDestination,
        List<ReasonCount> abandonReasons,
        List<String> buildings
) {
    public record DestinationStats(String building, String roomName, long total, long finished, long arrived,
                                   Double avgArrivalMs, Double medianArrivalMs) {}

    public record ReasonCount(String reason, long count) {}
}
