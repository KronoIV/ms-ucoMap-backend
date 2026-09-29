package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.NavigationTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationTripService {

    /** Un recorrido sin cierre pasado este tiempo se da por abandonado (la app se cerró sin avisar). */
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final NavigationTripRepository repository;

    public void report(TripReportDTO dto, String userAgent) {
        Instant now = Instant.now();
        Optional<NavigationTrip> existing = repository.findByTripId(dto.tripId());

        if (existing.isEmpty()) {
            boolean ended = dto.status() != TripStatus.IN_PROGRESS;
            NavigationTrip trip = NavigationTrip.builder()
                    .tripId(dto.tripId())
                    .deviceId(dto.deviceId())
                    .platform(DeviceSessionService.detectPlatform(userAgent))
                    .startedAt(ended && dto.durationMs() != null ? now.minusMillis(dto.durationMs()) : now)
                    .build();
            apply(trip, dto, now);
            repository.save(trip);
            log.info("Recorrido {} — trip={} destino={}", dto.status(), dto.tripId(), dto.roomName());
            return;
        }

        NavigationTrip trip = existing.get();
        if (!trip.getDeviceId().equals(dto.deviceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getMessage());
        }
        // Reintentos del mismo aviso: un recorrido cerrado no se reabre ni se modifica
        if (trip.getStatus() != TripStatus.IN_PROGRESS || dto.status() == TripStatus.IN_PROGRESS) return;

        apply(trip, dto, now);
        repository.save(trip);
        log.info("Recorrido {} — trip={} motivo={} duracion={}ms",
                dto.status(), dto.tripId(), trip.getEndReason(), dto.durationMs());
    }

    public List<NavigationTrip> findAll() {
        List<NavigationTrip> stale = repository.findByStatusAndStartedAtBefore(
                TripStatus.IN_PROGRESS, Instant.now().minus(STALE_AFTER));
        if (!stale.isEmpty()) {
            stale.forEach(t -> {
                t.setStatus(TripStatus.ABANDONED);
                t.setEndReason("timeout");
            });
            repository.saveAll(stale);
        }
        return repository.findAllByOrderByStartedAtDesc();
    }

    private void apply(NavigationTrip trip, TripReportDTO dto, Instant now) {
        trip.setRoomId(dto.roomId());
        trip.setRoomName(dto.roomName());
        trip.setBuilding(dto.building());
        trip.setStartMode(dto.startMode());
        trip.setStartDistanceM(dto.startDistanceM());
        trip.setStartAccuracyM(dto.startAccuracyM());
        trip.setStatus(dto.status());
        if (dto.status() == TripStatus.IN_PROGRESS) return;

        trip.setEndedAt(now);
        trip.setEndReason(dto.endReason() != null && !dto.endReason().isBlank() ? dto.endReason() : "closed");
        trip.setDurationMs(dto.durationMs());
        trip.setBuildingReachedMs(dto.buildingReachedMs());
        trip.setLocalizedMs(dto.localizedMs());
        trip.setOutdoorRouteM(dto.outdoorRouteM());
        trip.setIndoorRouteM(dto.indoorRouteM());
        trip.setModeSwitches(dto.modeSwitches());
        trip.setVpsFailures(dto.vpsFailures());
        trip.setUsedAR(dto.usedAR());
    }
}
