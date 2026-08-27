package co.edu.uco.ucomap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

public class AuthDTO {

    @Data
    public static class LoginRequest {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class Response {

        private String token;
        private String email;
        private String role;
    }

    @Data
    public static class ForgotPasswordRequest {

        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {

        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        private String newPassword;
    }
}
