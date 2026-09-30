package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.PingRequestDTO;
import co.edu.uco.ucomap.model.DeviceSession;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceSessionServiceTest {

    @Mock DeviceSessionRepository repository;
    @Mock SessionEventPublisher eventPublisher;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks DeviceSessionService service;

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
    void repeatedPingIncrementsCounterAndKeepsKnownData() {
        DeviceSession known = DeviceSession.builder().deviceId("d1").platform("Android").deviceModel("Pixel 8")
                .language("en-US").sessionCount(4).firstSeen(Instant.parse("2025-01-01T00:00:00Z")).build();
        when(repository.findByDeviceId("d1")).thenReturn(Optional.of(known));
        when(repository.save(known)).thenReturn(known);
        PingRequestDTO body = new PingRequestDTO("d1", null, null, "1.1.0", "es-CO", null, null, null);

        service.registerPing("d1", body, "Mozilla/5.0 (Linux; Android 14)", "5.6.7.8", "en-US");

        assertThat(known.getSessionCount()).isEqualTo(5);
        assertThat(known.getDeviceModel()).isEqualTo("Pixel 8");
        assertThat(known.getAppVersion()).isEqualTo("1.1.0");
        assertThat(known.getLanguage()).isEqualTo("es-CO");
        assertThat(known.getFirstSeen()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(known.getLastSeen()).isNotNull();
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
                new DeviceSessionRepository.PlatformStats("iOS", 2, 5),
                new DeviceSessionRepository.PlatformStats("Android", 1, 5),
                new DeviceSessionRepository.PlatformStats(null, 1, 1)));
        when(repository.countByLastSeenGreaterThanEqual(any())).thenReturn(1L, 3L);

        var stats = service.getStats();

        assertThat(stats.totalDevices()).isEqualTo(4);
        assertThat(stats.totalSessions()).isEqualTo(11);
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
