package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.PingRequestDTO;
import co.edu.uco.ucomap.dto.StatsDTO;
import co.edu.uco.ucomap.model.AppSession;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.model.PermissionSnapshot;
import co.edu.uco.ucomap.repository.AppSessionRepository;
import co.edu.uco.ucomap.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(10);
    static final Duration DAY_WINDOW    = Duration.ofHours(24);
    private static final int MAX_QUERY_LENGTH = 100;

    /** Ninguna visita real dura más; un valor mayor es un error del cliente. */
    static final long MAX_ACTIVE_MS = Duration.ofHours(12).toMillis();
    /** Margen por la latencia de red entre el reloj del teléfono y el del servidor. */
    static final long CLOCK_SLACK_MS = 30_000;
    private static final Pattern SESSION_ID = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Pattern FEATURE_KEY = Pattern.compile("^[a-z][a-z0-9:-]{0,39}$");
    private static final int MAX_FEATURES = 40;
    private static final int MAX_FEATURE_COUNT = 10_000;
    private static final Set<String> ACCESS_STATES =
            Set.of("granted", "prompt", "denied", "blocked", "unavailable", "error");
    private static final Set<String> MOTION_STATES =
            Set.of("granted", "denied", "not-required", "unknown");

    private final DeviceSessionRepository repository;
    private final AppSessionRepository     appSessionRepository;
    private final SessionEventPublisher    eventPublisher;
    private final MongoTemplate            mongoTemplate;

    /**
     * Registra o actualiza la sesion de un dispositivo.
     * Los campos del dispositivo vienen del body; IP y User-Agent de los headers HTTP.
     * Language: se prefiere el valor del body; si no viene, se usa el header Accept-Language.
     * Si el ping trae sessionId, también actualiza la visita y su tiempo de uso real.
     */
    public DeviceSession registerPing(String deviceId, PingRequestDTO body,
                                      String userAgent, String ip, String headerLang) {

        String lang     = (body != null && body.language() != null && !body.language().isBlank())
                          ? body.language() : headerLang;
        String platform = detectPlatform(userAgent);
        PermissionSnapshot permissions = sanitizePermissions(body);
        VisitUpdate visit = trackVisit(deviceId, body, platform, userAgent, lang, permissions);

        Optional<DeviceSession> existing = repository.findByDeviceId(deviceId);

        if (existing.isPresent()) {
            DeviceSession session = existing.get();
            session.setLastSeen(Instant.now());
            session.setSessionCount(session.getSessionCount() + 1);
            session.setUserAgent(userAgent);
            session.setIpAddress(ip);
            session.setPlatform(platform);
            if (lang != null) session.setLanguage(lang);
            if (body != null) {
                if (body.deviceModel()      != null) session.setDeviceModel(body.deviceModel());
                if (body.osVersion()        != null) session.setOsVersion(body.osVersion());
                if (body.appVersion()       != null) session.setAppVersion(body.appVersion());
                if (body.timezone()         != null) session.setTimezone(body.timezone());
                if (body.screenResolution() != null) session.setScreenResolution(body.screenResolution());
                if (body.networkType()      != null) session.setNetworkType(body.networkType());
            }
            if (permissions != null) session.setPermissions(permissions);
            if (visit.created()) session.setVisitCount(session.getVisitCount() + 1);
            session.setTotalActiveMs(session.getTotalActiveMs() + visit.activeDeltaMs());
            log.debug("Ping actualizado — deviceId={} platform={} pings={}",
                    deviceId, platform, session.getSessionCount());
            DeviceSession saved = repository.save(session);
            notifyDashboards(saved);
            return saved;
        }

        DeviceSession newSession = DeviceSession.builder()
                .deviceId(deviceId)
                .platform(platform)
                .userAgent(userAgent)
                .ipAddress(ip)
                .language(lang)
                .deviceModel(     body != null ? body.deviceModel()      : null)
                .osVersion(       body != null ? body.osVersion()        : null)
                .appVersion(      body != null ? body.appVersion()       : null)
                .timezone(        body != null ? body.timezone()         : null)
                .screenResolution(body != null ? body.screenResolution() : null)
                .networkType(     body != null ? body.networkType()      : null)
                .firstSeen(Instant.now())
                .lastSeen(Instant.now())
                .sessionCount(1)
                .visitCount(visit.created() ? 1 : 0)
                .totalActiveMs(visit.activeDeltaMs())
                .permissions(permissions)
                .build();

        log.info("Nuevo dispositivo — deviceId={} platform={} ip={}", deviceId, platform, ip);
        DeviceSession saved = repository.save(newSession);
        notifyDashboards(saved);
        return saved;
    }

    // ── Visitas (app_sessions) ────────────────────────────────────────────

    record VisitUpdate(boolean created, long activeDeltaMs) {
        static final VisitUpdate NONE = new VisitUpdate(false, 0);
    }

    private VisitUpdate trackVisit(String deviceId, PingRequestDTO body, String platform, String userAgent,
                                   String lang, PermissionSnapshot permissions) {
        if (body == null || body.sessionId() == null || !SESSION_ID.matcher(body.sessionId()).matches()) {
            return VisitUpdate.NONE;
        }
        // Dos pings simultáneos de una visita nueva: el segundo choca con el índice único y se reintenta como actualización
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return updateVisit(deviceId, body, platform, userAgent, lang, permissions);
            } catch (DuplicateKeyException e) {
                log.debug("Visita {} creada en paralelo, reintentando", body.sessionId());
            }
        }
        return VisitUpdate.NONE;
    }

    private VisitUpdate updateVisit(String deviceId, PingRequestDTO body, String platform, String userAgent,
                                    String lang, PermissionSnapshot permissions) {
        Instant now = Instant.now();
        Optional<AppSession> found = appSessionRepository.findBySessionId(body.sessionId());
        AppSession visit;
        boolean created = found.isEmpty();
        if (found.isPresent()) {
            visit = found.get();
            if (!deviceId.equals(visit.getDeviceId())) {
                log.warn("Visita {} reportada por otro dispositivo; se ignora", body.sessionId());
                return VisitUpdate.NONE;
            }
        } else {
            long reported = body.activeMs() == null ? 0 : Math.max(0, Math.min(body.activeMs(), MAX_ACTIVE_MS));
            visit = AppSession.builder()
                    .sessionId(body.sessionId())
                    .deviceId(deviceId)
                    .platform(platform)
                    .userAgent(truncate(userAgent, 512))
                    // El primer aviso puede llegar tarde (sin conexión): la visita empezó antes
                    .startedAt(now.minusMillis(reported))
                    .build();
        }
        long before = visit.getActiveMs();
        visit.setActiveMs(acceptedActiveMs(before, body.activeMs(), visit.getStartedAt(), now));
        visit.setLastActiveAt(now);
        visit.setPings(visit.getPings() + 1);
        if (body.appVersion() != null)       visit.setAppVersion(truncate(body.appVersion(), 32));
        if (lang != null)                    visit.setLanguage(truncate(lang, 16));
        if (body.screenResolution() != null) visit.setScreenResolution(truncate(body.screenResolution(), 16));
        if (body.networkType() != null)      visit.setNetworkType(truncate(body.networkType(), 16));
        if (permissions != null)             visit.setPermissions(permissions);
        mergeFeatures(visit.getFeatures(), body.features());
        appSessionRepository.save(visit);
        return new VisitUpdate(created, visit.getActiveMs() - before);
    }

    /**
     * El tiempo de uso lo mide la app (solo cuenta con la pantalla visible y en uso) y llega acumulado.
     * Nunca baja (un aviso atrasado no resta) y nunca supera el tiempo real transcurrido.
     */
    static long acceptedActiveMs(long previous, Long reported, Instant startedAt, Instant now) {
        if (reported == null || reported < 0) return previous;
        long elapsed = Math.max(0, Duration.between(startedAt, now).toMillis()) + CLOCK_SLACK_MS;
        long accepted = Math.min(Math.min(reported, elapsed), MAX_ACTIVE_MS);
        return Math.max(previous, accepted);
    }

    /** Los contadores llegan acumulados: se guarda el mayor. Claves y valores fuera de rango se descartan. */
    static void mergeFeatures(Map<String, Integer> target, Map<String, Integer> reported) {
        if (reported == null) return;
        for (Map.Entry<String, Integer> e : reported.entrySet()) {
            String key = e.getKey();
            Integer value = e.getValue();
            if (key == null || value == null || value <= 0 || !FEATURE_KEY.matcher(key).matches()) continue;
            if (!target.containsKey(key) && target.size() >= MAX_FEATURES) continue;
            target.merge(key, Math.min(value, MAX_FEATURE_COUNT), Math::max);
        }
    }

    static PermissionSnapshot sanitizePermissions(PingRequestDTO body) {
        if (body == null || body.permissions() == null) return null;
        PingRequestDTO.Permissions p = body.permissions();
        // Set.of(...).contains(null) lanza NullPointerException
        String camera   = p.camera() != null && ACCESS_STATES.contains(p.camera()) ? p.camera() : null;
        String location = p.location() != null && ACCESS_STATES.contains(p.location()) ? p.location() : null;
        String motion   = p.motion() != null && MOTION_STATES.contains(p.motion()) ? p.motion() : null;
        if (camera == null && location == null && motion == null) return null;
        return new PermissionSnapshot(camera, location, motion);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    // Las estadísticas solo se calculan si hay un panel escuchando por SSE
    private void notifyDashboards(DeviceSession saved) {
        if (eventPublisher.connectedClients() == 0) return;
        eventPublisher.publishPing(saved, getStats());
    }


    /** Estadisticas globales, calculadas en MongoDB (sin cargar todos los dispositivos en memoria). */
    public StatsDTO getStats() {
        long totalDevices  = 0;
        long totalSessions = 0;
        long totalVisits   = 0;
        long totalActiveMs = 0;
        Map<String, Long> byPlatform = new HashMap<>();
        for (DeviceSessionRepository.PlatformStats p : repository.aggregateByPlatform()) {
            totalDevices  += p.devices();
            totalSessions += p.sessions();
            totalVisits   += p.visits();
            totalActiveMs += p.activeMs();
            byPlatform.merge(p.platform() != null ? p.platform() : "Unknown", p.devices(), Long::sum);
        }
        Instant now = Instant.now();
        return new StatsDTO(totalDevices, totalSessions, byPlatform,
                repository.countByLastSeenGreaterThanEqual(now.minus(ACTIVE_WINDOW)),
                repository.countByLastSeenGreaterThanEqual(now.minus(DAY_WINDOW)),
                totalVisits, totalActiveMs);
    }

    /**
     * Dispositivos más recientes primero.
     * status: all | active (≤10 min) | today (10 min–24 h) | inactive (más de 24 h). q busca en id, plataforma, IP e idioma.
     */
    public PageResponse<DeviceSession> findPage(String q, String status, int page, int size) {
        int p = PageResponse.safePage(page);
        int s = PageResponse.safeSize(size);
        Criteria criteria = statusCriteria(status, Instant.now());
        if (q != null && !q.isBlank()) {
            String text = q.strip();
            if (text.length() > MAX_QUERY_LENGTH) text = text.substring(0, MAX_QUERY_LENGTH);
            Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("deviceId").regex(pattern),
                    Criteria.where("platform").regex(pattern),
                    Criteria.where("ipAddress").regex(pattern),
                    Criteria.where("language").regex(pattern)));
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "lastSeen"))
                .skip((long) p * s)
                .limit(s);
        List<DeviceSession> content = mongoTemplate.find(query, DeviceSession.class);
        long total = mongoTemplate.count(new Query(criteria), DeviceSession.class);
        return PageResponse.of(content, p, s, total);
    }

    private static Criteria statusCriteria(String status, Instant now) {
        Instant active = now.minus(ACTIVE_WINDOW);
        Instant day = now.minus(DAY_WINDOW);
        return switch (status == null ? "all" : status) {
            case "active" -> Criteria.where("lastSeen").gte(active);
            case "today" -> Criteria.where("lastSeen").gte(day).lt(active);
            case "inactive" -> Criteria.where("lastSeen").lt(day);
            default -> new Criteria();
        };
    }

    // ── Deteccion de plataforma ────────────────────────────────

    static String detectPlatform(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("android"))                            return "Android";
        if (ua.contains("iphone") || ua.contains("ipad"))     return "iOS";
        if (ua.contains("mobile"))                             return "Mobile-Other";
        if (ua.contains("windows"))                            return "Desktop-Windows";
        if (ua.contains("macintosh") || ua.contains("mac os")) return "Desktop-Mac";
        if (ua.contains("linux"))                              return "Desktop-Linux";
        if (ua.contains("postmanruntime"))                     return "Postman";
        if (ua.contains("java") || ua.contains("okhttp"))     return "App-Native";
        return "Web";
    }
}
