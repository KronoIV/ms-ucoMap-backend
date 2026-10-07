package co.edu.uco.ucomap.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import co.edu.uco.ucomap.common.dto.ApiError;
import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.security.filter.JwtAuthFilter;
import co.edu.uco.ucomap.security.filter.RateLimitFilter;
import co.edu.uco.ucomap.security.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    String attr = (String) req.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTR);
                    ErrorCode ec = safeValueOf(attr, ErrorCode.AUTH_TOKEN_MISSING);
                    writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, ApiError.of(ec.getMessage()));
                })
                .accessDeniedHandler((req, res, e) ->
                    writeJson(res, HttpServletResponse.SC_FORBIDDEN, ApiError.of(ErrorCode.AUTH_ACCESS_DENIED.getMessage()))
                )
            )
            .authorizeHttpRequests(auth -> auth

                // ── Public routes ──────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,  "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/sessions/ping").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/trips").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/campus/**").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/campus").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/buildings/**").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/rooms/**").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/graph/**").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/settings/**").permitAll()
                .requestMatchers(HttpMethod.GET,   "/api/navigation/navmesh").permitAll()
                .requestMatchers(HttpMethod.POST,  "/api/multiset/token").permitAll()

                // ── Protected: ADMIN only ──────────────────────────────────
                .requestMatchers("/api/sessions/**").hasRole("ADMIN")
                .requestMatchers("/api/trips/**").hasRole("ADMIN")
                .requestMatchers("/api/analytics/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/multiset/**").hasRole("ADMIN")
                .requestMatchers("/api/navigation/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            );

        return http.build();
    }

    private void writeJson(HttpServletResponse res, int status, Object body) {
        try {
            res.setStatus(status);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(res.getWriter(), body);
        } catch (Exception ignored) {
            // Response already committed or writer unavailable — nothing to do
        }
    }

    private static ErrorCode safeValueOf(String name, ErrorCode fallback) {
        if (name == null) return fallback;
        try { return ErrorCode.valueOf(name); } catch (IllegalArgumentException e) { return fallback; }
    }
}
