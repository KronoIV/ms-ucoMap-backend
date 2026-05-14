package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Configuración global del mapa del campus.
 * Solo existe UN documento en esta colección (id fijo = "campus_config").
 * Colección MongoDB: map_config
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "map_config")
public class MapConfig {

    /** ID fijo para el singleton de configuración. */
    @Id
    private String id;

    /** Esquina noroeste del bounding box del campus. */
    private GpsPoint northWest;

    /** Esquina sureste del bounding box del campus. */
    private GpsPoint southEast;

    /** Ancho del render isométrico de referencia (píxeles). */
    private int renderWidth;

    /** Alto del render isométrico de referencia (píxeles). */
    private int renderHeight;

    /**
     * Radio (metros) para considerar que el usuario llegó al edificio.
     * Valor recomendado: 20 m.
     */
    @Builder.Default
    private double arrivalThresholdMeters = 20.0;

    /**
     * Radio (metros) desde el cual se muestra el mapa outdoor.
     * Si el usuario está más cerca, va directo a AR.
     */
    @Builder.Default
    private double outdoorThresholdMeters = 50.0;
}

