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

    /** La app avisa cada minuto mientras navega: sin avisos en este tiempo, el usuario salió sin cerrar. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final String TIMEOUT_REASON = "timeout";

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
                    .lastSeenAt(now)
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
        boolean inProgress = trip.getStatus() == TripStatus.IN_PROGRESS;
        if (inProgress && dto.status() == TripStatus.IN_PROGRESS) {
            trip.setLastSeenAt(now);
            repository.save(trip);
            return;
        }
        // Un cierre tardío (p. ej. volvió a la app y llegó) corrige el abandono automático
        boolean timedOut = trip.getStatus() == TripStatus.ABANDONED && TIMEOUT_REASON.equals(trip.getEndReason());
        if (dto.status() == TripStatus.IN_PROGRESS || !(inProgress || timedOut)) return;

        trip.setLastSeenAt(now);
        apply(trip, dto, now);
        repository.save(trip);
        log.info("Recorrido {} — trip={} motivo={} duracion={}ms",
                dto.status(), dto.tripId(), trip.getEndReason(), dto.durationMs());
    }

    public List<NavigationTrip> findAll() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        List<NavigationTrip> stale = repository.findByStatus(TripStatus.IN_PROGRESS).stream()
                .filter(t -> lastSeen(t).isBefore(cutoff))
                .toList();
        if (!stale.isEmpty()) {
            stale.forEach(t -> {
                Instant last = lastSeen(t);
                t.setStatus(TripStatus.ABANDONED);
                t.setEndReason(TIMEOUT_REASON);
                t.setEndedAt(last);
                t.setDurationMs(Duration.between(t.getStartedAt(), last).toMillis());
            });
            repository.saveAll(stale);
        }
        return repository.findAllByOrderByStartedAtDesc();
    }

    private static Instant lastSeen(NavigationTrip trip) {
        return trip.getLastSeenAt() != null ? trip.getLastSeenAt() : trip.getStartedAt();
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
