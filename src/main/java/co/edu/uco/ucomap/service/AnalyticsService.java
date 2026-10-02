package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO;
import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO.Bucket;
import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO.Count;
import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO.Metric;
import co.edu.uco.ucomap.dto.TripAnalyticsDTO;
import co.edu.uco.ucomap.model.AppSession;
import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.PermissionSnapshot;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.service.analytics.AnalyticsMath;
import co.edu.uco.ucomap.service.analytics.UserAgents;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static co.edu.uco.ucomap.service.analytics.AnalyticsMath.mean;
import static co.edu.uco.ucomap.service.analytics.AnalyticsMath.median;
import static co.edu.uco.ucomap.service.analytics.AnalyticsMath.percentile;
import static co.edu.uco.ucomap.service.analytics.AnalyticsMath.ratio;

/**
 * Convierte lo que ya registra la app (visitas, dispositivos y recorridos) en métricas para el panel.
 *
 * Usuario = dispositivo (deviceId persistente del navegador). Cada métrica se compara con el periodo
 * anterior de la misma duración, salvo que ese periodo sea anterior a que existiera el dato.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Bogota");
    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final Duration MAX_RANGE = Duration.ofDays(400);

    /** Menos de esto es abrir y cerrar (rebote). */
    static final long SHORT_SESSION_MS = 10_000;
    static final double POOR_GPS_M = 30;
    static final int MANY_VPS_FAILURES = 3;
    static final double NEAR_BUILDING_M = 80;
    static final double CAMPUS_RADIUS_M = 600;
    /** Celda del mapa de orígenes (~22 m): agrupa puntos sin mostrar recorridos individuales. */
    private static final double HEAT_CELL = 0.0002;

    private final MongoTemplate mongo;
    private final NavigationTripService tripService;
    private final BuildingRepository buildingRepository;

    // ── Filtro ────────────────────────────────────────────────

    public record Range(Instant from, Instant to, ZoneId zone, String platform, String building) {
        Instant previousFrom() {
            return from.minus(Duration.between(from, to));
        }
    }

    public static Range range(Instant from, Instant to, String timezone, String platform, String building) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_RANGE);
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El inicio debe ser anterior al fin");
        }
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo no puede superar 400 días");
        }
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? DEFAULT_ZONE : ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zona horaria inválida");
        }
        return new Range(start, end, zone, blankToNull(platform), blankToNull(building));
    }

    // ── Analítica general ────────────────────────────────────

    public AnalyticsOverviewDTO overview(Range r) {
        tripService.expireStaleTrips();
        Instant prevFrom = r.previousFrom();

        List<AppSession> sessions = sessions(r.from(), r.to(), r.platform());
        List<AppSession> prevSessions = sessions(prevFrom, r.from(), r.platform());
        List<NavigationTrip> trips = trips(r.from(), r.to(), r.platform(), null);
        List<NavigationTrip> prevTrips = trips(prevFrom, r.from(), r.platform(), null);
        List<DeviceSession> touched = devicesTouched(r.from(), r.to(), r.platform());
        List<DeviceSession> prevTouched = devicesTouched(prevFrom, r.from(), r.platform());

        Set<String> active = activeUsers(sessions, trips, touched);
        Set<String> prevActive = activeUsers(prevSessions, prevTrips, prevTouched);
        long newUsers = countFirstSeen(touched, r.from(), r.to());
        long prevNewUsers = countFirstSeen(prevTouched, prevFrom, r.from());
        Map<String, DeviceSession> devices = devicesById(active, touched);

        AnalyticsOverviewDTO.Coverage coverage = coverage();
        boolean sessionsComparable = covers(coverage.sessionsSince(), prevFrom);
        boolean tripsComparable = covers(coverage.tripsSince(), prevFrom);
        boolean usersComparable = covers(coverage.devicesSince(), prevFrom);

        List<Long> durations = sessions.stream().map(AppSession::getActiveMs).toList();
        List<Long> prevDurations = prevSessions.stream().map(AppSession::getActiveMs).toList();
        long sessionUsers = sessions.stream().map(AppSession::getDeviceId).distinct().count();
        long prevSessionUsers = prevSessions.stream().map(AppSession::getDeviceId).distinct().count();
        TripCounts tc = TripCounts.of(trips);
        TripCounts ptc = TripCounts.of(prevTrips);

        AnalyticsOverviewDTO.Summary summary = new AnalyticsOverviewDTO.Summary(
                metric(active.size(), prevActive.size(), usersComparable),
                metric(newUsers, prevNewUsers, usersComparable),
                metric(active.size() - newUsers, prevActive.size() - prevNewUsers, usersComparable),
                metric(sessions.size(), prevSessions.size(), sessionsComparable),
                metric(perUser(sessions.size(), sessionUsers), perUser(prevSessions.size(), prevSessionUsers), sessionsComparable),
                metric(sum(durations), sum(prevDurations), sessionsComparable),
                metric(mean(durations), mean(prevDurations), sessionsComparable),
                metric(median(durations), median(prevDurations), sessionsComparable),
                metric(trips.size(), prevTrips.size(), tripsComparable),
                metric(tc.arrived, ptc.arrived, tripsComparable),
                metric(tc.abandoned, ptc.abandoned, tripsComparable),
                metric(tc.completionRate(), ptc.completionRate(), tripsComparable));

        return new AnalyticsOverviewDTO(
                new AnalyticsOverviewDTO.Period(r.from(), r.to(), prevFrom, r.from(), r.zone().getId(), r.platform()),
                summary,
                daily(r, sessions, trips, touched),
                weekdayHour(sessions.stream().map(AppSession::getStartedAt).toList(), r.zone()),
                durations(durations),
                new AnalyticsOverviewDTO.Users(active.size(), newUsers, active.size() - newUsers, sessionsPerUser(sessions)),
                devicesBreakdown(active, devices, sessions, trips),
                features(sessions),
                permissions(r, sessions, devices),
                problems(sessions, trips, devices, r),
                coverage);
    }

    // ── Análisis de recorridos ───────────────────────────────

    public TripAnalyticsDTO trips(Range r) {
        tripService.expireStaleTrips();
        Instant prevFrom = r.previousFrom();
        List<NavigationTrip> trips = trips(r.from(), r.to(), r.platform(), r.building());
        List<NavigationTrip> prevTrips = trips(prevFrom, r.from(), r.platform(), r.building());
        boolean comparable = covers(coverage().tripsSince(), prevFrom);
        Map<String, Building> buildings = buildingsByCategory();

        TripCounts tc = TripCounts.of(trips);
        TripCounts ptc = TripCounts.of(prevTrips);
        List<Long> arrivedDurations = durationsOf(trips, TripStatus.ARRIVED);
        List<Long> abandonDurations = durationsOf(trips, TripStatus.ABANDONED);
        List<Double> routes = trips.stream().map(AnalyticsService::routeM).filter(Objects::nonNull).toList();
        List<NavigationTrip> finished = trips.stream().filter(t -> t.getStatus() != TripStatus.IN_PROGRESS).toList();
        List<NavigationTrip> withAR = finished.stream().filter(t -> Boolean.TRUE.equals(t.getUsedAR())).toList();
        List<Long> localized = finished.stream().map(NavigationTrip::getLocalizedMs).filter(Objects::nonNull).toList();
        List<Integer> vpsFailures = withAR.stream().map(t -> t.getVpsFailures() != null ? t.getVpsFailures() : 0).toList();
        long withOrigin = trips.stream().filter(t -> t.getStartLat() != null && t.getStartLng() != null).count();

        TripAnalyticsDTO.Summary summary = new TripAnalyticsDTO.Summary(
                metric(trips.size(), prevTrips.size(), comparable),
                metric(tc.arrived, ptc.arrived, comparable),
                metric(tc.abandoned, ptc.abandoned, comparable),
                tc.inProgress,
                metric(tc.completionRate(), ptc.completionRate(), comparable),
                median(arrivedDurations), percentile(arrivedDurations, 75), percentile(arrivedDurations, 90),
                mean(arrivedDurations), median(abandonDurations), mean(routes),
                ratio(withAR.size(), finished.size()), median(localized), mean(vpsFailures), withOrigin);

        return new TripAnalyticsDTO(
                new AnalyticsOverviewDTO.Period(r.from(), r.to(), prevFrom, r.from(), r.zone().getId(), r.platform()),
                summary,
                destinations(trips, buildings),
                origins(trips, buildings),
                routesTable(trips, buildings),
                counts(trips.stream().filter(t -> t.getStatus() == TripStatus.ABANDONED)
                        .map(AnalyticsService::abandonStage).toList()),
                counts(trips.stream().filter(t -> t.getStatus() == TripStatus.ABANDONED)
                        .map(t -> t.getEndReason() != null ? t.getEndReason() : "closed").toList()),
                counts(trips.stream().map(t -> t.getStartMode() != null ? t.getStartMode() : "unknown").toList()),
                buckets(AnalyticsMath.histogram(trips.stream().map(NavigationTrip::getStartDistanceM)
                        .filter(Objects::nonNull).map(Math::round).toList(), AnalyticsMath.START_DISTANCE)),
                tripDaily(r, trips),
                weekdayHour(trips.stream().map(NavigationTrip::getStartedAt).toList(), r.zone()),
                originPoints(trips),
                destinationPoints(trips, buildings),
                mongo.findDistinct(new Query(), "building", NavigationTrip.class, String.class).stream()
                        .filter(b -> b != null && !b.isBlank()).sorted().toList());
    }

    // ── Consultas (solo los campos necesarios) ───────────────

    private List<AppSession> sessions(Instant from, Instant to, String platform) {
        Criteria c = Criteria.where("startedAt").gte(from).lt(to);
        if (platform != null) c = c.and("platform").is(platform);
        Query q = new Query(c);
        q.fields().include("deviceId", "platform", "startedAt", "lastActiveAt", "activeMs", "features",
                "permissions", "appVersion", "networkType");
        return mongo.find(q, AppSession.class);
    }

    private List<NavigationTrip> trips(Instant from, Instant to, String platform, String building) {
        Criteria c = Criteria.where("startedAt").gte(from).lt(to);
        if (platform != null) c = c.and("platform").is(platform);
        if (building != null) c = c.and("building").is(building);
        Query q = new Query(c);
        q.fields().exclude("lastSeenAt", "endedAt", "tripId");
        return mongo.find(q, NavigationTrip.class);
    }

    /** Dispositivos que aparecieron o hicieron ping en el periodo (incluye versiones de la app sin visitas). */
    private List<DeviceSession> devicesTouched(Instant from, Instant to, String platform) {
        Criteria c = new Criteria().orOperator(
                Criteria.where("firstSeen").gte(from).lt(to),
                Criteria.where("lastSeen").gte(from).lt(to));
        if (platform != null) c = new Criteria().andOperator(c, Criteria.where("platform").is(platform));
        return mongo.find(deviceFields(new Query(c)), DeviceSession.class);
    }

    private Map<String, DeviceSession> devicesById(Set<String> ids, List<DeviceSession> known) {
        Map<String, DeviceSession> byId = new HashMap<>();
        known.forEach(d -> byId.put(d.getDeviceId(), d));
        List<String> missing = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            mongo.find(deviceFields(new Query(Criteria.where("deviceId").in(missing))), DeviceSession.class)
                    .forEach(d -> byId.put(d.getDeviceId(), d));
        }
        return byId;
    }

    private static Query deviceFields(Query q) {
        q.fields().include("deviceId", "platform", "firstSeen", "lastSeen", "userAgent", "appVersion", "permissions");
        return q;
    }

    private AnalyticsOverviewDTO.Coverage coverage() {
        return new AnalyticsOverviewDTO.Coverage(
                earliest(AppSession.class, "startedAt"),
                earliest(NavigationTrip.class, "startedAt"),
                earliest(DeviceSession.class, "firstSeen"));
    }

    private Instant earliest(Class<?> type, String field) {
        Query q = new Query(Criteria.where(field).ne(null)).with(Sort.by(Sort.Direction.ASC, field)).limit(1);
        q.fields().include(field);
        Object first = mongo.findOne(q, type);
        if (first instanceof AppSession s) return s.getStartedAt();
        if (first instanceof NavigationTrip t) return t.getStartedAt();
        if (first instanceof DeviceSession d) return d.getFirstSeen();
        return null;
    }

    private Map<String, Building> buildingsByCategory() {
        Map<String, Building> map = new HashMap<>();
        for (Building b : buildingRepository.findAll()) {
            if (b.getCategory() != null) map.putIfAbsent(b.getCategory(), b);
        }
        return map;
    }

    // ── Usuarios y actividad ─────────────────────────────────

    private static Set<String> activeUsers(List<AppSession> sessions, List<NavigationTrip> trips, List<DeviceSession> touched) {
        Set<String> ids = new HashSet<>();
        sessions.forEach(s -> ids.add(s.getDeviceId()));
        trips.forEach(t -> ids.add(t.getDeviceId()));
        touched.forEach(d -> ids.add(d.getDeviceId()));
        ids.remove(null);
        return ids;
    }

    private static long countFirstSeen(List<DeviceSession> devices, Instant from, Instant to) {
        return devices.stream().filter(d -> within(d.getFirstSeen(), from, to)).count();
    }

    private List<AnalyticsOverviewDTO.DayPoint> daily(Range r, List<AppSession> sessions, List<NavigationTrip> trips,
                                                      List<DeviceSession> touched) {
        Map<LocalDate, DayAcc> days = emptyDays(r, DayAcc::new);
        for (AppSession s : sessions) {
            DayAcc d = days.get(day(s.getStartedAt(), r.zone()));
            if (d == null) continue;
            d.users.add(s.getDeviceId());
            d.sessions++;
            d.activeMs += s.getActiveMs();
        }
        for (NavigationTrip t : trips) {
            DayAcc d = days.get(day(t.getStartedAt(), r.zone()));
            if (d == null) continue;
            d.users.add(t.getDeviceId());
            d.trips++;
            if (t.getStatus() == TripStatus.ARRIVED) d.arrived++;
        }
        for (DeviceSession dev : touched) {
            if (within(dev.getFirstSeen(), r.from(), r.to())) {
                DayAcc d = days.get(day(dev.getFirstSeen(), r.zone()));
                if (d != null) { d.users.add(dev.getDeviceId()); d.newUsers++; }
            }
            if (within(dev.getLastSeen(), r.from(), r.to())) {
                DayAcc d = days.get(day(dev.getLastSeen(), r.zone()));
                if (d != null) d.users.add(dev.getDeviceId());
            }
        }
        return days.entrySet().stream().map(e -> new AnalyticsOverviewDTO.DayPoint(e.getKey(), e.getValue().users.size(),
                e.getValue().newUsers, e.getValue().sessions, e.getValue().activeMs, e.getValue().trips,
                e.getValue().arrived)).toList();
    }

    private static final class DayAcc {
        final Set<String> users = new HashSet<>();
        long newUsers, sessions, activeMs, trips, arrived;
    }

    static long[][] weekdayHour(List<Instant> instants, ZoneId zone) {
        long[][] grid = new long[7][24];
        for (Instant i : instants) {
            if (i == null) continue;
            ZonedDateTime z = i.atZone(zone);
            grid[z.getDayOfWeek().getValue() - 1][z.getHour()]++;
        }
        return grid;
    }

    private static AnalyticsOverviewDTO.Durations durations(List<Long> values) {
        return new AnalyticsOverviewDTO.Durations(values.size(), mean(values), median(values),
                percentile(values, 75), percentile(values, 90), percentile(values, 95), sum(values),
                buckets(AnalyticsMath.histogram(values, AnalyticsMath.SESSION_RANGES)));
    }

    private static List<Bucket> sessionsPerUser(List<AppSession> sessions) {
        Map<String, Long> perUser = sessions.stream()
                .collect(Collectors.groupingBy(AppSession::getDeviceId, Collectors.counting()));
        return buckets(AnalyticsMath.histogram(perUser.values(), AnalyticsMath.SESSIONS_PER_USER));
    }

    // ── Dispositivos ─────────────────────────────────────────

    private static AnalyticsOverviewDTO.Devices devicesBreakdown(Set<String> active, Map<String, DeviceSession> devices,
                                                                List<AppSession> sessions, List<NavigationTrip> trips) {
        Map<String, AppSession> latest = latestSessionByDevice(sessions);
        Map<String, String> tripPlatform = new HashMap<>();
        trips.forEach(t -> tripPlatform.putIfAbsent(t.getDeviceId(), t.getPlatform()));

        Map<String, Long> platforms = new HashMap<>(), browsers = new HashMap<>(), os = new HashMap<>(),
                versions = new HashMap<>(), networks = new HashMap<>();
        for (String id : active) {
            DeviceSession d = devices.get(id);
            AppSession s = latest.get(id);
            String platform = d != null && d.getPlatform() != null ? d.getPlatform()
                    : s != null ? s.getPlatform() : tripPlatform.get(id);
            platforms.merge(platform != null ? platform : "Unknown", 1L, Long::sum);
            String ua = d != null ? d.getUserAgent() : null;
            browsers.merge(UserAgents.browser(ua), 1L, Long::sum);
            os.merge(UserAgents.os(ua), 1L, Long::sum);
            String version = s != null && s.getAppVersion() != null ? s.getAppVersion()
                    : d != null && d.getAppVersion() != null ? d.getAppVersion() : "Sin dato";
            versions.merge(version, 1L, Long::sum);
            if (s != null && s.getNetworkType() != null) networks.merge(s.getNetworkType(), 1L, Long::sum);
        }
        return new AnalyticsOverviewDTO.Devices(active.size(), countsOf(platforms), countsOf(browsers), countsOf(os),
                countsOf(versions), countsOf(networks));
    }

    private static Map<String, AppSession> latestSessionByDevice(List<AppSession> sessions) {
        Map<String, AppSession> latest = new HashMap<>();
        for (AppSession s : sessions) {
            latest.merge(s.getDeviceId(), s, (a, b) -> lastActive(b).isAfter(lastActive(a)) ? b : a);
        }
        return latest;
    }

    private static Instant lastActive(AppSession s) {
        return s.getLastActiveAt() != null ? s.getLastActiveAt() : s.getStartedAt();
    }

    // ── Funciones ────────────────────────────────────────────

    private static List<AnalyticsOverviewDTO.FeatureUsage> features(List<AppSession> sessions) {
        Map<String, long[]> acc = new TreeMap<>();
        Map<String, Set<String>> users = new HashMap<>();
        for (AppSession s : sessions) {
            if (s.getFeatures() == null) continue;
            s.getFeatures().forEach((key, uses) -> {
                if (uses == null || uses <= 0) return;
                long[] a = acc.computeIfAbsent(key, k -> new long[2]);
                a[0]++;
                a[1] += uses;
                users.computeIfAbsent(key, k -> new HashSet<>()).add(s.getDeviceId());
            });
        }
        return acc.entrySet().stream()
                .map(e -> new AnalyticsOverviewDTO.FeatureUsage(e.getKey(), e.getValue()[0],
                        users.get(e.getKey()).size(), e.getValue()[1]))
                .sorted(Comparator.comparingLong(AnalyticsOverviewDTO.FeatureUsage::sessions).reversed())
                .toList();
    }

    // ── Permisos ─────────────────────────────────────────────

    private AnalyticsOverviewDTO.Permissions permissions(Range r, List<AppSession> sessions, Map<String, DeviceSession> devices) {
        Map<String, PermissionSnapshot> latest = latestPermissions(r, sessions, devices);

        List<AnalyticsOverviewDTO.PermissionStat> byPermission = List.of(
                permissionStat("camera", latest.values().stream().map(PermissionSnapshot::getCamera).toList()),
                permissionStat("location", latest.values().stream().map(PermissionSnapshot::getLocation).toList()),
                permissionStat("motion", latest.values().stream().map(PermissionSnapshot::getMotion).toList()));

        Map<String, Long> ofThree = new LinkedHashMap<>();
        for (String k : List.of("3", "2", "1", "0")) ofThree.put(k, 0L);
        latest.values().forEach(p -> ofThree.merge(String.valueOf(grantedCount(p)), 1L, Long::sum));

        // Evolución: estado de cada usuario el día de cada visita
        Map<LocalDate, Map<String, PermissionSnapshot>> perDay = new LinkedHashMap<>();
        emptyDays(r, HashMap::new).keySet().forEach(d -> perDay.put(d, new HashMap<>()));
        sessions.stream().filter(s -> s.getPermissions() != null)
                .sorted(Comparator.comparing(AnalyticsService::lastActive))
                .forEach(s -> {
                    Map<String, PermissionSnapshot> day = perDay.get(day(lastActive(s), r.zone()));
                    if (day != null) day.put(s.getDeviceId(), s.getPermissions());
                });
        List<AnalyticsOverviewDTO.PermissionDay> daily = perDay.entrySet().stream().map(e -> {
            Collection<PermissionSnapshot> ps = e.getValue().values();
            return new AnalyticsOverviewDTO.PermissionDay(e.getKey(), ps.size(),
                    ps.stream().filter(p -> grantedCount(p) == 3).count(),
                    ps.stream().filter(p -> "granted".equals(p.getCamera())).count(),
                    ps.stream().filter(p -> "granted".equals(p.getLocation())).count(),
                    ps.stream().filter(p -> "granted".equals(p.getMotion())).count(),
                    ps.stream().filter(p -> p.getMotion() != null && !"not-required".equals(p.getMotion())).count());
        }).toList();

        return new AnalyticsOverviewDTO.Permissions(latest.size(), byPermission, countsInOrder(ofThree), daily);
    }

    /** Último estado de cada usuario en el periodo: el de su última visita o, sin visitas, el del dispositivo. */
    private static Map<String, PermissionSnapshot> latestPermissions(Range r, List<AppSession> sessions,
                                                                    Map<String, DeviceSession> devices) {
        Map<String, PermissionSnapshot> latest = new HashMap<>();
        sessions.stream().filter(s -> s.getPermissions() != null)
                .sorted(Comparator.comparing(AnalyticsService::lastActive))
                .forEach(s -> latest.put(s.getDeviceId(), s.getPermissions()));
        devices.values().forEach(d -> {
            if (d.getPermissions() != null && within(d.getLastSeen(), r.from(), r.to())) {
                latest.putIfAbsent(d.getDeviceId(), d.getPermissions());
            }
        });
        return latest;
    }

    /** Permisos necesarios concedidos (0–3). En Android el movimiento no pide permiso y cuenta como concedido. */
    static int grantedCount(PermissionSnapshot p) {
        int n = 0;
        if ("granted".equals(p.getCamera())) n++;
        if ("granted".equals(p.getLocation())) n++;
        if ("granted".equals(p.getMotion()) || "not-required".equals(p.getMotion())) n++;
        return n;
    }

    private static AnalyticsOverviewDTO.PermissionStat permissionStat(String name, List<String> states) {
        long granted = 0, denied = 0, blocked = 0, pending = 0, unavailable = 0, notRequired = 0;
        for (String s : states) {
            if (s == null) continue;
            switch (s) {
                case "granted" -> granted++;
                case "denied" -> denied++;
                case "blocked" -> blocked++;
                case "unavailable" -> unavailable++;
                case "not-required" -> notRequired++;
                default -> pending++;
            }
        }
        return new AnalyticsOverviewDTO.PermissionStat(name, granted, denied, blocked, pending, unavailable, notRequired);
    }

    // ── Problemas ────────────────────────────────────────────

    private List<AnalyticsOverviewDTO.Problem> problems(List<AppSession> sessions, List<NavigationTrip> trips,
                                                        Map<String, DeviceSession> devices, Range r) {
        List<NavigationTrip> finished = trips.stream().filter(t -> t.getStatus() != TripStatus.IN_PROGRESS).toList();
        List<NavigationTrip> withAR = finished.stream().filter(t -> Boolean.TRUE.equals(t.getUsedAR())).toList();
        List<NavigationTrip> withAccuracy = trips.stream().filter(t -> t.getStartAccuracyM() != null).toList();
        Collection<PermissionSnapshot> perms = latestPermissions(r, sessions, devices).values();
        long motionRequired = perms.stream().filter(p -> p.getMotion() != null && !"not-required".equals(p.getMotion())).count();

        List<AnalyticsOverviewDTO.Problem> problems = new ArrayList<>(List.of(
                problem("trip-timeout", finished.stream().filter(t -> "timeout".equals(t.getEndReason())).count(), finished.size()),
                problem("trip-abandoned-outdoor", finished.stream().filter(t -> t.getStatus() == TripStatus.ABANDONED
                        && "outdoor".equals(abandonStage(t))).count(), finished.size()),
                problem("ar-not-located", withAR.stream().filter(t -> t.getLocalizedMs() == null).count(), withAR.size()),
                problem("vps-many-failures", withAR.stream().filter(t -> t.getVpsFailures() != null
                        && t.getVpsFailures() >= MANY_VPS_FAILURES).count(), withAR.size()),
                problem("gps-poor-start", withAccuracy.stream().filter(t -> t.getStartAccuracyM() > POOR_GPS_M).count(), withAccuracy.size()),
                problem("camera-refused", perms.stream().filter(p -> "denied".equals(p.getCamera()) || "blocked".equals(p.getCamera())).count(), perms.size()),
                problem("location-refused", perms.stream().filter(p -> "denied".equals(p.getLocation()) || "blocked".equals(p.getLocation())).count(), perms.size()),
                problem("motion-refused", perms.stream().filter(p -> "denied".equals(p.getMotion())).count(), motionRequired),
                problem("short-sessions", sessions.stream().filter(s -> s.getActiveMs() < SHORT_SESSION_MS).count(), sessions.size())));
        problems.removeIf(p -> p.base() == 0);
        problems.sort(Comparator.comparingDouble((AnalyticsOverviewDTO.Problem p) -> (double) p.count() / p.base()).reversed());
        return problems;
    }

    private static AnalyticsOverviewDTO.Problem problem(String code, long count, long base) {
        return new AnalyticsOverviewDTO.Problem(code, count, base);
    }

    // ── Recorridos ───────────────────────────────────────────

    /**
     * En qué momento se abandonó, a partir de los tiempos que reporta la app:
     * sin llegar al edificio → outdoor; dentro sin abrir la cámara → in-building;
     * con cámara sin ubicarse → locating; ubicado siguiendo las flechas → indoor-route.
     */
    static String abandonStage(NavigationTrip t) {
        if ("destination-changed".equals(t.getEndReason())) return "changed-destination";
        // Sin aviso de cierre no se conocen los tiempos del recorrido
        if ("timeout".equals(t.getEndReason())) return "unknown";
        boolean inBuilding = t.getBuildingReachedMs() != null || "indoor".equals(t.getStartMode());
        if (!inBuilding) return "outdoor";
        if (t.getLocalizedMs() != null) return "indoor-route";
        if (Boolean.TRUE.equals(t.getUsedAR())) return "locating";
        return "in-building";
    }

    private static Double routeM(NavigationTrip t) {
        if (t.getOutdoorRouteM() == null && t.getIndoorRouteM() == null) return null;
        return (t.getOutdoorRouteM() != null ? t.getOutdoorRouteM() : 0) + (t.getIndoorRouteM() != null ? t.getIndoorRouteM() : 0);
    }

    private static List<Long> durationsOf(List<NavigationTrip> trips, TripStatus status) {
        return trips.stream().filter(t -> t.getStatus() == status).map(NavigationTrip::getDurationMs)
                .filter(Objects::nonNull).toList();
    }

    private static List<TripAnalyticsDTO.DestinationRow> destinations(List<NavigationTrip> trips, Map<String, Building> buildings) {
        Map<String, List<NavigationTrip>> groups = trips.stream().collect(Collectors.groupingBy(
                t -> t.getBuilding() + "|" + t.getRoomName(), LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream().map(g -> {
            NavigationTrip first = g.get(0);
            TripCounts c = TripCounts.of(g);
            List<Long> arrived = durationsOf(g, TripStatus.ARRIVED);
            return new TripAnalyticsDTO.DestinationRow(first.getBuilding(), buildingLabel(first.getBuilding(), buildings),
                    first.getRoomName(), g.size(), c.arrived, c.abandoned, c.completionRate(), median(arrived),
                    mean(arrived), mean(g.stream().map(AnalyticsService::routeM).filter(Objects::nonNull).toList()));
        }).sorted(Comparator.comparingLong(TripAnalyticsDTO.DestinationRow::total).reversed()).toList();
    }

    /**
     * Origen legible: edificio a menos de 80 m del punto de partida, otra zona del campus o fuera de él.
     * Sin punto de partida (versiones anteriores o sin permiso) se usa el modo de inicio.
     */
    static String originLabel(NavigationTrip t, Map<String, Building> buildings) {
        if (t.getStartLat() == null || t.getStartLng() == null) {
            return "indoor".equals(t.getStartMode()) ? "Ya en el edificio" : "Sin ubicación";
        }
        Building nearest = null;
        double best = Double.MAX_VALUE;
        for (Building b : buildings.values()) {
            if (b.getGps() == null) continue;
            double d = AnalyticsMath.haversineM(t.getStartLat(), t.getStartLng(), b.getGps().getLat(), b.getGps().getLng());
            if (d < best) { best = d; nearest = b; }
        }
        if (nearest != null && best <= NEAR_BUILDING_M) return nearest.getLabel() != null ? nearest.getLabel() : nearest.getCategory();
        if (nearest != null && best <= CAMPUS_RADIUS_M) return "Otra zona del campus";
        return "Fuera del campus";
    }

    private static List<TripAnalyticsDTO.OriginRow> origins(List<NavigationTrip> trips, Map<String, Building> buildings) {
        Map<String, long[]> acc = new HashMap<>();
        for (NavigationTrip t : trips) {
            long[] a = acc.computeIfAbsent(originLabel(t, buildings), k -> new long[2]);
            a[0]++;
            if (t.getStatus() == TripStatus.ARRIVED) a[1]++;
        }
        return acc.entrySet().stream()
                .map(e -> new TripAnalyticsDTO.OriginRow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(TripAnalyticsDTO.OriginRow::total).reversed()).toList();
    }

    private static List<TripAnalyticsDTO.RouteRow> routesTable(List<NavigationTrip> trips, Map<String, Building> buildings) {
        Map<String, List<NavigationTrip>> groups = trips.stream().collect(Collectors.groupingBy(
                t -> originLabel(t, buildings) + "|" + t.getBuilding() + "|" + t.getRoomName(),
                LinkedHashMap::new, Collectors.toList()));
        return groups.values().stream().map(g -> {
            NavigationTrip first = g.get(0);
            TripCounts c = TripCounts.of(g);
            return new TripAnalyticsDTO.RouteRow(originLabel(first, buildings), first.getRoomName(),
                    buildingLabel(first.getBuilding(), buildings), g.size(), c.arrived,
                    median(durationsOf(g, TripStatus.ARRIVED)),
                    mean(g.stream().map(AnalyticsService::routeM).filter(Objects::nonNull).toList()));
        }).sorted(Comparator.comparingLong(TripAnalyticsDTO.RouteRow::total).reversed()).toList();
    }

    private static List<TripAnalyticsDTO.TripDay> tripDaily(Range r, List<NavigationTrip> trips) {
        Map<LocalDate, long[]> days = emptyDays(r, () -> new long[3]);
        for (NavigationTrip t : trips) {
            long[] d = days.get(day(t.getStartedAt(), r.zone()));
            if (d == null) continue;
            d[0]++;
            if (t.getStatus() == TripStatus.ARRIVED) d[1]++;
            if (t.getStatus() == TripStatus.ABANDONED) d[2]++;
        }
        return days.entrySet().stream()
                .map(e -> new TripAnalyticsDTO.TripDay(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private static List<TripAnalyticsDTO.HeatPoint> originPoints(List<NavigationTrip> trips) {
        Map<String, long[]> cells = new HashMap<>();
        Map<String, double[]> centers = new HashMap<>();
        for (NavigationTrip t : trips) {
            if (t.getStartLat() == null || t.getStartLng() == null) continue;
            double lat = Math.round(t.getStartLat() / HEAT_CELL) * HEAT_CELL;
            double lng = Math.round(t.getStartLng() / HEAT_CELL) * HEAT_CELL;
            String key = lat + "," + lng;
            cells.computeIfAbsent(key, k -> new long[1])[0]++;
            centers.putIfAbsent(key, new double[]{lat, lng});
        }
        return cells.entrySet().stream()
                .map(e -> new TripAnalyticsDTO.HeatPoint(centers.get(e.getKey())[0], centers.get(e.getKey())[1], e.getValue()[0]))
                .sorted(Comparator.comparingLong(TripAnalyticsDTO.HeatPoint::weight).reversed()).toList();
    }

    private static List<TripAnalyticsDTO.BuildingPoint> destinationPoints(List<NavigationTrip> trips, Map<String, Building> buildings) {
        Map<String, long[]> acc = new HashMap<>();
        for (NavigationTrip t : trips) {
            long[] a = acc.computeIfAbsent(String.valueOf(t.getBuilding()), k -> new long[2]);
            a[0]++;
            if (t.getStatus() == TripStatus.ARRIVED) a[1]++;
        }
        List<TripAnalyticsDTO.BuildingPoint> points = new ArrayList<>();
        acc.forEach((category, a) -> {
            Building b = buildings.get(category);
            if (b == null || b.getGps() == null) return;
            points.add(new TripAnalyticsDTO.BuildingPoint(category, buildingLabel(category, buildings),
                    b.getGps().getLat(), b.getGps().getLng(), a[0], a[1]));
        });
        points.sort(Comparator.comparingLong(TripAnalyticsDTO.BuildingPoint::total).reversed());
        return points;
    }

    private static String buildingLabel(String category, Map<String, Building> buildings) {
        Building b = category != null ? buildings.get(category) : null;
        return b != null && b.getLabel() != null ? b.getLabel() : category;
    }

    private record TripCounts(long arrived, long abandoned, long inProgress) {
        static TripCounts of(List<NavigationTrip> trips) {
            long arrived = 0, abandoned = 0, inProgress = 0;
            for (NavigationTrip t : trips) {
                if (t.getStatus() == TripStatus.ARRIVED) arrived++;
                else if (t.getStatus() == TripStatus.ABANDONED) abandoned++;
                else inProgress++;
            }
            return new TripCounts(arrived, abandoned, inProgress);
        }

        /** Sobre los terminados: los que siguen en curso todavía no tienen resultado. */
        Double completionRate() {
            return ratio(arrived, arrived + abandoned);
        }
    }

    // ── Utilidades ───────────────────────────────────────────

    private static Metric metric(Number value, Number previous, boolean comparable) {
        return new Metric(value != null ? value.doubleValue() : null,
                comparable && previous != null ? previous.doubleValue() : null);
    }

    private static boolean covers(Instant since, Instant periodStart) {
        return since != null && !since.isAfter(periodStart);
    }

    private static Double perUser(long sessions, long users) {
        return users == 0 ? null : (double) sessions / users;
    }

    private static long sum(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }

    private static boolean within(Instant i, Instant from, Instant to) {
        return i != null && !i.isBefore(from) && i.isBefore(to);
    }

    private static LocalDate day(Instant i, ZoneId zone) {
        return i == null ? null : i.atZone(zone).toLocalDate();
    }

    private static <T> Map<LocalDate, T> emptyDays(Range r, Supplier<T> init) {
        // LinkedHashMap y no TreeMap: get(null) devuelve null en vez de lanzar excepción
        Map<LocalDate, T> days = new LinkedHashMap<>();
        LocalDate end = r.to().minusMillis(1).atZone(r.zone()).toLocalDate();
        for (LocalDate d = r.from().atZone(r.zone()).toLocalDate(); !d.isAfter(end); d = d.plusDays(1)) {
            days.put(d, init.get());
        }
        return days;
    }

    private static List<Count> counts(List<String> values) {
        return countsOf(values.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
    }

    private static List<Count> countsOf(Map<String, Long> counts) {
        return AnalyticsMath.sortedDesc(counts).stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    private static List<Count> countsInOrder(Map<String, Long> counts) {
        return counts.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    private static List<Bucket> buckets(Map<String, Long> histogram) {
        return histogram.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue())).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
