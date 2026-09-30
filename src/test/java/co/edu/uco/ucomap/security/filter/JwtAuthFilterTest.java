package co.edu.uco.ucomap.security.filter;

import co.edu.uco.ucomap.model.Role;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import co.edu.uco.ucomap.security.jwt.JwtUtil;
import co.edu.uco.ucomap.security.jwt.JwtUtilTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String EMAIL = "admin@uco.edu.co";

    private final UserRepository userRepository = mock(UserRepository.class);
    private JwtUtil jwtUtil;
    private JwtAuthFilter filter;
    private MockHttpServletRequest lastRequest;

    @BeforeEach
    void setUp() {
        jwtUtil = JwtUtilTest.newJwtUtil(JwtUtilTest.SECRET, 60_000);
        filter = new JwtAuthFilter(jwtUtil, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User user(boolean active, Instant passwordChangedAt) {
        return User.builder().id("u1").email(EMAIL).role(Role.ADMIN)
                .active(active).passwordChangedAt(passwordChangedAt).build();
    }

    private MockFilterChain run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        lastRequest = request;
        return chain;
    }

    @Test
    void withoutHeaderRequestContinuesAnonymous() throws Exception {
        MockFilterChain chain = run(null);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenOfActiveUserAuthenticatesWithRole() throws Exception {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user(true, Instant.now().minusSeconds(3600))));

        run("Bearer " + jwtUtil.generateToken(EMAIL, "ADMIN"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(EMAIL);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void tokenIssuedRightAfterPasswordChangeIsAccepted() throws Exception {
        // El iat del JWT tiene precisión de segundos; passwordChangedAt tiene milisegundos
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, Instant.now())));

        run("Bearer " + jwtUtil.generateToken(EMAIL, "ADMIN"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void tokenIssuedBeforePasswordChangeIsRejected() throws Exception {
        String token = jwtUtil.generateToken(EMAIL, "ADMIN");
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user(true, Instant.now().plusSeconds(5))));

        MockFilterChain chain = run("Bearer " + token);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(lastRequest.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR)).isEqualTo("AUTH_TOKEN_INVALID");
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false, null)));

        run("Bearer " + jwtUtil.generateToken(EMAIL, "ADMIN"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(lastRequest.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR)).isEqualTo("AUTH_INACTIVE_USER");
    }

    @Test
    void deletedUserIsRejected() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        run("Bearer " + jwtUtil.generateToken(EMAIL, "ADMIN"));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(lastRequest.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR)).isEqualTo("AUTH_INACTIVE_USER");
    }

    @Test
    void expiredTokenIsMarkedExpired() throws Exception {
        String expired = JwtUtilTest.newJwtUtil(JwtUtilTest.SECRET, -1_000).generateToken(EMAIL, "ADMIN");

        MockFilterChain chain = run("Bearer " + expired);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(lastRequest.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR)).isEqualTo("AUTH_TOKEN_EXPIRED");
    }

    @Test
    void malformedTokenIsMarkedInvalid() throws Exception {
        run("Bearer abc.def.ghi");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(lastRequest.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR)).isEqualTo("AUTH_TOKEN_INVALID");
    }
}
