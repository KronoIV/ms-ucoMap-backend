package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Una visita a la app: desde que se abre hasta 30 min sin uso (lo decide la app y envía un sessionId nuevo).
 * Colección MongoDB: app_sessions
 *
 * El tiempo de uso es activeMs: tiempo con la app en pantalla y en uso, medido en el teléfono.
 * No se usa lastSeen − firstSeen porque incluye el tiempo en segundo plano o con la pestaña olvidada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "app_sessions")
public class AppSession {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sessionId;

    @Indexed
    private String deviceId;

    private String platform;
    private String userAgent;
    private String appVersion;
    private String language;
    private String screenResolution;
    private String networkType;

    @Indexed
    private Instant startedAt;
    /** Último aviso recibido con la app en uso. */
    private Instant lastActiveAt;

    /** Tiempo de uso real (ms): nunca baja y nunca supera el tiempo transcurrido desde el inicio. */
    @Builder.Default
    private long activeMs = 0;

    @Builder.Default
    private int pings = 0;

    /** Veces que se usó cada función en la visita (p. ej. "mode:outdoor" → 2). */
    @Builder.Default
    private Map<String, Integer> features = new HashMap<>();

    /** Último estado de permisos reportado en la visita. */
    private PermissionSnapshot permissions;
}
