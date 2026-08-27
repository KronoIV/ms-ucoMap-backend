package co.edu.uco.ucomap.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_TOKEN_MISSING("No se proporcionó un token de autenticación."),
    AUTH_TOKEN_INVALID("El token de autenticación es inválido."),
    AUTH_TOKEN_EXPIRED("El token de autenticación ha expirado."),
    AUTH_INACTIVE_USER("La cuenta de usuario está desactivada."),
    AUTH_CREDENTIAL_INVALID("Credenciales inválidas. Verifica tu email y contraseña."),
    AUTH_ACCESS_DENIED("No tienes permisos para realizar esta operación."),
    RATE_LIMIT_EXCEEDED("Demasiadas peticiones. Por favor espera un momento antes de reintentar."),
    RESOURCE_NOT_FOUND("El recurso solicitado no existe."),
    VALIDATION_ERROR("Los datos proporcionados no son válidos."),
    CONFLICT("Ya existe un recurso con los datos proporcionados."),
    INTERNAL_SERVER_ERROR("Error inesperado del servidor."),
    PASSWORD_RESET_TOKEN_INVALID("El enlace de restablecimiento no es válido."),
    PASSWORD_RESET_TOKEN_EXPIRED("El enlace de restablecimiento ha expirado. Solicita uno nuevo."),
    PASSWORD_RESET_TOKEN_USED("Este enlace ya fue utilizado o fue reemplazado."),
    PASSWORD_RESET_COOLDOWN("Por favor espera antes de solicitar otro enlace de restablecimiento."),
    EMAIL_DELIVERY_ERROR("No se pudo enviar el correo. Intenta de nuevo más tarde.");

    private final String message;
}
