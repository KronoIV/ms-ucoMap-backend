package com.ucomap.backend.service;

import com.ucomap.backend.dto.StatsDTO;
import com.ucomap.backend.model.DeviceSession;
import com.ucomap.backend.repository.DeviceSessionRepository;
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

    /**
     * Registra o actualiza la sesion de un dispositivo.
     * Toda la informacion viene de los headers HTTP — el cliente no envia nada.
     */
    public DeviceSession registerPing(String deviceId, String userAgent, String ip, String language) {
        Optional<DeviceSession> existing = repository.findByDeviceId(deviceId);

        if (existing.isPresent()) {
            DeviceSession session = existing.get();
            session.setLastSeen(Instant.now());
            session.setSessionCount(session.getSessionCount() + 1);
            session.setUserAgent(userAgent);
            session.setIpAddress(ip);
            if (language != null) session.setLanguage(language);
            log.info("Ping actualizado — deviceId={} platform={} sesiones={}",
                    deviceId, session.getPlatform(), session.getSessionCount());
            return repository.save(session);
        }

        String platform = detectPlatform(userAgent);
        DeviceSession newSession = DeviceSession.builder()
                .deviceId(deviceId)
                .platform(platform)
                .userAgent(userAgent)
                .ipAddress(ip)
                .language(language)
                .firstSeen(Instant.now())
                .lastSeen(Instant.now())
                .sessionCount(1)
                .build();

        log.info("Nuevo dispositivo — deviceId={} platform={} ip={}", deviceId, platform, ip);
        return repository.save(newSession);
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

    private String detectPlatform(String userAgent) {
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
