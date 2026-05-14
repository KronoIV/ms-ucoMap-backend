package com.ucomap.backend.dto;

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
        String networkType
) {}

