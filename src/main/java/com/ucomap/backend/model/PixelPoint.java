package com.ucomap.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Coordenadas en píxeles sobre el render isométrico 500×800 del campus. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixelPoint {
    private int x;
    private int y;
}

