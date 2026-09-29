package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.PingRequestDTO;
import co.edu.uco.ucomap.dto.StatsDTO;
import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final DeviceSessionRepository repository;
    private final SessionEventPublisher    eventPublisher;

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
            eventPublisher.publishPing(saved, getStats());
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
        eventPublisher.publishPing(saved, getStats());
        return saved;
    }


    /** Estadisticas globales. */
    public StatsDTO getStats() {
        List<DeviceSession> all = repository.findAll();
        long totalDevices  = all.size();
        long totalSessions = all.stream().mapToLong(DeviceSession::getSessionCount).sum();
        Map<String, Long> byPlatform = all.stream()
                .collect(Collectors.groupingBy(DeviceSession::getPlatform, Collectors.counting()));
        return new StatsDTO(totalDevices, totalSessions, byPlatform);
    }

    /** Lista todos los dispositivos mas recientes primero. */
    public List<DeviceSession> getAllSessions() {
        return repository.findAllByOrderByLastSeenDesc();
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
