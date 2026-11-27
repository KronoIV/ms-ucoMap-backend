package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.UcoVpsDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Emite los tokens del VPS propio (vps-server, DESIGN.md §10): HS256 con {@code kid}, emisor "ucomap-backend",
 * audiencia "ucomap-vps", 10 min para la app (alcance localize) y 30 min para el admin.
 * El secreto se comparte con el VPS (VPS_TOKEN_SECRETS=kid:secreto); el backend nunca llama al VPS.
 */
@Slf4j
@Service
public class UcoVpsService {

    public static final String ISSUER = "ucomap-backend";
    public static final String AUDIENCE = "ucomap-vps";
    private static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final SecretKey key;
    private final String kid;
    private final String baseUrl;
    private final String mapSetId;
    private final long tokenTtlSeconds;
    private final long adminTokenTtlSeconds;

    public UcoVpsService(@Value("${app.vps.token-secret:}") String secret,
                         @Value("${app.vps.token-kid:k1}") String kid,
                         @Value("${app.vps.base-url:}") String baseUrl,
                         @Value("${app.vps.map-set-id:uco}") String mapSetId,
                         @Value("${app.vps.token-ttl-seconds:600}") long tokenTtlSeconds,
                         @Value("${app.vps.admin-token-ttl-seconds:1800}") long adminTokenTtlSeconds) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0 && bytes.length < 32) {
            throw new IllegalStateException("VPS_TOKEN_SECRET must be at least 32 characters");
        }
        this.key = bytes.length == 0 ? null : Keys.hmacShaKeyFor(bytes);
        this.kid = kid;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.mapSetId = mapSetId;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.adminTokenTtlSeconds = adminTokenTtlSeconds;
        if (!isConfigured()) {
            log.info("VPS propio sin configurar (VPS_BASE_URL / VPS_TOKEN_SECRET): /api/vps/* responde 503");
        }
    }

    public boolean isConfigured() {
        return key != null && !baseUrl.isBlank();
    }

    /** Token de la app: solo localiza, y solo en el map set del campus. */
    public UcoVpsDTO.TokenResponse appToken(String sessionId) {
        String sid = sessionId != null && SESSION_ID.matcher(sessionId).matches()
                ? sessionId : UUID.randomUUID().toString();
        return issue("localize", "app", sid, tokenTtlSeconds);
    }

    /** Token del panel: administra el map set del campus (subir escaneos, alinear, construir, publicar). */
    public UcoVpsDTO.TokenResponse adminToken(String adminEmail) {
        return issue("admin", adminEmail, "admin-" + UUID.randomUUID(), adminTokenTtlSeconds);
    }

    private UcoVpsDTO.TokenResponse issue(String scope, String subject, String sid, long ttlSeconds) {
        if (!isConfigured()) {
            throw new IllegalStateException("VPS no configurado");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String token = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(ISSUER)
                .audience().single(AUDIENCE)
                .subject(subject)
                .claim("sid", sid)
                .claim("scope", scope)
                .claim("mapSetId", mapSetId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                // HS256 explícito: con claves largas jjwt elegiría HS512 y el VPS solo acepta HS256
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new UcoVpsDTO.TokenResponse(token, expiresAt, baseUrl, mapSetId, scope);
    }
}
