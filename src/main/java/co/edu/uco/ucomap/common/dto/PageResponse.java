package co.edu.uco.ucomap.common.dto;

import java.util.List;

/** Página de resultados; {@code page} empieza en 0. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int MAX_SIZE = 100;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResponse<>(content, page, size, totalElements, (int) ((totalElements + size - 1) / size));
    }

    public static int safePage(int page) {
        return Math.max(0, page);
    }

    public static int safeSize(int size) {
        return Math.clamp(size, 1, MAX_SIZE);
    }
}
