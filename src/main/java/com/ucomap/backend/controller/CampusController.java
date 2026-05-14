package com.ucomap.backend.controller;

import com.ucomap.backend.dto.CampusDataDTO;
import com.ucomap.backend.service.CampusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint principal: devuelve TODOS los datos del campus en un solo request.
 * El frontend lo llama una vez al inicializar para reemplazar los datos quemados.
 *
 * GET /api/campus
 */
@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusController {

    private final CampusService campusService;

    @GetMapping
    public ResponseEntity<CampusDataDTO> getCampusData() {
        return ResponseEntity.ok(campusService.getCampusData());
    }
}

