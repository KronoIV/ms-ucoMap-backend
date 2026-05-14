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
 * Edificio/bloque destino del campus (EDC, COLEGIO).
 * Colección MongoDB: buildings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "buildings")
public class Building {

    /**
     * ID del edificio: "EDC", "COLEGIO".
     * Coincide con el nodeId del GraphNode tipo BUILDING.
     */
    @Id
    private String buildingId;

    /** Nombre visible en la UI. */
    @NotBlank
    private String label;

    /** Color hex para el mapa outdoor (ej. "#D84315"). */
    private String color;

    /**
     * Categoría de los salones que pertenecen a este edificio.
     * Debe coincidir con Room.category ("CO", "EDC", "Otros").
     */
    @Indexed
    private String category;

    /** Coordenadas GPS del edificio (centro aproximado). */
    private GpsPoint gps;

    @Builder.Default
    private boolean active = true;
}

