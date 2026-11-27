package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiError;
import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.UcoVpsDTO;
import co.edu.uco.ucomap.service.UcoVpsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Tokens del VPS propio. La app y el panel hablan directo con el VPS (imágenes y archivos grandes no pasan por
 * este backend); aquí solo se decide quién puede hacerlo y por cuánto tiempo.
 */
@RestController
@RequestMapping("/api/vps")
@RequiredArgsConstructor
public class UcoVpsController {

    static final String NOT_CONFIGURED = "El VPS propio no está configurado en el servidor.";

    private final UcoVpsService vpsService;

    /** Público (la app no tiene sesión). Alcance localize, 10 min. */
    @PostMapping("/token")
    public ResponseEntity<?> appToken(@Valid @RequestBody(required = false) UcoVpsDTO.TokenRequest body) {
        if (!vpsService.isConfigured()) return notConfigured();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiSuccess.of(vpsService.appToken(body == null ? null : body.sessionId())));
    }

    /** Solo ADMIN (SecurityConfig). Alcance admin del map set del campus, 30 min. */
    @PostMapping("/admin-token")
    public ResponseEntity<?> adminToken(Principal principal) {
        if (!vpsService.isConfigured()) return notConfigured();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiSuccess.of(vpsService.adminToken(principal.getName())));
    }

    private static ResponseEntity<ApiError> notConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(ApiError.of(NOT_CONFIGURED));
    }
}
