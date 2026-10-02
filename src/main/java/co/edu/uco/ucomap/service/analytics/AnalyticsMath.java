package co.edu.uco.ucomap.service.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estadística descriptiva usada por la analítica (sin estado, fácil de probar). */
public final class AnalyticsMath {

    private AnalyticsMath() {}

    public static Double mean(Collection<? extends Number> values) {
        if (values.isEmpty()) return null;
        double sum = 0;
        for (Number v : values) sum += v.doubleValue();
        return sum / values.size();
    }

    public static Double median(List<? extends Number> values) {
        return percentile(values, 50);
    }

    /** Percentil con interpolación lineal entre los dos valores vecinos (método de Excel/NumPy por defecto). */
    public static Double percentile(List<? extends Number> values, double p) {
        if (values.isEmpty()) return null;
        double[] sorted = values.stream().mapToDouble(Number::doubleValue).sorted().toArray();
        if (sorted.length == 1) return sorted[0];
        double rank = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo);
    }

    public static Double ratio(long part, long total) {
        return total == 0 ? null : (double) part / total;
    }

    /** Un tramo del histograma: [from, to) en las unidades de los valores; to null = sin límite. */
    public record Range(String label, long from, Long to) {
        boolean contains(long v) {
            return v >= from && (to == null || v < to);
        }
    }

    /** Cuenta cuántos valores caen en cada tramo, en el orden de los tramos. */
    public static Map<String, Long> histogram(Collection<Long> values, List<Range> ranges) {
        Map<String, Long> counts = new LinkedHashMap<>();
        ranges.forEach(r -> counts.put(r.label(), 0L));
        for (long v : values) {
            for (Range r : ranges) {
                if (r.contains(v)) {
                    counts.merge(r.label(), 1L, Long::sum);
                    break;
                }
            }
        }
        return counts;
    }

    /** Duración de las visitas: tramos pensados para una app de orientación (minutos, no horas). */
    public static final List<Range> SESSION_RANGES = List.of(
            new Range("< 30 s", 0, 30_000L),
            new Range("30 s – 1 min", 30_000, 60_000L),
            new Range("1 – 3 min", 60_000, 180_000L),
            new Range("3 – 5 min", 180_000, 300_000L),
            new Range("5 – 10 min", 300_000, 600_000L),
            new Range("10 – 20 min", 600_000, 1_200_000L),
            new Range("20 – 30 min", 1_200_000, 1_800_000L),
            new Range("> 30 min", 1_800_000, null));

    public static final List<Range> SESSIONS_PER_USER = List.of(
            new Range("1 visita", 1, 2L),
            new Range("2 visitas", 2, 3L),
            new Range("3 – 5", 3, 6L),
            new Range("6 – 10", 6, 11L),
            new Range("> 10", 11, null));

    /** Distancia en línea recta al edificio al empezar (m). */
    public static final List<Range> START_DISTANCE = List.of(
            new Range("< 50 m", 0, 50L),
            new Range("50 – 150 m", 50, 150L),
            new Range("150 – 300 m", 150, 300L),
            new Range("300 – 600 m", 300, 600L),
            new Range("> 600 m", 600, null));

    public static <K> List<Map.Entry<K, Long>> sortedDesc(Map<K, Long> counts) {
        List<Map.Entry<K, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<K, Long>comparingByValue().reversed());
        return entries;
    }

    /** Distancia entre dos puntos GPS (m), fórmula de haversine. */
    public static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
