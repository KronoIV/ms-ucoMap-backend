package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Veredicto de la app sobre la guía con cámara (AR) en el teléfono y los datos con que lo decidió
 * (reglas en uco-map-app/src/services/ar-device-rules.ts). Sirve para ver qué equipos tienen AR y cuáles fallan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArCapabilitySnapshot {
    /** compatible | limited | unsupported | disabled (apagada en el panel). */
    private String verdict;
    /** webxr | webcam; null sin AR. */
    private String tracker;
    /** full | lite. */
    private String profile;
    /** Versión de los criterios con que se evaluó. */
    private String criteria;
    /** evaluated | cached | forced | runtime (rebajado tras una sesión AR que no rindió). */
    private String source;
    /** Por qué, en códigos cortos (p. ej. "ram<4", "gpu:D:mali-g57-mc2", "bench:slow:180/12"). */
    private List<String> reasons;
    /** Renderer de WebGL tal como lo informa el navegador. */
    private String gpu;
    /** A (gama alta) … E (entrada), apple o unknown. */
    private String gpuClass;
    private Double memoryGb;
    private Integer cores;
    /** Medición en el teléfono: captura de un frame para el VPS y capa dibujada (ms). */
    private Integer benchFrameMs;
    private Double benchGpuMs;
}
