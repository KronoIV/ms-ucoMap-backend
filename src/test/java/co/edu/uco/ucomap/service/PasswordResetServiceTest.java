package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.PasswordResetToken;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.PasswordResetTokenRepository;
import co.edu.uco.ucomap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    private PasswordResetService service;
    private final User user = User.builder().id("u1").email("admin@uco.edu.co").build();

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, emailService);
        ReflectionTestUtils.setField(service, "tokenExpiryMinutes", 60L);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://admin.test");
        ReflectionTestUtils.setField(service, "resetPath", "/reset-password");
        ReflectionTestUtils.setField(service, "cooldownMinutes", 1L);
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static PasswordResetToken token(Instant createdAt, Instant expiresAt) {
        return PasswordResetToken.builder().id("t1").userId("u1").tokenHash("hash")
                .createdAt(createdAt).expiresAt(expiresAt).build();
    }

    // ── requestReset ──────────────────────────────────────────

    @Test
    void unknownEmailIsIgnoredSilently() {
        when(userRepository.findByEmail("nadie@uco.edu.co")).thenReturn(Optional.empty());

        service.requestReset("nadie@uco.edu.co");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyLong());
    }

    @Test
    void issuesHashedTokenAndEmailsTheRawOne() throws Exception {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserIdOrderByCreatedAtDesc("u1")).thenReturn(Optional.empty());
        when(tokenRepository.findByUserIdAndUsedFalseAndRevokedFalse("u1")).thenReturn(List.of());

        service.requestReset(user.getEmail());

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), link.capture(), eq(60L));

        assertThat(link.getValue()).startsWith("https://admin.test/reset-password?token=");
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(rawToken).hasSize(64);

        PasswordResetToken stored = saved.getValue();
        // En la base de datos solo queda el hash, nunca el token que viaja por correo
        assertThat(stored.getTokenHash()).isEqualTo(sha256(rawToken)).isNotEqualTo(rawToken);
        assertThat(stored.getUserId()).isEqualTo("u1");
        assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void newRequestRevokesPreviousActiveTokens() {
        PasswordResetToken old = token(Instant.now().minus(Duration.ofMinutes(10)), Instant.now().plusSeconds(600));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserIdOrderByCreatedAtDesc("u1")).thenReturn(Optional.of(old));
        when(tokenRepository.findByUserIdAndUsedFalseAndRevokedFalse("u1")).thenReturn(List.of(old));

        service.requestReset(user.getEmail());

        assertThat(old.isRevoked()).isTrue();
        verify(tokenRepository).saveAll(List.of(old));
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), anyString(), eq(60L));
    }

    @Test
    void cooldownPreventsEmailSpam() {
        PasswordResetToken recent = token(Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserIdOrderByCreatedAtDesc("u1")).thenReturn(Optional.of(recent));

        service.requestReset(user.getEmail());

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyLong());
    }

    // ── resetPassword ─────────────────────────────────────────

    @Test
    void validTokenChangesPasswordAndIsConsumed() throws Exception {
        PasswordResetToken valid = token(Instant.now(), Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(sha256("raw-token"))).thenReturn(Optional.of(valid));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaClave123")).thenReturn("bcrypt-hash");

        service.resetPassword("raw-token", "NuevaClave123");

        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        // Invalida los JWT emitidos antes del cambio (ver JwtAuthFilter)
        assertThat(user.getPasswordChangedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
        assertThat(valid.isUsed()).isTrue();
        assertThat(valid.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(valid);
    }

    @Test
    void unknownTokenIsRejected() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertRejected(() -> service.resetPassword("raw", "NuevaClave123"),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    @Test
    void usedTokenCannotBeReused() {
        PasswordResetToken used = token(Instant.now(), Instant.now().plusSeconds(600));
        used.setUsed(true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertRejected(() -> service.resetPassword("raw", "NuevaClave123"),
                ErrorCode.PASSWORD_RESET_TOKEN_USED);
    }

    @Test
    void revokedTokenIsRejected() {
        PasswordResetToken revoked = token(Instant.now(), Instant.now().plusSeconds(600));
        revoked.setRevoked(true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertRejected(() -> service.resetPassword("raw", "NuevaClave123"),
                ErrorCode.PASSWORD_RESET_TOKEN_USED);
    }

    @Test
    void expiredTokenIsRejected() {
        PasswordResetToken expired = token(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertRejected(() -> service.resetPassword("raw", "NuevaClave123"),
                ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
    }

    private void assertRejected(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getReason()).isEqualTo(expected.getMessage());
                });
        verify(userRepository, never()).save(any());
    }
}
