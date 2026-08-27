package co.edu.uco.ucomap.dto;

import co.edu.uco.ucomap.model.SettingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTOs del recurso AppSetting.
 *
 * {@link Response} — representación pública retornada al cliente.
 * {@link Request}  — payload de entrada para POST y PUT.
 */
public class AppSettingDTO {

    /**
     * Respuesta pública del parámetro de configuración.
     * Desacopla la representación MongoDB de la API HTTP.
     */
    @Data
    @Builder
    public static class Response {
        private String      key;
        private String      value;
        private SettingType type;
        private String      description;
        private String      category;
        private boolean     active;
        private Instant     updatedAt;
    }

    /**
     * Payload de entrada para crear (POST) o actualizar (PUT) un parámetro.
     *
     * - En POST: {@code key} es obligatoria (es el identificador del documento).
     * - En PUT:  {@code key} es ignorada; el identificador viene por el path variable.
     */
    public record Request(
            @Size(max = 100, message = "La clave no puede exceder 100 caracteres")
            String key,

            @NotBlank(message = "El valor no puede estar vacío")
            @Size(max = 500, message = "El valor no puede exceder 500 caracteres")
            String value,

            SettingType type,

            @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
            String description,

            @Size(max = 100, message = "La categoría no puede exceder 100 caracteres")
            String category
    ) {}
}
