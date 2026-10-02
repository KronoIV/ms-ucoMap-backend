package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.PingRequestDTO;
import co.edu.uco.ucomap.model.AppSession;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.repository.AppSessionRepository;
import co.edu.uco.ucomap.repository.DeviceSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock DeviceSessionRepository repository;
    @Mock AppSessionRepository appSessionRepository;
    @Mock SessionEventPublisher eventPublisher;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks DeviceSessionService service;

    private static final String SESSION = "123e4567-e89b-12d3-a456-426614174000";

    private static PingRequestDTO visitPing(String sessionId, Long activeMs, Map<String, Integer> features,
                                            PingRequestDTO.Permissions permissions) {
        return new PingRequestDTO("d1", null, null, "2.1.0", "es-CO", "America/Bogota", "390x844", "4g",
                sessionId, activeMs, features, permissions);
    }

    private void deviceExists(DeviceSession device) {
        when(repository.findByDeviceId("d1")).thenReturn(Optional.of(device));
        when(repository.save(device)).thenReturn(device);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) Mobile Safari | Android",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X)  | iOS",
            "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X)           | iOS",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124    | Desktop-Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) Safari     | Desktop-Mac",
            "Mozilla/5.0 (X11; Linux x86_64) Firefox/125             | Desktop-Linux",
            "PostmanRuntime/7.37.0                                   | Postman",
            "okhttp/4.12.0                                           | App-Native",
            "curl/8.4.0                                              | Web",
    })
    void detectsPlatformFromUserAgent(String userAgent, String expected) {
        assertThat(DeviceSessionService.detectPlatform(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingUserAgentIsUnknown(String userAgent) {
        assertThat(DeviceSessionService.detectPlatform(userAgent)).isEqualTo("Unknown");
    }

    @Test
    void firstPingRegistersDeviceWithHeaderLanguageFallback() {
        when(repository.findByDeviceId("d1")).thenReturn(Optional.empty());
        when(repository.save(any(DeviceSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventPublisher.connectedClients()).thenReturn(1);
        PingRequestDTO body = new PingRequestDTO("d1", "iPhone 14", "iOS 17.4", "1.0.0", null,
                "America/Bogota", "390x844", "wifi");

        DeviceSession saved = service.registerPing("d1", body, "Mozilla/5.0 (iPhone)", "1.2.3.4", "es-CO");

        assertThat(saved.getSessionCount()).isEqualTo(1);
        assertThat(saved.getPlatform()).isEqualTo("iOS");
        assertThat(saved.getLanguage()).isEqualTo("es-CO");
        assertThat(saved.getDeviceModel()).isEqualTo("iPhone 14");
        verify(eventPublisher).publishPing(eq(saved), any());
    }

    @Test
    void repeatedPingCountsPingsButNotVisitsAndKeepsKnownData() {
        DeviceSession known = DeviceSession.builder().deviceId("d1").platform("Android").deviceModel("Pixel 8")
                .language("en-US").sessionCount(4).firstSeen(Instant.parse("2025-01-01T00:00:00Z")).build();
        when(repository.findByDeviceId("d1")).thenReturn(Optional.of(known));
        when(repository.save(known)).thenReturn(known);
        PingRequestDTO body = new PingRequestDTO("d1", null, null, "1.1.0", "es-CO", null, null, null);

        service.registerPing("d1", body, "Mozilla/5.0 (Linux; Android 14)", "5.6.7.8", "en-US");

        assertThat(known.getSessionCount()).isEqualTo(5);
        // Un ping sin sessionId (app anterior) no es una visita ni suma tiempo de uso
        assertThat(known.getVisitCount()).isZero();
        assertThat(known.getTotalActiveMs()).isZero();
        verify(appSessionRepository, never()).save(any());
        assertThat(known.getDeviceModel()).isEqualTo("Pixel 8");
        assertThat(known.getAppVersion()).isEqualTo("1.1.0");
        assertThat(known.getLanguage()).isEqualTo("es-CO");
        assertThat(known.getFirstSeen()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(known.getLastSeen()).isNotNull();
    }

    // ── Visitas y tiempo de uso ──

    @Test
    void firstPingOfVisitCreatesItAndCountsOneVisit() {
        DeviceSession known = DeviceSession.builder().deviceId("d1").visitCount(2).totalActiveMs(60_000).build();
        deviceExists(known);
        when(appSessionRepository.findBySessionId(SESSION)).thenReturn(Optional.empty());

        service.registerPing("d1", visitPing(SESSION, 5_000L, Map.of("tab:rooms", 1),
                new PingRequestDTO.Permissions("granted", "denied", "not-required")), "Mozilla/5.0 (iPhone)", "ip", null);

        ArgumentCaptor<AppSession> saved = ArgumentCaptor.forClass(AppSession.class);
        verify(appSessionRepository).save(saved.capture());
        AppSession visit = saved.getValue();
        assertThat(visit.getDeviceId()).isEqualTo("d1");
        assertThat(visit.getActiveMs()).isEqualTo(5_000L);
        assertThat(visit.getStartedAt()).isCloseTo(Instant.now().minusMillis(5_000), within(Duration.ofSeconds(2)));
        assertThat(visit.getFeatures()).containsEntry("tab:rooms", 1);
        assertThat(visit.getPermissions().getLocation()).isEqualTo("denied");
        assertThat(known.getVisitCount()).isEqualTo(3);
        assertThat(known.getTotalActiveMs()).isEqualTo(65_000L);
        assertThat(known.getPermissions().getMotion()).isEqualTo("not-required");
    }

    @Test
    void laterPingAddsOnlyTheNewActiveTime() {
        DeviceSession known = DeviceSession.builder().deviceId("d1").visitCount(1).totalActiveMs(30_000).build();
        deviceExists(known);
        AppSession visit = AppSession.builder().sessionId(SESSION).deviceId("d1")
                .startedAt(Instant.now().minusSeconds(600)).activeMs(30_000).pings(3)
                .features(new HashMap<>(Map.of("mode:outdoor", 2))).build();
        when(appSessionRepository.findBySessionId(SESSION)).thenReturn(Optional.of(visit));

        service.registerPing("d1", visitPing(SESSION, 90_000L, Map.of("mode:outdoor", 1, "search", 3), null),
                "Mozilla/5.0 (iPhone)", "ip", null);

        assertThat(visit.getActiveMs()).isEqualTo(90_000L);
        assertThat(visit.getPings()).isEqualTo(4);
        // Los contadores llegan acumulados: se conserva el mayor
        assertThat(visit.getFeatures()).containsEntry("mode:outdoor", 2).containsEntry("search", 3);
        assertThat(known.getVisitCount()).isEqualTo(1);
        assertThat(known.getTotalActiveMs()).isEqualTo(90_000L);
    }

    @Test
    void visitReportedByAnotherDeviceIsIgnored() {
        deviceExists(DeviceSession.builder().deviceId("d1").build());
        when(appSessionRepository.findBySessionId(SESSION))
                .thenReturn(Optional.of(AppSession.builder().sessionId(SESSION).deviceId("other").startedAt(Instant.now()).build()));

        service.registerPing("d1", visitPing(SESSION, 10_000L, null, null), "ua", "ip", null);

        verify(appSessionRepository, never()).save(any());
    }

    @Test
    void invalidSessionIdIsNotAVisit() {
        deviceExists(DeviceSession.builder().deviceId("d1").build());

        service.registerPing("d1", visitPing("not-a-uuid", 10_000L, null, null), "ua", "ip", null);

        verify(appSessionRepository, never()).findBySessionId(any());
    }

    @Test
    void activeTimeNeverDecreasesNorExceedsElapsedTime() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Instant now = start.plusSeconds(120);
        // Un aviso atrasado con menos tiempo no resta
        assertThat(DeviceSessionService.acceptedActiveMs(60_000, 40_000L, start, now)).isEqualTo(60_000);
        // Más tiempo del transcurrido (+ margen de red) se recorta
        assertThat(DeviceSessionService.acceptedActiveMs(0, 10_000_000L, start, now))
                .isEqualTo(120_000 + DeviceSessionService.CLOCK_SLACK_MS);
        assertThat(DeviceSessionService.acceptedActiveMs(0, 90_000L, start, now)).isEqualTo(90_000);
        assertThat(DeviceSessionService.acceptedActiveMs(5_000, null, start, now)).isEqualTo(5_000);
        assertThat(DeviceSessionService.acceptedActiveMs(5_000, -1L, start, now)).isEqualTo(5_000);
        // Nunca más de 12 h aunque la visita lleve días abierta
        assertThat(DeviceSessionService.acceptedActiveMs(0, Long.MAX_VALUE, start, start.plus(Duration.ofDays(3))))
                .isEqualTo(DeviceSessionService.MAX_ACTIVE_MS);
    }

    @Test
    void featureKeysAndCountsAreSanitized() {
        Map<String, Integer> target = new HashMap<>();
        Map<String, Integer> reported = new HashMap<>();
        reported.put("tab:kb", 2);
        reported.put("$where", 1);
        reported.put("a.b", 1);
        reported.put("Upper", 1);
        reported.put("zero", 0);
        reported.put("huge", 1_000_000);

        DeviceSessionService.mergeFeatures(target, reported);

        assertThat(target).containsOnlyKeys("tab:kb", "huge").containsEntry("huge", 10_000);
    }

    @Test
    void unknownPermissionStatesAreDropped() {
        var p = DeviceSessionService.sanitizePermissions(
                visitPing(SESSION, 0L, null, new PingRequestDTO.Permissions("granted", "maybe", null)));
        assertThat(p.getCamera()).isEqualTo("granted");
        assertThat(p.getLocation()).isNull();
        assertThat(p.getMotion()).isNull();
        assertThat(DeviceSessionService.sanitizePermissions(
                visitPing(SESSION, 0L, null, new PingRequestDTO.Permissions("x", "y", "z")))).isNull();
    }

    @Test
    void pingWithoutOpenDashboardsSkipsStats() {
        // Calcular estadísticas en cada ping agotó la memoria en la prueba de carga (~870 usuarios)
        when(repository.findByDeviceId("d1")).thenReturn(Optional.empty());
        when(repository.save(any(DeviceSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventPublisher.connectedClients()).thenReturn(0);

        service.registerPing("d1", null, "Mozilla/5.0 (iPhone)", "1.2.3.4", "es-CO");

        verify(repository, never()).aggregateByPlatform();
        verify(repository, never()).findAll();
        verify(eventPublisher, never()).publishPing(any(), any());
    }

    @Test
    void statsAggregateDevicesSessionsAndPlatforms() {
        when(repository.aggregateByPlatform()).thenReturn(List.of(
                new DeviceSessionRepository.PlatformStats("iOS", 2, 5, 3, 120_000),
                new DeviceSessionRepository.PlatformStats("Android", 1, 5, 1, 30_000),
                new DeviceSessionRepository.PlatformStats(null, 1, 1, 0, 0)));
        when(repository.countByLastSeenGreaterThanEqual(any())).thenReturn(1L, 3L);

        var stats = service.getStats();

        assertThat(stats.totalDevices()).isEqualTo(4);
        assertThat(stats.totalSessions()).isEqualTo(11);
        assertThat(stats.totalVisits()).isEqualTo(4);
        assertThat(stats.totalActiveMs()).isEqualTo(150_000);
        assertThat(stats.byPlatform())
                .containsEntry("iOS", 2L).containsEntry("Android", 1L).containsEntry("Unknown", 1L);
        assertThat(stats.activeNow()).isEqualTo(1);
        assertThat(stats.activeToday()).isEqualTo(3);
        verify(repository, never()).findAll();
    }

    @Test
    void searchIsLiteralAndSizeIsCapped() {
        // Un texto como ".*" no debe convertirse en una expresión regular que recorra todo
        service.findPage(".*", "active", 0, 1_000);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(DeviceSession.class));
        String json = query.getValue().getQueryObject().toString();
        assertThat(json).contains("\\Q.*\\E").contains("lastSeen");
        assertThat(query.getValue().getLimit()).isEqualTo(PageResponse.MAX_SIZE);
        assertThat(query.getValue().getSortObject().toJson()).contains("\"lastSeen\": -1");
    }

    @Test
    void pageReportsTotals() {
        when(mongoTemplate.find(any(Query.class), eq(DeviceSession.class)))
                .thenReturn(List.of(DeviceSession.builder().deviceId("d1").build()));
        when(mongoTemplate.count(any(Query.class), eq(DeviceSession.class))).thenReturn(21L);

        PageResponse<DeviceSession> page = service.findPage(null, "all", 2, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
    }
}
