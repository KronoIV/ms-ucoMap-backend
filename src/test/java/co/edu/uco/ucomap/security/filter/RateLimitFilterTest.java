package co.edu.uco.ucomap.security.filter;

import co.edu.uco.ucomap.common.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static RateLimitFilter newFilter(int maxRequests, String trustedProxies) {
        return newFilter(maxRequests, maxRequests, trustedProxies);
    }

    private static RateLimitFilter newFilter(int maxRequests, int authMaxRequests, String trustedProxies) {
        RateLimitFilter filter = new RateLimitFilter(new ObjectMapper(), new ClientIpResolver(trustedProxies));
        ReflectionTestUtils.setField(filter, "maxRequests", maxRequests);
        ReflectionTestUtils.setField(filter, "authMaxRequests", authMaxRequests);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60L);
        return filter;
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String remoteAddr, String forwardedFor)
            throws Exception {
        return call(filter, "/api/rooms", remoteAddr, forwardedFor);
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String uri, String remoteAddr,
                                                String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksRequestsOverTheLimitWithApiError() throws Exception {
        RateLimitFilter filter = newFilter(3, "");

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "10.0.0.1", null).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call(filter, "10.0.0.1", null);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("\"succeeded\":false");
    }

    @Test
    void limitsAreCountedPerClient() throws Exception {
        RateLimitFilter filter = newFilter(1, "");

        assertThat(call(filter, "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.2", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.1", null).getStatus()).isEqualTo(429);
    }

    @Test
    void spoofedForwardedForFromUntrustedClientIsIgnored() throws Exception {
        RateLimitFilter filter = newFilter(1, "");

        assertThat(call(filter, "10.0.0.1", "1.1.1.1").getStatus()).isEqualTo(200);
        // Cambiar X-Forwarded-For no debe permitir evadir el límite
        assertThat(call(filter, "10.0.0.1", "2.2.2.2").getStatus()).isEqualTo(429);
    }

    @Test
    void forwardedForFromTrustedProxyIdentifiesTheRealClient() throws Exception {
        RateLimitFilter filter = newFilter(1, "10.10.0.1");

        assertThat(call(filter, "10.10.0.1", "1.1.1.1, 10.10.0.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.10.0.1", "2.2.2.2").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.10.0.1", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void trustedProxiesCanBeConfiguredAsCidrRanges() throws Exception {
        RateLimitFilter filter = newFilter(1, "10.0.0.0/8");

        assertThat(call(filter, "10.214.3.7", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.99.0.2", "2.2.2.2").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.3.3.3", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void clientCannotSpoofItsIpThroughTheProxy() throws Exception {
        // El proxy agrega la IP real al final: lo de la izquierda lo escribió el cliente
        RateLimitFilter filter = newFilter(1, "10.0.0.0/8");

        assertThat(call(filter, "10.0.0.5", "9.9.9.1, 1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "10.0.0.5", "9.9.9.2, 1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    void authEndpointsHaveTheirOwnStricterLimit() throws Exception {
        RateLimitFilter filter = newFilter(100, 2, "");

        assertThat(call(filter, "/api/auth/login", "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/auth/login", "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/auth/forgot-password", "10.0.0.1", null).getStatus()).isEqualTo(429);
        // Agotar el límite de login no bloquea el uso normal de la app desde esa IP
        assertThat(call(filter, "/api/rooms", "10.0.0.1", null).getStatus()).isEqualTo(200);
    }
}
