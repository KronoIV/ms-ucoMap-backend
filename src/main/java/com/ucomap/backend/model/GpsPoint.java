package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Coordenadas GPS (se almacena como objeto embebido en MongoDB). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPoint {
    private double lat;
    private double lng;
}

