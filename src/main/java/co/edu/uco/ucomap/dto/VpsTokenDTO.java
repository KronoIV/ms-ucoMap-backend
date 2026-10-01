package co.edu.uco.ucomap.dto;

import java.time.Instant;

/**
 * Token de consulta VPS para la app AR. Mismo formato que {@code POST m2m/token} de MultiSet,
 * para que el SDK web lo consuma directamente vía {@code endpoints.authUrl}.
 */
public record VpsTokenDTO(String token, Instant expiresAt, String mapSetCode) {}
