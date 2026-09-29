package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.MapMeshDTO;
import co.edu.uco.ucomap.service.MultiSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/multiset")
@RequiredArgsConstructor
public class MultiSetController {

    private final MultiSetService multiSetService;

    @GetMapping("/map-meshes")
    public ResponseEntity<ApiSuccess<List<MapMeshDTO>>> getMapMeshes(
            @RequestParam(defaultValue = "textured") String type) {
        return ResponseEntity.ok(ApiSuccess.of(multiSetService.getMapMeshes(!"raw".equalsIgnoreCase(type))));
    }
}
