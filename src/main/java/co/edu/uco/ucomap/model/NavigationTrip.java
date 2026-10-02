package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Un recorrido de navegación: desde que el usuario confirma un destino hasta que llega o abandona.
 * Colección MongoDB: navigation_trips
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "navigation_trips")
public class NavigationTrip {

    @Id
    private String id;

    /** UUID generado por la app al iniciar el recorrido. */
    @Indexed(unique = true)
    private String tripId;

    @Indexed
    private String deviceId;

    private String platform;

    // ── Destino ──────────────────────────────────────────────
    private String roomId;
    private String roomName;
    private String building;

    // ── Inicio ───────────────────────────────────────────────
    /** Modo con el que arrancó: indoor, outdoor o ask. */
    private String startMode;
    /** Distancia en línea recta al edificio al iniciar (m), si había GPS. */
    private Double startDistanceM;
    private Double startAccuracyM;
    /** Punto de partida (4 decimales, ~11 m), si la app tenía permiso de ubicación. */
    private Double startLat;
    private Double startLng;

    // ── Resultado ────────────────────────────────────────────
    private TripStatus status;
    /** ar-arrival, building-arrival, closed, destination-changed, page-closed, timeout (salió sin cerrar). */
    private String endReason;

    private Instant startedAt;
    private Instant endedAt;
    /** Último aviso de la app mientras el recorrido estaba en curso. */
    private Instant lastSeenAt;
    /** Duración medida en el dispositivo (ms). */
    private Long durationMs;

    /** Tiempo hasta entrar en modo interior (ms desde el inicio). */
    private Long buildingReachedMs;
    /** Tiempo hasta la primera localización VPS (ms desde el inicio). */
    private Long localizedMs;

    /** Longitud de la ruta exterior calculada (m). */
    private Double outdoorRouteM;
    /** Longitud de la ruta interior AR calculada (m). */
    private Double indoorRouteM;

    private Integer modeSwitches;
    private Integer vpsFailures;
    private Boolean usedAR;
}
