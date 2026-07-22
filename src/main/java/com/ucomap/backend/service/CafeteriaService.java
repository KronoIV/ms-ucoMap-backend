package com.ucomap.backend.service;

import com.ucomap.backend.model.Cafeteria;
import com.ucomap.backend.repository.CafeteriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
                        HttpStatus.NOT_FOUND, "Cafeteria no encontrada: " + cafeteriaId));
    }

    public Cafeteria create(Cafeteria cafeteria) {
        if (cafeteriaRepository.existsByCafeteriaId(cafeteria.getCafeteriaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cafeteria con cafeteriaId: " + cafeteria.getCafeteriaId());
        }
        return cafeteriaRepository.save(cafeteria);
    }

    public Cafeteria update(String cafeteriaId, Cafeteria updated) {
        Cafeteria existing = findByCafeteriaId(cafeteriaId);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setSchedule(updated.getSchedule());
        existing.setLat(updated.getLat());
        existing.setLng(updated.getLng());
        existing.setActive(updated.isActive());
        return cafeteriaRepository.save(existing);
    }

    public void delete(String cafeteriaId) {
        Cafeteria cafeteria = findByCafeteriaId(cafeteriaId);
        cafeteriaRepository.delete(cafeteria);
    }

    public Cafeteria patch(String cafeteriaId, Map<String, Object> fields) {
        Cafeteria cafeteria = findByCafeteriaId(cafeteriaId);
        if (fields.containsKey("name"))        cafeteria.setName((String) fields.get("name"));
        if (fields.containsKey("description")) cafeteria.setDescription((String) fields.get("description"));
        if (fields.containsKey("schedule"))    cafeteria.setSchedule((String) fields.get("schedule"));
        if (fields.containsKey("lat"))         cafeteria.setLat(((Number) fields.get("lat")).doubleValue());
        if (fields.containsKey("lng"))         cafeteria.setLng(((Number) fields.get("lng")).doubleValue());
        if (fields.containsKey("active"))      cafeteria.setActive((Boolean) fields.get("active"));
        return cafeteriaRepository.save(cafeteria);
    }
}
