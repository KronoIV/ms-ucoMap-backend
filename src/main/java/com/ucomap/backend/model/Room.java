package com.ucomap.backend.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Salón o espacio dentro del campus UCO.
 * Colección MongoDB: rooms
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    /** Identificador corto: "205", "EDC", "L2", etc. */
    @NotBlank
    @Indexed(unique = true)
    private String roomId;

    /** Nombre completo del salón/espacio. */
    @NotBlank
    private String name;

    /**
     * Categoría del salón: "CO" (Colegio), "EDC", "Otros".
     * Determina a qué bloque pertenece para la navegación outdoor.
     */
    @Indexed
    private String category;

    /**
     * ID del clip/state de animación en el modelo AR (Zappar).
     * Corresponde a la clave en POI_CLIP_NAMES del frontend.
     */
    @NotBlank
    private String stateId;

    /** URL del modelo 3D (para uso futuro). */
    private String modelUrl;

    @Builder.Default
    private boolean active = true;
}

