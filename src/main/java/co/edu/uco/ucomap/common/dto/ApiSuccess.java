package co.edu.uco.ucomap.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiSuccess<T>(
        T data,
        boolean succeeded,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message
) {

    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(data, true, null);
    }

    public static ApiSuccess<Void> ofMessage(String message) {
        return new ApiSuccess<>(null, true, message);
    }
}
