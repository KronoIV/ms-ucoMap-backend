package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.PasswordResetToken;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.PasswordResetTokenRepository;
import co.edu.uco.ucomap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.password-reset.token-expiry-minutes}")
    private long tokenExpiryMinutes;

    @Value("${app.password-reset.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-reset.reset-path}")
    private String resetPath;

    @Value("${app.password-reset.cooldown-minutes}")
    private long cooldownMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Initiates a password reset for the given email.
     * Always returns silently — never reveals whether the email is registered.
     */
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unregistered email; ignoring silently");
            return;
        }

        User user = userOpt.get();

        // Cooldown: prevent token spam per account
        Optional<PasswordResetToken> latest = tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent()) {
            Instant cooldownCutoff = Instant.now().minusSeconds(cooldownMinutes * 60);
            PasswordResetToken lastToken = latest.get();
            if (!lastToken.isUsed() && !lastToken.isRevoked()
                    && lastToken.getCreatedAt().isAfter(cooldownCutoff)) {
                log.warn("Password reset cooldown active for user [{}]; request ignored", user.getId());
                return;
            }
        }

        // Revoke all prior active tokens before issuing a new one
        List<PasswordResetToken> activeTokens =
                tokenRepository.findByUserIdAndUsedFalseAndRevokedFalse(user.getId());
        if (!activeTokens.isEmpty()) {
            activeTokens.forEach(t -> t.setRevoked(true));
            tokenRepository.saveAll(activeTokens);
            log.debug("Revoked {} prior token(s) for user [{}]", activeTokens.size(), user.getId());
        }

        // 32 cryptographically secure random bytes → 64-char hex raw token
        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        Instant now = Instant.now();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.getId())
                .tokenHash(tokenHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(tokenExpiryMinutes * 60))
                .build();
        tokenRepository.save(resetToken);

        String resetLink = frontendUrl + resetPath + "?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink, tokenExpiryMinutes);
        log.info("Password reset token issued for user [{}]", user.getId());
    }

    /**
     * Validates the reset token and updates the user's password.
     */
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256Hex(rawToken);

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ErrorCode.PASSWORD_RESET_TOKEN_INVALID.getMessage()));

        if (resetToken.isUsed() || resetToken.isRevoked()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.PASSWORD_RESET_TOKEN_USED.getMessage());
        }

        if (!Instant.now().isBefore(resetToken.getExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED.getMessage());
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));

        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(now);
        tokenRepository.save(resetToken);

        log.info("Password successfully reset for user [{}]", user.getId());
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available by the Java spec
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
