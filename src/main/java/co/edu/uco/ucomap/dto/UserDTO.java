package co.edu.uco.ucomap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class UserDTO {

    @Data
    public static class CreateRequest {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class UpdateRequest {

        @Email
        private String email;

        private String password;

        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        private String id;
        private String email;
        private String role;
        private boolean active;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
