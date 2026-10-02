package co.edu.uco.ucomap.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Analítica general del panel (GET /api/analytics).
 * Los tiempos van en ms; los días y horas en la zona horaria del periodo.
 */
public record AnalyticsOverviewDTO(
        Period period,
        Summary summary,
        List<DayPoint> daily,
        /** Visitas iniciadas por día de la semana (0 = lunes) y hora (0–23). */
        long[][] sessionsByWeekdayHour,
        Durations sessionDurations,
        Users users,
        Devices devices,
        List<FeatureUsage> features,
        Permissions permissions,
        List<Problem> problems,
        Coverage coverage
) {

    public record Period(Instant from, Instant to, Instant previousFrom, Instant previousTo, String timezone, String platform) {}

    /** previous es null cuando el periodo anterior no tiene datos comparables (antes de que se midiera). */
    public record Metric(Double value, Double previous) {}

    public record Summary(
            Metric activeUsers,
            Metric newUsers,
            Metric returningUsers,
            Metric sessions,
            Metric sessionsPerUser,
            Metric totalActiveMs,
            Metric avgSessionMs,
            Metric medianSessionMs,
            Metric trips,
            Metric arrivedTrips,
            Metric abandonedTrips,
            Metric completionRate
    ) {}

    public record DayPoint(LocalDate date, long activeUsers, long newUsers, long sessions, long activeMs,
                           long trips, long arrived) {}

    public record Bucket(String label, long count) {}

    public record Count(String key, long count) {}

    public record Durations(long count, Double meanMs, Double medianMs, Double p75Ms, Double p90Ms, Double p95Ms,
                            long totalMs, List<Bucket> histogram) {}

    public record Users(long active, long newUsers, long returning, List<Bucket> sessionsPerUser) {}

    /** Usuarios (dispositivos) por cada característica; withData = usuarios con ese dato disponible. */
    public record Devices(long users, List<Count> platforms, List<Count> browsers, List<Count> os,
                          List<Count> appVersions, List<Count> networks) {}

    public record FeatureUsage(String key, long sessions, long users, long uses) {}

    public record Permissions(long usersWithData, List<PermissionStat> byPermission, List<Count> grantedOfThree,
                              List<PermissionDay> daily) {}

    /** Usuarios según el último estado reportado de un permiso. */
    public record PermissionStat(String permission, long granted, long denied, long blocked, long pending,
                                 long unavailable, long notRequired) {}

    public record PermissionDay(LocalDate date, long users, long allGranted, long cameraGranted,
                                long locationGranted, long motionGranted, long motionRequired) {}

    /** count de base (p. ej. recorridos que terminaron sin ubicarse de 40 con cámara). */
    public record Problem(String code, long count, long base) {}

    /** Desde cuándo existe cada fuente: las visitas y los permisos se miden desde la versión 2.1 de la app. */
    public record Coverage(Instant sessionsSince, Instant tripsSince, Instant devicesSince) {}
}
