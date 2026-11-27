package co.edu.uco.ucomap.dto;

import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Tokens cortos para hablar directo con el VPS propio (vps-server). */
public final class UcoVpsDTO {

    private UcoVpsDTO() {
    }

    /**
     * Pedido de la app. {@code sessionId} (opcional) es el id de la sesión del dispositivo: el VPS lo usa para
     * limitar peticiones por sesión y para relacionar sus registros con los recorridos.
     */
    public record TokenRequest(
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$")
            String sessionId
    ) {
    }

    /** {@code baseUrl} es la raíz pública del VPS (la app y el admin le hablan directo). */
    public record TokenResponse(
            String token,
            Instant expiresAt,
            String baseUrl,
            String mapSetId,
            String scope
    ) {
    }
}
