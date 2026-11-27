package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.UcoVpsDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UcoVpsServiceTest {

    // 80 caracteres: con signWith(key) a secas jjwt firmaría HS512, que el VPS rechaza
    private static final String SECRET = "s".repeat(80);

    private static UcoVpsService service(String secret, String baseUrl) {
        return new UcoVpsService(secret, "k7", baseUrl, "uco", 600, 1800);
    }

    private static Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(UcoVpsService.ISSUER)
                .requireAudience(UcoVpsService.AUDIENCE)
                .build()
                .parseSignedClaims(token);
    }

    @Test
    void appTokenOnlyLocalizesInTheCampusMapSet() {
        UcoVpsDTO.TokenResponse res = service(SECRET, "https://vps.example/").appToken("sesion-12345678");

        Jws<Claims> jws = parse(res.token());
        assertThat(jws.getHeader().getKeyId()).isEqualTo("k7");
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        Claims c = jws.getPayload();
        assertThat(c.get("scope", String.class)).isEqualTo("localize");
        assertThat(c.get("mapSetId", String.class)).isEqualTo("uco");
        assertThat(c.get("sid", String.class)).isEqualTo("sesion-12345678");
        assertThat(c.getSubject()).isEqualTo("app");
        assertThat(Duration.between(c.getIssuedAt().toInstant(), c.getExpiration().toInstant())).hasSeconds(600);
        assertThat(res.expiresAt()).isAfter(Instant.now().plusSeconds(590));
        assertThat(res.baseUrl()).isEqualTo("https://vps.example");
        assertThat(res.scope()).isEqualTo("localize");
    }

    @Test
    void invalidSessionIdGetsARandomOne() {
        UcoVpsService vps = service(SECRET, "https://vps.example");

        String sid = parse(vps.appToken("../../etc").token()).getPayload().get("sid", String.class);
        assertThat(UUID.fromString(sid)).isNotNull();
        assertThat(parse(vps.appToken(null).token()).getPayload().get("sid", String.class)).isNotEqualTo(sid);
    }

    @Test
    void adminTokenCarriesTheAdminEmail() {
        UcoVpsDTO.TokenResponse res = service(SECRET, "https://vps.example").adminToken("admin@uco.edu.co");

        Claims c = parse(res.token()).getPayload();
        assertThat(c.get("scope", String.class)).isEqualTo("admin");
        assertThat(c.getSubject()).isEqualTo("admin@uco.edu.co");
        assertThat(c.get("mapSetId", String.class)).isEqualTo("uco");
        assertThat(Duration.between(c.getIssuedAt().toInstant(), c.getExpiration().toInstant())).hasSeconds(1800);
    }

    @Test
    void withoutSecretOrUrlTheVpsIsOff() {
        assertThat(service("", "https://vps.example").isConfigured()).isFalse();
        assertThat(service(SECRET, "").isConfigured()).isFalse();
        assertThatThrownBy(() -> service("", "").appToken(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> service("corto", "https://vps.example"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
