package co.edu.uco.ucomap.security;

import co.edu.uco.ucomap.common.config.CorsConfig;
import co.edu.uco.ucomap.common.dto.PageResponse;
import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.common.web.ClientIpResolver;
import co.edu.uco.ucomap.controller.AuthController;
import co.edu.uco.ucomap.controller.GraphController;
import co.edu.uco.ucomap.controller.NavigationTripController;
import co.edu.uco.ucomap.controller.UserController;
import co.edu.uco.ucomap.dto.TripFilter;
import co.edu.uco.ucomap.dto.TripReportDTO;
import co.edu.uco.ucomap.model.Role;
import co.edu.uco.ucomap.model.TripStatus;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import co.edu.uco.ucomap.security.config.SecurityConfig;
import co.edu.uco.ucomap.security.jwt.JwtUtil;
import co.edu.uco.ucomap.security.jwt.JwtUtilTest;
import co.edu.uco.ucomap.security.service.UserDetailsServiceImpl;
import co.edu.uco.ucomap.service.GraphService;
import co.edu.uco.ucomap.service.NavigationTripService;
import co.edu.uco.ucomap.service.PasswordResetService;
import co.edu.uco.ucomap.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reglas de acceso reales (SecurityConfig + filtros JWT) sobre controladores con servicios simulados.
 * No usa base de datos.
 */
@WebMvcTest(controllers = {AuthController.class, GraphController.class,
        NavigationTripController.class, UserController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtUtil.class, UserDetailsServiceImpl.class, ClientIpResolver.class})
class SecurityIntegrationTest {

    private static final String EMAIL = "admin@uco.edu.co";
    private static final String PASSWORD = "Clave12345";
    private static final String VALID_TRIP_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserRepository userRepository;
    @MockitoBean GraphService graphService;
    @MockitoBean NavigationTripService tripService;
    @MockitoBean UserService userService;
    @MockitoBean PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        givenUser(true);
    }

    private void givenUser(boolean active) {
        User user = User.builder().id("u1").email(EMAIL).role(Role.ADMIN).active(active)
                .passwordHash(new BCryptPasswordEncoder().encode(PASSWORD))
                .passwordChangedAt(Instant.now().minusSeconds(3600)).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private String bearer() {
        return "Bearer " + jwtUtil.generateToken(EMAIL, "ADMIN");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static TripReportDTO trip(String tripId) {
        return new TripReportDTO(tripId, "device-1", TripStatus.IN_PROGRESS,
                "co101", "CO 101", "CO", "outdoor", 100.0, 5.0, null, null, null, null,
                null, null, null, null, null);
    }

    // ── Rutas públicas que usa la app móvil ──────────────────

    @Test
    void appCanReadGraphWithoutToken() throws Exception {
        when(graphService.findAllNodes()).thenReturn(List.of());

        mvc.perform(get("/api/graph/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true));
    }

    @Test
    void appCanReportTripsWithoutToken() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0 (iPhone)")
                        .content(json(trip(VALID_TRIP_ID))))
                .andExpect(status().isOk());

        verify(tripService).report(any(TripReportDTO.class), eq("Mozilla/5.0 (iPhone)"));
    }

    @Test
    void invalidTripReportIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(json(trip("../../etc/passwd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.succeeded").value(false))
                .andExpect(jsonPath("$.error").value(ErrorCode.VALIDATION_ERROR.getMessage()))
                .andExpect(jsonPath("$.details.tripId").exists());

        verify(tripService, never()).report(any(), any());
    }

    @Test
    void malformedJsonReturnsValidationError() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    // ── Rutas de administración ──────────────────────────────

    @Test
    void adminRoutesRequireToken() throws Exception {
        mvc.perform(get("/api/trips"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_TOKEN_MISSING.getMessage()));
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/graph/nodes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(tripService, never()).findPage(any(), anyInt(), anyInt());
        verify(graphService, never()).createNode(any());
    }

    @Test
    void adminGetsPagedTripsWithFilters() throws Exception {
        when(tripService.findPage(any(), eq(1), eq(50))).thenReturn(PageResponse.of(List.of(), 1, 50, 75));

        mvc.perform(get("/api/trips").header("Authorization", bearer())
                        .param("page", "1").param("size", "50")
                        .param("from", "2026-09-01T05:00:00Z").param("building", "CO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(75))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        verify(tripService).findPage(
                eq(new TripFilter(Instant.parse("2026-09-01T05:00:00Z"), null, "CO")), eq(1), eq(50));
    }

    @Test
    void invalidDateParameterIsBadRequest() throws Exception {
        mvc.perform(get("/api/trips/summary").header("Authorization", bearer()).param("from", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.from").exists());
    }

    @Test
    void tripsExportIsCsvAttachment() throws Exception {
        when(tripService.exportCsv(any())).thenReturn("Inicio,Fin\n");

        mvc.perform(get("/api/trips/export").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).startsWith("text/csv"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .contains("attachment").contains("recorridos-ucomap.csv"));
    }

    @Test
    void adminRoutesWorkWithValidToken() throws Exception {
        when(userService.listUsers()).thenReturn(List.of());

        mvc.perform(get("/api/users").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true));
    }

    @Test
    void expiredTokenIsReportedAsExpired() throws Exception {
        String expired = JwtUtilTest.newJwtUtil(JwtUtilTest.SECRET, -1_000).generateToken(EMAIL, "ADMIN");

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_TOKEN_EXPIRED.getMessage()));
    }

    @Test
    void forgedTokenIsRejected() throws Exception {
        String forged = JwtUtilTest.newJwtUtil("attacker-secret-with-more-than-32-chars!", 60_000)
                .generateToken(EMAIL, "ADMIN");

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_TOKEN_INVALID.getMessage()));
        verify(userService, never()).listUsers();
    }

    @Test
    void tokenOfDeactivatedUserIsRejected() throws Exception {
        String token = bearer();
        givenUser(false);

        mvc.perform(get("/api/users").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_INACTIVE_USER.getMessage()));
    }

    // ── Autenticación ────────────────────────────────────────

    @Test
    void loginWithValidCredentialsReturnsUsableToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(EMAIL);
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_CREDENTIAL_INVALID.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownEmailGetsTheSameErrorAsWrongPassword() throws Exception {
        when(userRepository.findByEmail("nadie@uco.edu.co")).thenReturn(Optional.empty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nadie@uco.edu.co", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(ErrorCode.AUTH_CREDENTIAL_INVALID.getMessage()));
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        givenUser(false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordNeverRevealsIfEmailExists() throws Exception {
        String registered = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nadie@uco.edu.co"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(registered).isEqualTo(unknown);
        verify(passwordResetService).requestReset(EMAIL);
    }

    @Test
    void resetPasswordRequiresMinimumLength() throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "abc", "newPassword", "corta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }
}
