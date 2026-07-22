package com.ucomap.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Parámetro de configuración de la aplicación móvil UCO Map.
 * Colección MongoDB: app_settings
 *
 * Permite controlar el comportamiento de la app desde el backend
 * sin necesidad de publicar una nueva versión.
 *
 * Para agregar un nuevo parámetro:
 *   1. Insertar un documento en esta colección (o vía AppSettingInitializer).
 *   2. Agregar la key a AppSettings en campus-api.ts con su default.
 *   3. Consumir en index.ts donde sea necesario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "app_settings")
public class AppSetting {

    /**
     * Clave única del parámetro (ej. "enableIndoorARNavigation").
     * Sirve como _id del documento MongoDB — garantiza unicidad sin índice adicional.
     */
    @Id
    @NotBlank
    @Size(max = 100)
    private String key;

    /**
     * Valor serializado como String.
     * Se parsea en el cliente según {@code type}:
     *   BOOLEAN → "true" / "false"
     *   NUMBER  → "42" / "3.14"
     *   JSON    → objeto JSON stringificado
     *   STRING  → valor literal
     */
    @NotBlank
    @Size(max = 500)
    private String value;

    /** Tipo de dato para que el cliente parsee correctamente el valor. */
    @Builder.Default
    private SettingType type = SettingType.STRING;

    /** Descripción legible para la UI del panel de administración. */
    @Size(max = 500)
    private String description;

    /**
     * Categoría para agrupar parámetros en la UI admin.
     * Ejemplos: "navigation", "ui", "features", "timing".
     */
    @Size(max = 100)
    @Indexed
    private String category;

    /** Indica si el parámetro está activo y debe ser retornado a los clientes. */
    @Builder.Default
    private boolean active = true;

    /** Fecha y hora de la última modificación (UTC). */
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
