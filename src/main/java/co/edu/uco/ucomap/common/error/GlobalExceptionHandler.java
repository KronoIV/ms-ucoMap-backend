package co.edu.uco.ucomap.common.error;

import co.edu.uco.ucomap.common.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("Error {}: {}", ex.getStatusCode().value(), message, ex);
        } else {
            log.warn("Petición rechazada {}: {}", ex.getStatusCode().value(), message);
        }
        return ResponseEntity.status(ex.getStatusCode()).body(ApiError.of(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Inválido",
                        (a, b) -> a
                ));
        log.warn("Validación fallida — campos={}", details.keySet());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR.getMessage(), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la petición ilegible: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parámetro con formato inválido: {}", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR.getMessage(), Map.of(ex.getName(), "Formato inválido")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ErrorCode.AUTH_ACCESS_DENIED.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        log.warn("Ruta inexistente: {} /{}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
