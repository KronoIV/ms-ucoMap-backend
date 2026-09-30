package co.edu.uco.ucomap.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtUtilTest {

    public static final String SECRET = "test-secret-only-for-unit-tests-0123456789abcdef";

    public static JwtUtil newJwtUtil(String secret, long expirationMs) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expirationMs", expirationMs);
        ReflectionTestUtils.invokeMethod(util, "init");
        return util;
    }

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = newJwtUtil(SECRET, 60_000);
    }

    @Test
    void generatedTokenContainsEmailAndRole() {
        String token = jwtUtil.generateToken("admin@uco.edu.co", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("admin@uco.edu.co");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtUtil.extractIssuedAt(token)).isNotNull();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateToken("admin@uco.edu.co", "ADMIN");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "x." + parts[2];

        assertThat(jwtUtil.isTokenValid(forged)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtUtil other = newJwtUtil("another-secret-with-at-least-32-characters!!", 60_000);
        String foreign = other.generateToken("admin@uco.edu.co", "ADMIN");

        assertThat(jwtUtil.isTokenValid(foreign)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil expiring = newJwtUtil(SECRET, -1_000);
        String token = expiring.generateToken("admin@uco.edu.co", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
        assertThatThrownBy(() -> jwtUtil.parseClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void garbageAndEmptyTokensAreRejected() {
        assertThat(jwtUtil.isTokenValid("not-a-jwt")).isFalse();
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> newJwtUtil("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
