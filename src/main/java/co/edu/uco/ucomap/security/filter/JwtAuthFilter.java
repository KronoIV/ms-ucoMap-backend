package co.edu.uco.ucomap.security.filter;

import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import co.edu.uco.ucomap.security.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTR = "auth_error";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                jwtUtil.parseClaims(token);

                String email = jwtUtil.extractEmail(token);
                Instant tokenIssuedAt = jwtUtil.extractIssuedAt(token);
                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                    log.warn("Token de usuario inexistente o inactivo — email={}", email);
                    request.setAttribute(AUTH_ERROR_ATTR, "AUTH_INACTIVE_USER");
                } else {
                    User user = userOpt.get();
                    // Reject tokens issued before the last password change (JWT iat has second precision)
                    if (user.getPasswordChangedAt() != null
                            && tokenIssuedAt.isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS))) {
                        log.warn("Token emitido antes del último cambio de contraseña — userId={}", user.getId());
                        request.setAttribute(AUTH_ERROR_ATTR, "AUTH_TOKEN_INVALID");
                    } else {
                        String role = user.getRole().name();
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                );
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        } catch (ExpiredJwtException e) {
            log.debug("Token expirado");
            request.setAttribute(AUTH_ERROR_ATTR, "AUTH_TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token inválido: {}", e.getMessage());
            request.setAttribute(AUTH_ERROR_ATTR, "AUTH_TOKEN_INVALID");
        }

        chain.doFilter(request, response);
    }
}
