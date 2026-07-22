package com.ucomap.backend.model;

/** Tipo de dato de un parámetro de configuración de la aplicación. */
public enum SettingType {
    /** Valor lógico: "true" o "false". */
    BOOLEAN,
    /** Texto libre. */
    STRING,
    /** Número entero o decimal. */
    NUMBER,
    /** Objeto JSON serializado como string. */
    JSON
}
