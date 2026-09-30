package co.edu.uco.ucomap.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static RateLimitFilter newFilter(int maxRequests, String trustedProxies) {
        RateLimitFilter filter = new RateLimitFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "maxRequests", maxRequests);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60L);
        ReflectionTestUtils.setField(filter, "trustedProxiesConfig", trustedProxies);
        filter.init();
        return filter;
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String remoteAddr, String forwardedFor)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rooms");
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
}
