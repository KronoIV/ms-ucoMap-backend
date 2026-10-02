package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.AnalyticsOverviewDTO;
import co.edu.uco.ucomap.dto.TripAnalyticsDTO;
import co.edu.uco.ucomap.model.AppSession;
import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.model.GpsPoint;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.PermissionSnapshot;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.BuildingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final Instant FROM = Instant.parse("2026-03-02T05:00:00Z");   // lunes 00:00 en Bogotá
    private static final Instant TO = Instant.parse("2026-03-09T05:00:00Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Mock MongoTemplate mongo;
    @Mock NavigationTripService tripService;
    @Mock BuildingRepository buildingRepository;
    @InjectMocks AnalyticsService service;

    private static AppSession session(String device, Instant start, long activeMs, PermissionSnapshot perms,
                                      Map<String, Integer> features) {
        return AppSession.builder().sessionId(device + start).deviceId(device).platform("iOS").startedAt(start)
                .lastActiveAt(start.plusMillis(activeMs)).activeMs(activeMs).permissions(perms)
                .features(features).build();
    }

    private static NavigationTrip trip(String device, TripStatus status, String reason, Instant start) {
        return NavigationTrip.builder().deviceId(device).status(status).endReason(reason).startedAt(start)
                .building("CO").roomName("CO 101").startMode("outdoor").durationMs(300_000L).build();
    }

    @Test
    void overviewUsesRealActiveTimeAndCountsUsersOnce() {
        Instant monday9 = FROM.plusSeconds(9 * 3600);
        when(mongo.find(any(Query.class), eq(AppSession.class))).thenReturn(List.of(
                session("a", monday9, 60_000, new PermissionSnapshot("granted", "granted", "not-required"), Map.of("mode:outdoor", 1)),
                session("a", monday9.plusSeconds(86_400), 120_000, null, Map.of()),
                session("b", monday9, 3_600_000, new PermissionSnapshot("blocked", "granted", "denied"), Map.of("mode:outdoor", 2, "search", 1))),
                List.of());
        when(mongo.find(any(Query.class), eq(NavigationTrip.class))).thenReturn(List.of(
                trip("a", TripStatus.ARRIVED, "ar-arrival", monday9),
                trip("c", TripStatus.ABANDONED, "closed", monday9),
                trip("c", TripStatus.IN_PROGRESS, null, monday9)), List.of());
        when(mongo.find(any(Query.class), eq(DeviceSession.class))).thenReturn(List.of(
                DeviceSession.builder().deviceId("a").platform("iOS").firstSeen(FROM.minusSeconds(864_000)).lastSeen(monday9).build(),
                DeviceSession.builder().deviceId("b").platform("iOS").firstSeen(monday9).lastSeen(monday9).build()),
                List.of(), List.of(DeviceSession.builder().deviceId("c").platform("Android").firstSeen(FROM.minusSeconds(864_000)).build()));

        AnalyticsOverviewDTO dto = service.overview(AnalyticsService.range(FROM, TO, "America/Bogota", null, null));

        var s = dto.summary();
        assertThat(s.activeUsers().value()).isEqualTo(3);        // a, b (visitas) y c (recorridos)
        assertThat(s.newUsers().value()).isEqualTo(1);           // b apareció en el periodo
        assertThat(s.returningUsers().value()).isEqualTo(2);
        assertThat(s.sessions().value()).isEqualTo(3);
        assertThat(s.totalActiveMs().value()).isEqualTo(3_780_000);
        assertThat(s.medianSessionMs().value()).isEqualTo(120_000);  // la visita de 1 h no infla la mediana
        assertThat(s.completionRate().value()).isEqualTo(0.5);       // 1 de 2 terminados (el en curso no cuenta)
        // Sin datos anteriores al periodo no hay comparación
        assertThat(s.sessions().previous()).isNull();

        assertThat(dto.daily()).hasSize(7);
        assertThat(dto.daily().get(0).sessions()).isEqualTo(2);
        assertThat(dto.sessionsByWeekdayHour()[0][9]).isEqualTo(2);  // lunes 9:00 hora local
        assertThat(dto.sessionDurations().histogram()).extracting(AnalyticsOverviewDTO.Bucket::count)
                .containsExactly(0L, 0L, 2L, 0L, 0L, 0L, 0L, 1L);

        var features = dto.features();
        assertThat(features.get(0).key()).isEqualTo("mode:outdoor");
        assertThat(features.get(0).sessions()).isEqualTo(2);
        assertThat(features.get(0).uses()).isEqualTo(3);

        var perms = dto.permissions();
        assertThat(perms.usersWithData()).isEqualTo(2);
        assertThat(perms.grantedOfThree()).extracting(AnalyticsOverviewDTO.Count::count).containsExactly(1L, 0L, 1L, 0L);
        assertThat(perms.byPermission().get(0).blocked()).isEqualTo(1);
        assertThat(dto.problems()).extracting(AnalyticsOverviewDTO.Problem::code).contains("camera-refused", "motion-refused");
    }

    @Test
    void abandonStageFollowsWhatTheTripReached() {
        NavigationTrip t = trip("a", TripStatus.ABANDONED, "closed", FROM);
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("outdoor");
        t.setBuildingReachedMs(60_000L);
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("in-building");
        t.setUsedAR(true);
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("locating");
        t.setLocalizedMs(90_000L);
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("indoor-route");
        t.setEndReason("timeout");
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("unknown");
        t.setEndReason("destination-changed");
        assertThat(AnalyticsService.abandonStage(t)).isEqualTo("changed-destination");
    }

    @Test
    void originIsTheNearestBuildingOrAZone() {
        Building co = Building.builder().buildingId("COLEGIO").label("Colegio").category("CO")
                .gps(new GpsPoint(6.149879, -75.366053)).build();
        Map<String, Building> buildings = Map.of("CO", co);
        NavigationTrip t = trip("a", TripStatus.ARRIVED, null, FROM);

        assertThat(AnalyticsService.originLabel(t, buildings)).isEqualTo("Sin ubicación");
        t.setStartMode("indoor");
        assertThat(AnalyticsService.originLabel(t, buildings)).isEqualTo("Ya en el edificio");
        t.setStartLat(6.1499);
        t.setStartLng(-75.3661);
        assertThat(AnalyticsService.originLabel(t, buildings)).isEqualTo("Colegio");
        t.setStartLat(6.1530);   // ~350 m
        assertThat(AnalyticsService.originLabel(t, buildings)).isEqualTo("Otra zona del campus");
        t.setStartLat(6.20);
        assertThat(AnalyticsService.originLabel(t, buildings)).isEqualTo("Fuera del campus");
    }

    @Test
    void tripAnalyticsGroupsRoutesAndStages() {
        Building co = Building.builder().label("Colegio").category("CO").gps(new GpsPoint(6.149879, -75.366053)).build();
        when(buildingRepository.findAll()).thenReturn(List.of(co));
        NavigationTrip arrived = trip("a", TripStatus.ARRIVED, "ar-arrival", FROM.plusSeconds(3600));
        arrived.setStartLat(6.1499);
        arrived.setStartLng(-75.3661);
        arrived.setOutdoorRouteM(100.0);
        arrived.setIndoorRouteM(20.0);
        NavigationTrip abandoned = trip("b", TripStatus.ABANDONED, "closed", FROM.plusSeconds(7200));
        when(mongo.find(any(Query.class), eq(NavigationTrip.class))).thenReturn(List.of(arrived, abandoned), List.of());

        TripAnalyticsDTO dto = service.trips(AnalyticsService.range(FROM, TO, null, null, null));

        assertThat(dto.summary().completionRate().value()).isEqualTo(0.5);
        assertThat(dto.summary().avgRouteM()).isEqualTo(120.0);
        assertThat(dto.summary().withOrigin()).isEqualTo(1);
        assertThat(dto.destinations()).singleElement().satisfies(d -> {
            assertThat(d.buildingLabel()).isEqualTo("Colegio");
            assertThat(d.total()).isEqualTo(2);
            assertThat(d.completionRate()).isEqualTo(0.5);
        });
        assertThat(dto.routes()).extracting(TripAnalyticsDTO.RouteRow::origin).containsExactlyInAnyOrder("Colegio", "Sin ubicación");
        assertThat(dto.abandonStages()).singleElement().satisfies(c -> assertThat(c.key()).isEqualTo("outdoor"));
        assertThat(dto.destinationPoints()).singleElement().satisfies(p -> assertThat(p.total()).isEqualTo(2));
        assertThat(dto.originPoints()).hasSize(1);
    }

    @Test
    void weekdayHourUsesLocalTime() {
        // 2026-03-02 14:30 UTC = lunes 09:30 en Bogotá
        long[][] grid = AnalyticsService.weekdayHour(List.of(Instant.parse("2026-03-02T14:30:00Z")), BOGOTA);
        assertThat(grid[0][9]).isEqualTo(1);
    }

    @Test
    void invalidRangesAreRejected() {
        assertThatThrownBy(() -> AnalyticsService.range(TO, FROM, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> AnalyticsService.range(FROM.minusSeconds(500L * 86_400), TO, null, null, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AnalyticsService.range(FROM, TO, "Mars/Base", null, null))
                .isInstanceOf(ResponseStatusException.class);
        var defaults = AnalyticsService.range(null, null, null, " ", null);
        assertThat(defaults.platform()).isNull();
        assertThat(defaults.zone()).isEqualTo(BOGOTA);
    }

    @Test
    void grantedCountTreatsAndroidMotionAsGranted() {
        assertThat(AnalyticsService.grantedCount(new PermissionSnapshot("granted", "granted", "not-required"))).isEqualTo(3);
        assertThat(AnalyticsService.grantedCount(new PermissionSnapshot("granted", "denied", "unknown"))).isEqualTo(1);
        assertThat(AnalyticsService.grantedCount(new PermissionSnapshot(null, null, null))).isZero();
    }
}
