package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.TripFilter;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.dto.TripSummaryDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.TransitionStats;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.NavigationTripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationTripServiceTest {

    private static final String TRIP_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String DEVICE = "device-1";
    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)";
    private static final TripFilter NO_FILTER = new TripFilter(null, null, null);

    @Mock NavigationTripRepository repository;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks NavigationTripService service;

    private static TripReportDTO report(String deviceId, TripStatus status, String endReason, Long durationMs) {
        return new TripReportDTO(TRIP_ID, deviceId, status, "co101", "CO 101", "CO", "outdoor",
                120.0, 8.0, endReason, durationMs, 60_000L, 90_000L, 110.0, 25.0, 1, 2, true);
    }

    private static NavigationTrip existing(TripStatus status, String endReason, Instant startedAt, Instant lastSeenAt) {
        return NavigationTrip.builder().id("db1").tripId(TRIP_ID).deviceId(DEVICE)
                .status(status).endReason(endReason).startedAt(startedAt).lastSeenAt(lastSeenAt).build();
    }

    private NavigationTrip savedTrip() {
        ArgumentCaptor<NavigationTrip> captor = ArgumentCaptor.forClass(NavigationTrip.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    // ── report ────────────────────────────────────────────────

    @Test
    void startReportCreatesTripInProgress() {
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.empty());

        service.report(report(DEVICE, TripStatus.IN_PROGRESS, null, null), IPHONE_UA);

        NavigationTrip trip = savedTrip();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getPlatform()).isEqualTo("iOS");
        assertThat(trip.getRoomName()).isEqualTo("CO 101");
        assertThat(trip.getEndedAt()).isNull();
        assertThat(trip.getDurationMs()).isNull();
        assertThat(trip.getStartedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
    }

    @Test
    void startPointIsStoredWithAtMostFourDecimals() {
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.empty());
        TripReportDTO r = report(DEVICE, TripStatus.IN_PROGRESS, null, null);
        TripReportDTO withStart = new TripReportDTO(r.tripId(), r.deviceId(), r.status(), r.roomId(), r.roomName(),
                r.building(), r.startMode(), r.startDistanceM(), r.startAccuracyM(), r.endReason(), r.durationMs(),
                r.buildingReachedMs(), r.localizedMs(), r.outdoorRouteM(), r.indoorRouteM(), r.modeSwitches(),
                r.vpsFailures(), r.usedAR(), 6.14987954, -75.36605391);

        service.report(withStart, IPHONE_UA);

        NavigationTrip trip = savedTrip();
        assertThat(trip.getStartLat()).isEqualTo(6.1499);
        assertThat(trip.getStartLng()).isEqualTo(-75.3661);
    }

    @Test
    void finalReportWithoutStartBackdatesStartFromDuration() {
        // El aviso de inicio se perdió: el cierre crea el recorrido completo
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.empty());

        service.report(report(DEVICE, TripStatus.ARRIVED, "ar-arrival", 300_000L), IPHONE_UA);

        NavigationTrip trip = savedTrip();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED);
        assertThat(trip.getEndReason()).isEqualTo("ar-arrival");
        assertThat(trip.getDurationMs()).isEqualTo(300_000L);
        assertThat(Duration.between(trip.getStartedAt(), trip.getEndedAt()).toMillis())
                .isCloseTo(300_000L, within(1_000L));
        assertThat(trip.getUsedAR()).isTrue();
    }

    @Test
    void finalReportStoresHowTheIndoorTransitionWasDecided() {
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.empty());
        TripReportDTO r = report(DEVICE, TripStatus.ARRIVED, "ar-arrival", 300_000L);
        TransitionStats stats = TransitionStats.builder()
                .trigger("auto-door").score(72).distanceM(9.5).accuracyM(5.0).approachMs(41_000L)
                .switchToVpsMs(2_300L).prewarmed(true).cancellations(0).rejections(1).returnsToOutdoor(0).falseIndoor(0)
                .build();
        TripReportDTO withTransition = new TripReportDTO(r.tripId(), r.deviceId(), r.status(), r.roomId(), r.roomName(),
                r.building(), r.startMode(), r.startDistanceM(), r.startAccuracyM(), r.endReason(), r.durationMs(),
                r.buildingReachedMs(), r.localizedMs(), r.outdoorRouteM(), r.indoorRouteM(), r.modeSwitches(),
                r.vpsFailures(), r.usedAR(), null, null, stats);

        service.report(withTransition, IPHONE_UA);

        assertThat(savedTrip().getTransition()).isEqualTo(stats);
    }

    @Test
    void heartbeatOnlyRefreshesLastSeen() {
        Instant started = Instant.now().minusSeconds(120);
        NavigationTrip trip = existing(TripStatus.IN_PROGRESS, null, started, started);
        trip.setRoomName("Original");
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(trip));

        service.report(report(DEVICE, TripStatus.IN_PROGRESS, null, null), IPHONE_UA);

        assertThat(trip.getLastSeenAt()).isAfter(started);
        assertThat(trip.getRoomName()).isEqualTo("Original");
        assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        verify(repository).save(trip);
    }

    @Test
    void closingReportFinishesTrip() {
        Instant started = Instant.now().minusSeconds(300);
        NavigationTrip trip = existing(TripStatus.IN_PROGRESS, null, started, started);
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(trip));

        service.report(report(DEVICE, TripStatus.ABANDONED, null, 300_000L), IPHONE_UA);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.ABANDONED);
        assertThat(trip.getEndReason()).isEqualTo("closed");
        assertThat(trip.getEndedAt()).isNotNull();
        assertThat(trip.getStartedAt()).isEqualTo(started);
        verify(repository).save(trip);
    }

    @Test
    void anotherDeviceCannotOverwriteTheTrip() {
        Instant started = Instant.now().minusSeconds(60);
        when(repository.findByTripId(TRIP_ID))
                .thenReturn(Optional.of(existing(TripStatus.IN_PROGRESS, null, started, started)));

        assertThatThrownBy(() -> service.report(report("intruder", TripStatus.ARRIVED, "ar-arrival", 1L), IPHONE_UA))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void finishedTripIsNotReopenedOrOverwritten() {
        Instant started = Instant.now().minusSeconds(600);
        NavigationTrip trip = existing(TripStatus.ARRIVED, "ar-arrival", started, started);
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(trip));

        service.report(report(DEVICE, TripStatus.IN_PROGRESS, null, null), IPHONE_UA);
        service.report(report(DEVICE, TripStatus.ABANDONED, "closed", 5L), IPHONE_UA);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED);
        assertThat(trip.getEndReason()).isEqualTo("ar-arrival");
        verify(repository, never()).save(any());
    }

    @Test
    void lateArrivalCorrectsAutomaticTimeout() {
        Instant started = Instant.now().minusSeconds(1800);
        NavigationTrip trip = existing(TripStatus.ABANDONED, "timeout", started, started.plusSeconds(60));
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(trip));

        service.report(report(DEVICE, TripStatus.ARRIVED, "building-arrival", 1_500_000L), IPHONE_UA);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.ARRIVED);
        assertThat(trip.getEndReason()).isEqualTo("building-arrival");
        assertThat(trip.getDurationMs()).isEqualTo(1_500_000L);
        verify(repository).save(trip);
    }

    // ── Expiración de recorridos sin avisos (al consultar el panel) ──

    @Test
    void staleTripsAreClosedAsTimeoutAtTheirLastHeartbeat() {
        Instant started = Instant.now().minus(Duration.ofMinutes(40));
        Instant lastSeen = Instant.now().minus(Duration.ofMinutes(25));
        NavigationTrip stale = existing(TripStatus.IN_PROGRESS, null, started, lastSeen);
        Instant recentSeen = Instant.now().minus(Duration.ofMinutes(2));
        NavigationTrip alive = existing(TripStatus.IN_PROGRESS, null, started, recentSeen);
        when(repository.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(stale, alive));
        when(mongoTemplate.find(any(Query.class), eq(NavigationTrip.class))).thenReturn(List.of(stale, alive));
        when(mongoTemplate.count(any(Query.class), eq(NavigationTrip.class))).thenReturn(2L);

        List<NavigationTrip> result = service.findPage(NO_FILTER, 0, 25).content();

        assertThat(result).containsExactly(stale, alive);
        assertThat(stale.getStatus()).isEqualTo(TripStatus.ABANDONED);
        assertThat(stale.getEndReason()).isEqualTo("timeout");
        assertThat(stale.getEndedAt()).isEqualTo(lastSeen);
        assertThat(stale.getDurationMs()).isEqualTo(Duration.ofMinutes(15).toMillis());
        assertThat(alive.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
        verify(repository).saveAll(List.of(stale));
    }

    @Test
    void tripWithoutHeartbeatUsesStartAsLastSeen() {
        Instant started = Instant.now().minus(Duration.ofMinutes(30));
        NavigationTrip legacy = existing(TripStatus.IN_PROGRESS, null, started, null);
        when(repository.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(legacy));

        service.findPage(NO_FILTER, 0, 25);

        assertThat(legacy.getStatus()).isEqualTo(TripStatus.ABANDONED);
        assertThat(legacy.getEndedAt()).isEqualTo(started);
        assertThat(legacy.getDurationMs()).isZero();
    }

    @Test
    void nothingIsSavedWhenNoTripIsStale() {
        when(repository.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of());

        service.summarize(NO_FILTER);

        verify(repository, never()).saveAll(anyList());
    }

    // ── Paginación, resumen y exportación ─────────────────────

    @Test
    void pageSizeIsCappedAndPagesAreSkipped() {
        when(mongoTemplate.count(any(Query.class), eq(NavigationTrip.class))).thenReturn(1_050L);

        PageResponse<NavigationTrip> page = service.findPage(NO_FILTER, 3, 5_000);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(NavigationTrip.class));
        assertThat(query.getValue().getLimit()).isEqualTo(PageResponse.MAX_SIZE);
        assertThat(query.getValue().getSkip()).isEqualTo(3L * PageResponse.MAX_SIZE);
        assertThat(query.getValue().getSortObject().toJson()).contains("\"startedAt\": -1");
        assertThat(page.totalElements()).isEqualTo(1_050L);
        assertThat(page.totalPages()).isEqualTo(11);
    }

    @Test
    void filterRestrictsDateRangeAndBuilding() {
        Instant from = Instant.parse("2026-09-01T05:00:00Z");
        Instant to = Instant.parse("2026-09-30T04:59:59Z");

        service.findPage(new TripFilter(from, to, "CO"), 0, 25);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(NavigationTrip.class));
        var criteria = query.getValue().getQueryObject();
        assertThat(criteria.get("building")).isEqualTo("CO");
        assertThat(criteria.get("startedAt", org.bson.Document.class))
                .containsEntry("$gte", from).containsEntry("$lte", to);
    }

    private static NavigationTrip finished(String room, TripStatus status, String reason, Long durationMs,
                                           Long localizedMs, boolean usedAR, Integer vpsFailures) {
        return NavigationTrip.builder().building("CO").roomName(room).status(status).endReason(reason)
                .durationMs(durationMs).localizedMs(localizedMs).usedAR(usedAR).vpsFailures(vpsFailures).build();
    }

    @Test
    void summaryComputesSuccessRateTimesAndReasons() {
        when(mongoTemplate.find(any(Query.class), eq(NavigationTrip.class))).thenReturn(List.of(
                finished("CO 101", TripStatus.ARRIVED, "ar-arrival", 60_000L, 5_000L, true, 1),
                finished("CO 101", TripStatus.ARRIVED, "ar-arrival", 120_000L, 7_000L, true, 3),
                finished("CO 101", TripStatus.ARRIVED, "building-arrival", 300_000L, null, false, null),
                finished("CO 202", TripStatus.ABANDONED, "timeout", 50_000L, null, false, null),
                finished("CO 202", TripStatus.ABANDONED, null, 10_000L, null, false, null),
                finished("CO 202", TripStatus.IN_PROGRESS, null, null, null, false, null)));
        when(mongoTemplate.findDistinct(any(Query.class), eq("building"), eq(NavigationTrip.class), eq(String.class)))
                .thenReturn(List.of("EDC", "CO", ""));

        TripSummaryDTO s = service.summarize(NO_FILTER);

        assertThat(s.total()).isEqualTo(6);
        assertThat(s.finished()).isEqualTo(5);
        assertThat(s.arrived()).isEqualTo(3);
        assertThat(s.inProgress()).isEqualTo(1);
        assertThat(s.avgArrivalMs()).isEqualTo(160_000.0);
        assertThat(s.medianArrivalMs()).isEqualTo(120_000.0);
        assertThat(s.avgLocalizedMs()).isEqualTo(6_000.0);
        assertThat(s.avgVpsFailures()).isEqualTo(2.0);
        assertThat(s.abandonReasons()).extracting(TripSummaryDTO.ReasonCount::reason)
                .containsExactlyInAnyOrder("timeout", "closed");
        assertThat(s.byDestination()).hasSize(2);
        assertThat(s.byDestination().get(0).total()).isEqualTo(3);
        assertThat(s.buildings()).containsExactly("CO", "EDC");
    }

    @Test
    void emptyRangeHasNoAverages() {
        TripSummaryDTO s = service.summarize(NO_FILTER);

        assertThat(s.total()).isZero();
        assertThat(s.avgArrivalMs()).isNull();
        assertThat(s.medianArrivalMs()).isNull();
    }

    @Test
    void csvExportNeutralizesFormulasFromAppData() {
        // roomName llega desde la app pública: Excel no debe ejecutarlo como fórmula
        NavigationTrip trip = NavigationTrip.builder()
                .startedAt(Instant.parse("2026-09-29T15:00:00Z")).roomName("=HYPERLINK(\"http://x\")")
                .building("CO").status(TripStatus.ARRIVED).durationMs(90_500L).usedAR(true).build();
        when(mongoTemplate.stream(any(Query.class), eq(NavigationTrip.class))).thenReturn(Stream.of(trip));

        String[] lines = service.exportCsv(NO_FILTER).split("\n");

        assertThat(lines[0]).startsWith("Inicio,Fin,Destino,Edificio,Resultado");
        assertThat(lines[1])
                .startsWith("\"2026-09-29 10:00:00\",,\"'=HYPERLINK(\"\"http://x\"\")\",\"CO\",\"Llegó\"")
                .contains(",90.5,")
                .contains(",sí,");
    }
}
