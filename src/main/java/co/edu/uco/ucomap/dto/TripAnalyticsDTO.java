package co.edu.uco.ucomap.dto;

import java.time.LocalDate;
import java.util.List;

/** Análisis de recorridos (GET /api/analytics/trips). Tiempos en ms, distancias en m. */
public record TripAnalyticsDTO(
        AnalyticsOverviewDTO.Period period,
        Summary summary,
        List<DestinationRow> destinations,
        List<OriginRow> origins,
        List<RouteRow> routes,
        /** Etapa en la que se abandonó: outdoor, in-building, locating, indoor-route, changed-destination, unknown. */
        List<AnalyticsOverviewDTO.Count> abandonStages,
        List<AnalyticsOverviewDTO.Count> abandonReasons,
        List<AnalyticsOverviewDTO.Count> startModes,
        List<AnalyticsOverviewDTO.Bucket> startDistances,
        List<TripDay> daily,
        long[][] byWeekdayHour,
        List<HeatPoint> originPoints,
        List<BuildingPoint> destinationPoints,
        List<String> buildings
) {

    public record Summary(
            AnalyticsOverviewDTO.Metric total,
            AnalyticsOverviewDTO.Metric arrived,
            AnalyticsOverviewDTO.Metric abandoned,
            long inProgress,
            AnalyticsOverviewDTO.Metric completionRate,
            Double medianDurationMs,
            Double p75DurationMs,
            Double p90DurationMs,
            Double avgDurationMs,
            Double medianAbandonMs,
            Double avgRouteM,
            Double arUsageRate,
            Double medianLocalizedMs,
            Double avgVpsFailures,
            /** Recorridos con punto de partida (necesario para orígenes y mapa). */
            long withOrigin
    ) {}

    public record DestinationRow(String building, String buildingLabel, String roomName, long total, long arrived,
                                 long abandoned, Double completionRate, Double medianDurationMs,
                                 Double avgDurationMs, Double avgRouteM) {}

    public record OriginRow(String label, long total, long arrived) {}

    public record RouteRow(String origin, String destination, String building, long total, long arrived,
                           Double medianDurationMs, Double avgRouteM) {}

    public record TripDay(LocalDate date, long total, long arrived, long abandoned) {}

    public record HeatPoint(double lat, double lng, long weight) {}

    public record BuildingPoint(String building, String label, double lat, double lng, long total, long arrived) {}
}
