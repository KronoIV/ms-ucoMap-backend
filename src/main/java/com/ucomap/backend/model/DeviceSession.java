package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Registro de conexion de un dispositivo unico al sistema.
 * Coleccion MongoDB: device_sessions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "device_sessions")
public class DeviceSession {

    @Id
    private String id;

    /** UUID generado por el cliente y persistido en localStorage/AsyncStorage. */
    @Indexed(unique = true)
    private String deviceId;

    // ── Detectado automaticamente ────────────────────────────

    /** Plataforma detectada del User-Agent: "iOS", "Android", "Desktop-Windows", etc. */
    private String platform;

    /** Valor raw del header User-Agent */
    private String userAgent;

    /** Direccion IP del cliente (considera X-Forwarded-For para proxies) */
    private String ipAddress;

    // ── Enviado por el cliente ────────────────────────────────

    /** Modelo del dispositivo: "iPhone 14", "Samsung Galaxy S23" */
    private String deviceModel;

    /** Version del sistema operativo: "iOS 17.4", "Android 14" */
    private String osVersion;

    /** Version de la aplicacion instalada: "1.0.3" */
    private String appVersion;

    /** Idioma del dispositivo: "es-CO", "en-US" */
    private String language;

    /** Zona horaria: "America/Bogota" */
    private String timezone;

    /** Resolucion de pantalla: "390x844" */
    private String screenResolution;

    /** Tipo de red: "wifi", "cellular", "ethernet" */
    private String networkType;

    // ── Contadores y fechas ───────────────────────────────────

    /** Fecha y hora de la primera conexion */
    private Instant firstSeen;

    /** Fecha y hora de la ultima conexion */
    private Instant lastSeen;

    /** Cantidad de veces que este dispositivo ha hecho ping */
    @Builder.Default
    private int sessionCount = 1;
}
