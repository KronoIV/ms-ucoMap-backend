package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Cafeteria;
import co.edu.uco.ucomap.repository.CafeteriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.edu.uco.ucomap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CafeteriaService {

    private final CafeteriaRepository cafeteriaRepository;

    public List<Cafeteria> findAll() {
        return cafeteriaRepository.findAll();
    }

    public List<Cafeteria> findAllActive() {
        return cafeteriaRepository.findByActiveTrue();
    }

    public Cafeteria findByCafeteriaId(String cafeteriaId) {
        return cafeteriaRepository.findByCafeteriaId(cafeteriaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    public Cafeteria create(Cafeteria cafeteria) {
        if (cafeteriaRepository.existsByCafeteriaId(cafeteria.getCafeteriaId())) {
            log.warn("Cafetería duplicada — cafeteriaId={}", cafeteria.getCafeteriaId());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }
        Cafeteria saved = cafeteriaRepository.save(cafeteria);
        log.info("Cafetería creada — cafeteriaId={}", saved.getCafeteriaId());
        return saved;
    }

    public Cafeteria update(String cafeteriaId, Cafeteria updated) {
        Cafeteria existing = findByCafeteriaId(cafeteriaId);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setSchedule(updated.getSchedule());
        existing.setLat(updated.getLat());
        existing.setLng(updated.getLng());
        existing.setActive(updated.isActive());
        Cafeteria saved = cafeteriaRepository.save(existing);
        log.info("Cafetería actualizada — cafeteriaId={}", cafeteriaId);
        return saved;
    }

    public void delete(String cafeteriaId) {
        Cafeteria cafeteria = findByCafeteriaId(cafeteriaId);
        cafeteriaRepository.delete(cafeteria);
        log.info("Cafetería eliminada — cafeteriaId={}", cafeteriaId);
    }

    public Cafeteria patch(String cafeteriaId, Map<String, Object> fields) {
        Cafeteria cafeteria = findByCafeteriaId(cafeteriaId);
        if (fields.containsKey("name"))        cafeteria.setName((String) fields.get("name"));
        if (fields.containsKey("description")) cafeteria.setDescription((String) fields.get("description"));
        if (fields.containsKey("schedule"))    cafeteria.setSchedule((String) fields.get("schedule"));
        if (fields.containsKey("lat"))         cafeteria.setLat(((Number) fields.get("lat")).doubleValue());
        if (fields.containsKey("lng"))         cafeteria.setLng(((Number) fields.get("lng")).doubleValue());
        if (fields.containsKey("active"))      cafeteria.setActive((Boolean) fields.get("active"));
        Cafeteria saved = cafeteriaRepository.save(cafeteria);
        log.info("Cafetería modificada — cafeteriaId={} campos={}", cafeteriaId, fields.keySet());
        return saved;
    }
}
