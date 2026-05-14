package com.ucomap.backend.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Clip/estado de animación AR de un POI (Point of Interest).
 * Mapea el stateId del frontend al nombre visible.
 * Colección MongoDB: poi_clips
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "poi_clips")
public class PoiClip {

    /** Clave del clip en la animación Zappar (ej. "co205", "EDC", "Laboratorio_Fisica"). */
    @Id
    private String clipId;

    /** Nombre visible en la UI (ej. "CO 205", "Laboratorio de Física"). */
    @NotBlank
    private String displayName;

    @Builder.Default
    private boolean active = true;
}

