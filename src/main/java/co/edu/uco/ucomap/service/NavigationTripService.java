package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.dto.TripFilter;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.dto.TripSummaryDTO;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.repository.NavigationTripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationTripService {

    /** La app avisa cada minuto mientras navega: sin avisos en este tiempo, el usuario salió sin cerrar. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final String TIMEOUT_REASON = "timeout";

    private final NavigationTripRepository repository;
    private final MongoTemplate mongoTemplate;

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
            try {
                repository.save(trip);
            } catch (DuplicateKeyException e) {
                // Inicio y cierre llegaron a la vez (p. ej. al despertar el servidor): el otro ya lo creó
                report(dto, userAgent);
                return;
            }
            log.info("Recorrido {} — trip={} destino={}", dto.status(), dto.tripId(), dto.roomName());
            return;
        }

        NavigationTrip trip = existing.get();
        if (!trip.getDeviceId().equals(dto.deviceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getMessage());
        }
        boolean inProgress = trip.getStatus() == TripStatus.IN_PROGRESS;
        if (inProgress && dto.status() == TripStatus.IN_PROGRESS) {
            // Solo la hora: guardar el documento leído podría pisar un cierre que llegó a la vez
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("tripId").is(dto.tripId()).and("status").is(TripStatus.IN_PROGRESS)),
                    Update.update("lastSeenAt", now), NavigationTrip.class);
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

    public PageResponse<NavigationTrip> findPage(TripFilter filter, int page, int size) {
        expireStaleTrips();
        int p = PageResponse.safePage(page);
        int s = PageResponse.safeSize(size);
        Query query = new Query(filter.toCriteria())
                .with(Sort.by(Sort.Direction.DESC, "startedAt"))
                .skip((long) p * s)
                .limit(s);
        List<NavigationTrip> content = mongoTemplate.find(query, NavigationTrip.class);
        long total = mongoTemplate.count(new Query(filter.toCriteria()), NavigationTrip.class);
        return PageResponse.of(content, p, s, total);
    }

    /** Métricas del rango filtrado; solo trae de la base los campos necesarios. */
    public TripSummaryDTO summarize(TripFilter filter) {
        expireStaleTrips();
        Query query = new Query(filter.toCriteria());
        query.fields().include("status", "endReason", "durationMs", "localizedMs", "vpsFailures",
                "usedAR", "building", "roomName");

        Stats all = new Stats(null, null);
        Map<String, Stats> byDestination = new LinkedHashMap<>();
        Map<String, Long> abandonReasons = new HashMap<>();
        for (NavigationTrip trip : mongoTemplate.find(query, NavigationTrip.class)) {
            all.add(trip);
            String key = trip.getBuilding() + "|" + trip.getRoomName();
            byDestination.computeIfAbsent(key, k -> new Stats(trip.getBuilding(), trip.getRoomName())).add(trip);
            if (trip.getStatus() == TripStatus.ABANDONED) {
                String reason = trip.getEndReason() != null ? trip.getEndReason() : "closed";
                abandonReasons.merge(reason, 1L, Long::sum);
            }
        }

        List<String> buildings = mongoTemplate.findDistinct(new Query(), "building", NavigationTrip.class, String.class)
                .stream().filter(b -> b != null && !b.isBlank()).sorted().toList();

        return new TripSummaryDTO(
                all.total, all.finished, all.arrived, all.total - all.finished,
                mean(all.arrivalDurations), median(all.arrivalDurations),
                mean(all.localized), mean(all.vpsFailures),
                byDestination.values().stream()
                        .sorted(Comparator.comparingLong((Stats st) -> st.total).reversed())
                        .map(st -> new TripSummaryDTO.DestinationStats(st.building, st.roomName, st.total,
                                st.finished, st.arrived, mean(st.arrivalDurations), median(st.arrivalDurations)))
                        .toList(),
                abandonReasons.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> new TripSummaryDTO.ReasonCount(e.getKey(), e.getValue()))
                        .toList(),
                buildings);
    }

    /** CSV (separado por comas, UTF-8) con todos los recorridos del rango, del más reciente al más antiguo. */
    public String exportCsv(TripFilter filter) {
        expireStaleTrips();
        Query query = new Query(filter.toCriteria()).with(Sort.by(Sort.Direction.DESC, "startedAt"));
        StringBuilder csv = new StringBuilder(
                "Inicio,Fin,Destino,Edificio,Resultado,Motivo,Duración (s),Inicio en,Dist. inicial (m),"
                        + "Precisión GPS (m),Ruta ext. (m),Ruta int. (m),Llegó al edificio (s),Ubicación VPS (s),"
                        + "Fallos VPS,Cambios modo,Usó AR,Plataforma,Dispositivo\n");
        try (Stream<NavigationTrip> trips = mongoTemplate.stream(query, NavigationTrip.class)) {
            trips.forEach(t -> csv.append(String.join(",",
                    cell(date(t.getStartedAt())), cell(date(t.getEndedAt())),
                    cell(t.getRoomName()), cell(t.getBuilding()), cell(statusLabel(t.getStatus())),
                    cell(t.getEndReason()), seconds(t.getDurationMs()), cell(t.getStartMode()),
                    number(t.getStartDistanceM()), number(t.getStartAccuracyM()),
                    number(t.getOutdoorRouteM()), number(t.getIndoorRouteM()),
                    seconds(t.getBuildingReachedMs()), seconds(t.getLocalizedMs()),
                    number(t.getVpsFailures()), number(t.getModeSwitches()),
                    t.getUsedAR() == null ? "" : (t.getUsedAR() ? "sí" : "no"),
                    cell(t.getPlatform()), cell(t.getDeviceId()))).append('\n'));
        }
        return csv.toString();
    }

    /** Cierra como abandonados los recorridos sin avisos recientes (la app se cerró sin poder avisar). */
    public void expireStaleTrips() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        repository.findByStatus(TripStatus.IN_PROGRESS).stream()
                .filter(t -> lastSeen(t).isBefore(cutoff))
                .forEach(t -> {
                    Instant last = lastSeen(t);
                    // Condicionado a que siga igual: un cierre o un aviso que llegó mientras tanto gana
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(t.getId())
                                    .and("status").is(TripStatus.IN_PROGRESS)
                                    .and("lastSeenAt").is(t.getLastSeenAt())),
                            new Update()
                                    .set("status", TripStatus.ABANDONED)
                                    .set("endReason", TIMEOUT_REASON)
                                    .set("endedAt", last)
                                    .set("durationMs", Duration.between(t.getStartedAt(), last).toMillis()),
                            NavigationTrip.class);
                });
    }

    private static Instant lastSeen(NavigationTrip trip) {
        return trip.getLastSeenAt() != null ? trip.getLastSeenAt() : trip.getStartedAt();
    }

    /** La app reenvía los cierres que no llegaron: el fin real es inicio + duración, no la hora del reenvío. */
    private static Instant endedAt(NavigationTrip trip, TripReportDTO dto, Instant now) {
        if (dto.durationMs() == null || trip.getStartedAt() == null) return now;
        Instant byDuration = trip.getStartedAt().plusMillis(dto.durationMs());
        return byDuration.isBefore(now) ? byDuration : now;
    }

    private void apply(NavigationTrip trip, TripReportDTO dto, Instant now) {
        trip.setRoomId(dto.roomId());
        trip.setRoomName(dto.roomName());
        trip.setBuilding(dto.building());
        trip.setStartMode(dto.startMode());
        trip.setStartDistanceM(dto.startDistanceM());
        trip.setStartAccuracyM(dto.startAccuracyM());
        if (dto.startLat() != null && dto.startLng() != null) {
            trip.setStartLat(round4(dto.startLat()));
            trip.setStartLng(round4(dto.startLng()));
        }
        trip.setStatus(dto.status());
        if (dto.status() == TripStatus.IN_PROGRESS) return;

        trip.setEndedAt(endedAt(trip, dto, now));
        trip.setEndReason(dto.endReason() != null && !dto.endReason().isBlank() ? dto.endReason() : "closed");
        trip.setDurationMs(dto.durationMs());
        trip.setBuildingReachedMs(dto.buildingReachedMs());
        trip.setLocalizedMs(dto.localizedMs());
        trip.setOutdoorRouteM(dto.outdoorRouteM());
        trip.setIndoorRouteM(dto.indoorRouteM());
        trip.setModeSwitches(dto.modeSwitches());
        trip.setVpsFailures(dto.vpsFailures());
        trip.setUsedAR(dto.usedAR());
        trip.setTransition(dto.transition());
    }

    // ── Métricas ──────────────────────────────────────────────

    /** Aunque el cliente envíe más precisión, no se guarda más de ~11 m. */
    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }

    private static final class Stats {
        final String building;
        final String roomName;
        long total;
        long finished;
        long arrived;
        final List<Long> arrivalDurations = new ArrayList<>();
        final List<Long> localized = new ArrayList<>();
        final List<Long> vpsFailures = new ArrayList<>();

        Stats(String building, String roomName) {
            this.building = building;
            this.roomName = roomName;
        }

        void add(NavigationTrip t) {
            total++;
            if (t.getStatus() == TripStatus.IN_PROGRESS) return;
            finished++;
            if (t.getLocalizedMs() != null) localized.add(t.getLocalizedMs());
            if (Boolean.TRUE.equals(t.getUsedAR())) {
                vpsFailures.add(t.getVpsFailures() != null ? t.getVpsFailures().longValue() : 0L);
            }
            if (t.getStatus() == TripStatus.ARRIVED) {
                arrived++;
                if (t.getDurationMs() != null) arrivalDurations.add(t.getDurationMs());
            }
        }
    }

    private static Double mean(List<Long> values) {
        return values.isEmpty() ? null : values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static Double median(List<Long> values) {
        if (values.isEmpty()) return null;
        List<Long> sorted = values.stream().sorted().toList();
        int m = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(m) : (sorted.get(m - 1) + sorted.get(m)) / 2.0;
    }

    // ── CSV ───────────────────────────────────────────────────

    private static final DateTimeFormatter CSV_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("America/Bogota"));

    private static String date(Instant instant) {
        return instant == null ? null : CSV_DATE.format(instant);
    }

    private static String seconds(Long ms) {
        return ms == null ? "" : String.format(Locale.ROOT, "%.1f", ms / 1000.0);
    }

    private static String number(Number n) {
        if (n == null) return "";
        return n instanceof Double d ? String.format(Locale.ROOT, "%.1f", d) : n.toString();
    }

    private static String statusLabel(TripStatus status) {
        if (status == null) return null;
        return switch (status) {
            case ARRIVED -> "Llegó";
            case ABANDONED -> "Abandonó";
            case IN_PROGRESS -> "En curso";
        };
    }

    /** Celda de texto entre comillas; neutraliza fórmulas (los nombres de destino los envía la app pública). */
    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }
}
