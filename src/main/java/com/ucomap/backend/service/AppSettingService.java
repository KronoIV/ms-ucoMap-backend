package com.ucomap.backend.service;

import com.ucomap.backend.dto.AppSettingDTO;
import com.ucomap.backend.model.AppSetting;
import com.ucomap.backend.model.SettingType;
import com.ucomap.backend.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository repository;

    // ── Lectura ────────────────────────────────────────────────────────────────

    /** Retorna todos los parámetros activos. */
    public List<AppSettingDTO.Response> findAll() {
        return repository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Retorna un parámetro activo por clave. Lanza 404 si no existe o está inactivo. */
    public AppSettingDTO.Response findByKey(String key) {
        AppSetting setting = repository.findById(key)
                .filter(AppSetting::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parámetro no encontrado: " + key));
        return toResponse(setting);
    }

    // ── Escritura ─────────────────────────────────────────────────────────────

    /** Crea un nuevo parámetro. Lanza 409 si la clave ya existe. */
    public AppSettingDTO.Response create(AppSettingDTO.Request request) {
        String key = request.key();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La clave (key) es obligatoria al crear un parámetro");
        }
        if (repository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un parámetro con la clave: " + key);
        }

        AppSetting setting = AppSetting.builder()
                .key(key)
                .value(request.value())
                .type(request.type() != null ? request.type() : SettingType.STRING)
                .description(request.description())
                .category(request.category())
                .active(true)
                .updatedAt(Instant.now())
                .build();

        return toResponse(repository.save(setting));
    }

    /** Actualiza el valor y metadatos de un parámetro existente. Lanza 404 si no existe. */
    public AppSettingDTO.Response update(String key, AppSettingDTO.Request request) {
        AppSetting existing = repository.findById(key)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Parámetro no encontrado: " + key));

        existing.setValue(request.value());
        if (request.type() != null) existing.setType(request.type());
        if (request.description() != null) existing.setDescription(request.description());
        if (request.category() != null) existing.setCategory(request.category());
        existing.setUpdatedAt(Instant.now());

        return toResponse(repository.save(existing));
    }

    /** Elimina físicamente un parámetro. Lanza 404 si no existe. */
    public void delete(String key) {
        if (!repository.existsById(key)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Parámetro no encontrado: " + key);
        }
        repository.deleteById(key);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    /**
     * Obtiene el valor de un parámetro parseado al tipo {@code T}.
     * Retorna {@code defaultValue} si la clave no existe, está inactiva o el parseo falla.
     *
     * Útil para consumir parámetros desde otros servicios del backend sin
     * acoplarlos directamente al modelo {@link AppSetting}.
     *
     * @param key          Clave del parámetro.
     * @param targetType   Clase objetivo (Boolean.class, Integer.class, String.class…).
     * @param defaultValue Valor de respaldo.
     */
    @SuppressWarnings("unchecked")
    public <T> T getValueAs(String key, Class<T> targetType, T defaultValue) {
        try {
            return repository.findById(key)
                    .filter(AppSetting::isActive)
                    .map(s -> (T) parseValue(s.getValue(), targetType))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Object parseValue(String raw, Class<?> targetType) {
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.parseBoolean(raw);
        if (targetType == Integer.class || targetType == int.class)     return Integer.parseInt(raw);
        if (targetType == Long.class    || targetType == long.class)    return Long.parseLong(raw);
        if (targetType == Double.class  || targetType == double.class)  return Double.parseDouble(raw);
        return raw;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AppSettingDTO.Response toResponse(AppSetting setting) {
        return AppSettingDTO.Response.builder()
                .key(setting.getKey())
                .value(setting.getValue())
                .type(setting.getType())
                .description(setting.getDescription())
                .category(setting.getCategory())
                .active(setting.isActive())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }
}
