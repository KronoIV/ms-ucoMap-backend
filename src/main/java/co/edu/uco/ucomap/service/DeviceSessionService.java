package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.dto.PingRequestDTO;
import co.edu.uco.ucomap.dto.StatsDTO;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(10);
    static final Duration DAY_WINDOW    = Duration.ofHours(24);
    private static final int MAX_QUERY_LENGTH = 100;

    private final DeviceSessionRepository repository;
    private final SessionEventPublisher    eventPublisher;
    private final MongoTemplate            mongoTemplate;

    /**
     * Registra o actualiza la sesion de un dispositivo.
     * Los campos del dispositivo vienen del body; IP y User-Agent de los headers HTTP.
     * Language: se prefiere el valor del body; si no viene, se usa el header Accept-Language.
     */
    public DeviceSession registerPing(String deviceId, PingRequestDTO body,
                                      String userAgent, String ip, String headerLang) {

        String lang     = (body != null && body.language() != null && !body.language().isBlank())
                          ? body.language() : headerLang;
        String platform = detectPlatform(userAgent);

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
            log.info("Ping actualizado — deviceId={} platform={} sesiones={}",
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
                .build();

        log.info("Nuevo dispositivo — deviceId={} platform={} ip={}", deviceId, platform, ip);
        DeviceSession saved = repository.save(newSession);
        notifyDashboards(saved);
        return saved;
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
        Map<String, Long> byPlatform = new HashMap<>();
        for (DeviceSessionRepository.PlatformStats p : repository.aggregateByPlatform()) {
            totalDevices  += p.devices();
            totalSessions += p.sessions();
            byPlatform.merge(p.platform() != null ? p.platform() : "Unknown", p.devices(), Long::sum);
        }
        Instant now = Instant.now();
        return new StatsDTO(totalDevices, totalSessions, byPlatform,
                repository.countByLastSeenGreaterThanEqual(now.minus(ACTIVE_WINDOW)),
                repository.countByLastSeenGreaterThanEqual(now.minus(DAY_WINDOW)));
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
