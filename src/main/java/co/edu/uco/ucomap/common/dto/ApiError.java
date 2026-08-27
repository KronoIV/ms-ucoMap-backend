package co.edu.uco.ucomap.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record ApiError(
        String error,
        boolean succeeded,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> details
) {

    public static ApiError of(String code) {
        return new ApiError(code, false, null);
    }

    public static ApiError of(String code, Map<String, String> details) {
        return new ApiError(code, false, details);
    }
}
