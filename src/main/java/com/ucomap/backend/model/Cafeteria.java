package com.ucomap.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cafetería o punto de alimentación en el campus UCO.
 * Colección MongoDB: cafeterias
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cafeterias")
public class Cafeteria {

    @Id
    private String id;

    /** Identificador único legible: "caf_principal", "caf_norte", etc. */
    @NotBlank
    @Indexed(unique = true)
    private String cafeteriaId;

    /** Nombre completo de la cafetería. */
    @NotBlank
    private String name;

    /** Descripción o información adicional (opcional). */
    private String description;

    /** Horario de atención (opcional). */
    private String schedule;

    /** Latitud GPS de la cafetería. */
    @NotNull
    private Double lat;

    /** Longitud GPS de la cafetería. */
    @NotNull
    private Double lng;

    @Builder.Default
    private boolean active = true;
}

