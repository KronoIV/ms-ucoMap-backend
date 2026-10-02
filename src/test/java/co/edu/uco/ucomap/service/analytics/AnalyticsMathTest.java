package co.edu.uco.ucomap.service.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnalyticsMathTest {

    @Test
    void medianAndPercentilesInterpolate() {
        List<Long> v = List.of(10L, 20L, 30L, 40L);
        assertThat(AnalyticsMath.median(v)).isEqualTo(25.0);
        assertThat(AnalyticsMath.percentile(v, 75)).isEqualTo(32.5);
        assertThat(AnalyticsMath.percentile(v, 0)).isEqualTo(10.0);
        assertThat(AnalyticsMath.percentile(v, 100)).isEqualTo(40.0);
        assertThat(AnalyticsMath.median(List.of(7L))).isEqualTo(7.0);
        assertThat(AnalyticsMath.median(List.<Long>of())).isNull();
    }

    @Test
    void medianIgnoresAFewHugeSessions() {
        // Por esto el panel muestra la mediana: dos visitas olvidadas no deben cambiar la visita típica
        List<Long> v = List.of(60_000L, 70_000L, 80_000L, 90_000L, 100_000L, 36_000_000L, 40_000_000L);
        assertThat(AnalyticsMath.median(v)).isEqualTo(90_000.0);
        assertThat(AnalyticsMath.mean(v)).isGreaterThan(10_000_000.0);
    }

    @Test
    void histogramUsesHalfOpenRanges() {
        Map<String, Long> h = AnalyticsMath.histogram(List.of(0L, 29_999L, 30_000L, 59_999L, 3_600_000L),
                AnalyticsMath.SESSION_RANGES);
        assertThat(h.get("< 30 s")).isEqualTo(2);
        assertThat(h.get("30 s – 1 min")).isEqualTo(2);
        assertThat(h.get("> 30 min")).isEqualTo(1);
        assertThat(h.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(5);
        assertThat(h.keySet()).first().isEqualTo("< 30 s");
    }

    @Test
    void ratioWithoutBaseIsNull() {
        assertThat(AnalyticsMath.ratio(1, 0)).isNull();
        assertThat(AnalyticsMath.ratio(1, 4)).isEqualTo(0.25);
    }

    @Test
    void haversineMatchesKnownDistance() {
        // ~111 m por milésima de grado de latitud
        assertThat(AnalyticsMath.haversineM(6.149, -75.366, 6.150, -75.366)).isCloseTo(111.2, within(0.5));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Version/17.4 Mobile/15E148 Safari/604.1 | Safari | iOS 17.4",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 CriOS/129.0 Mobile/15E148 Safari/604.1 | Chrome | iOS 18.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Instagram 300.0 | Vista web en app | iOS 17.4",
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 SamsungBrowser/25.0 Chrome/121.0 Mobile Safari/537.36 | Samsung Internet | Android 14",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 | Chrome | Android 13",
            "Mozilla/5.0 (Linux; Android 12; wv) AppleWebKit/537.36 Version/4.0 Chrome/120.0 Mobile Safari/537.36 | Vista web en app | Android 12",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36 Edg/124.0 | Edge | Windows",
            "Unknown | Desconocido | Desconocido",
    })
    void browserAndOsFromUserAgent(String ua, String browser, String os) {
        assertThat(UserAgents.browser(ua)).isEqualTo(browser);
        assertThat(UserAgents.os(ua)).isEqualTo(os);
    }
}
