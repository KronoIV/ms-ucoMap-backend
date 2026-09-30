package co.edu.uco.ucomap.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * IP real del cliente. Solo se lee X-Forwarded-For si la conexión viene de un proxy de confianza
 * (IP o rango CIDR configurado en app.rate-limit.trusted-proxies).
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${app.rate-limit.trusted-proxies:}") String trustedProxiesConfig) {
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrusted(remoteAddr)) return remoteAddr;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remoteAddr;

        // Cada proxy agrega al final la IP que le habló; lo que está a la izquierda lo puede inventar el cliente
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!hop.isEmpty() && !isTrusted(hop)) return hop;
        }
        return remoteAddr;
    }

    private boolean isTrusted(String ip) {
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(ip)) return true;
            } catch (IllegalArgumentException ignored) {
                // Valor que no es una IP (p. ej. "unknown")
            }
        }
        return false;
    }
}
