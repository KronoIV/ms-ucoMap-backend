package co.edu.uco.ucomap.security.filter;

import co.edu.uco.ucomap.common.dto.ApiError;
import co.edu.uco.ucomap.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds}")
    private long windowSeconds;

    @Value("${app.rate-limit.trusted-proxies}")
    private String trustedProxiesConfig;

    private Set<String> trustedProxies;

    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    private final ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        Deque<Long> timestamps = requestLog.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            long windowStart = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                log.warn("Rate limit excedido — ip={} path={}", ip, request.getRequestURI());
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", String.valueOf(windowSeconds));
                objectMapper.writeValue(response.getWriter(),
                        ApiError.of(ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()));
                return;
            }

            timestamps.addLast(now);
        }

        periodicCleanup(now, windowMillis);
        chain.doFilter(request, response);
    }

    // removes stale IPs from memory every 60 seconds
    private void periodicCleanup(long now, long windowMillis) {
        long last = lastCleanup.get();
        if (now - last > 60_000L && lastCleanup.compareAndSet(last, now)) {
            requestLog.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty()
                            || now - entry.getValue().peekLast() > windowMillis;
                }
            });
        }
    }

    // X-Forwarded-For is only trusted when the direct connection comes from a configured proxy
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxies.isEmpty() && trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].strip();
            }
        }
        return remoteAddr;
    }
}
