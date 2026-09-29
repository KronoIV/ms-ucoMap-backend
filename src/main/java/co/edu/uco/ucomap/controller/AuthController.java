package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.AuthDTO;
import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.security.jwt.JwtUtil;
import co.edu.uco.ucomap.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<ApiSuccess<AuthDTO.Response>> login(@Valid @RequestBody AuthDTO.LoginRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            log.warn("Login fallido — email={} motivo={}", request.getEmail(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_CREDENTIAL_INVALID.getMessage());
        }

        String email = auth.getName();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(a -> a.replace("ROLE_", ""))
                .orElse("ADMIN");

        String token = jwtUtil.generateToken(email, role);
        log.info("Login exitoso — email={} role={}", email, role);
        return ResponseEntity.ok(ApiSuccess.of(new AuthDTO.Response(token, email, role)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiSuccess<Void>> forgotPassword(
            @Valid @RequestBody AuthDTO.ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        // Always return the same message — never reveal whether the email is registered
        return ResponseEntity.ok(ApiSuccess.ofMessage(
                "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiSuccess<Void>> resetPassword(
            @Valid @RequestBody AuthDTO.ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
