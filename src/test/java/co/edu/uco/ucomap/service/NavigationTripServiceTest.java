package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.NavigationTripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationTripServiceTest {

    private static final String TRIP_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String DEVICE = "device-1";
    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)";

    @Mock NavigationTripRepository repository;
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

    // ── findAll: expiración de recorridos sin avisos ─────────

    @Test
    void staleTripsAreClosedAsTimeoutAtTheirLastHeartbeat() {
        Instant started = Instant.now().minus(Duration.ofMinutes(40));
        Instant lastSeen = Instant.now().minus(Duration.ofMinutes(25));
        NavigationTrip stale = existing(TripStatus.IN_PROGRESS, null, started, lastSeen);
        Instant recentSeen = Instant.now().minus(Duration.ofMinutes(2));
        NavigationTrip alive = existing(TripStatus.IN_PROGRESS, null, started, recentSeen);
        when(repository.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of(stale, alive));
        when(repository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(stale, alive));

        List<NavigationTrip> result = service.findAll();

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

        service.findAll();

        assertThat(legacy.getStatus()).isEqualTo(TripStatus.ABANDONED);
        assertThat(legacy.getEndedAt()).isEqualTo(started);
        assertThat(legacy.getDurationMs()).isZero();
    }

    @Test
    void nothingIsSavedWhenNoTripIsStale() {
        when(repository.findByStatus(TripStatus.IN_PROGRESS)).thenReturn(List.of());

        service.findAll();

        verify(repository, never()).saveAll(anyList());
    }
}
