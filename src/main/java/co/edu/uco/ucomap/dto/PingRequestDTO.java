package co.edu.uco.ucomap.dto;

import java.util.Map;

/**
 * Payload que el cliente envia al hacer ping al iniciar la app.
 * Los campos obligatorios son solo deviceId.
 * El resto son opcionales — el cliente envia lo que pueda obtener.
 */
public record PingRequestDTO(

        // ── Obligatorio ──────────────────────────────────────
        /** UUID generado en el cliente, persistido en localStorage/AsyncStorage */
        String deviceId,

        // ── Info del dispositivo (cliente la envia) ───────────
        /** Modelo del dispositivo: "iPhone 14", "Samsung Galaxy S23", etc. */
        String deviceModel,

        /** Version del sistema operativo: "iOS 17.4", "Android 14", etc. */
        String osVersion,

        /** Version de la aplicacion: "1.0.3" */
        String appVersion,

        /** Idioma configurado: "es-CO", "en-US" */
        String language,

        /** Zona horaria: "America/Bogota" */
        String timezone,

        /** Resolucion de pantalla: "390x844" */
        String screenResolution,

        /** Tipo de conexion de red: "wifi", "cellular", "ethernet" */
        String networkType,

        // ── Visita (app ≥ 2.1; las versiones anteriores no lo envían) ──
        /** UUID de la visita: la app crea uno nuevo al abrirse o tras 30 min sin uso. */
        String sessionId,

        /** Tiempo de uso acumulado en la visita (ms), medido en el teléfono. */
        Long activeMs,

        /** Usos acumulados de cada función en la visita. Se depuran en el servicio. */
        Map<String, Integer> features,

        /** Estado actual de los permisos. */
        Permissions permissions
) {
    public record Permissions(String camera, String location, String motion) {}

    /** Ping de versiones anteriores de la app (solo datos del dispositivo). */
    public PingRequestDTO(String deviceId, String deviceModel, String osVersion, String appVersion,
                          String language, String timezone, String screenResolution, String networkType) {
        this(deviceId, deviceModel, osVersion, appVersion, language, timezone, screenResolution, networkType,
                null, null, null, null);
    }
}

