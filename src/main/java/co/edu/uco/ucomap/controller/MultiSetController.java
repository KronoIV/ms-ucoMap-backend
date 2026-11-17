package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.MapMeshDTO;
import co.edu.uco.ucomap.dto.MultiSetUsageDTO;
import co.edu.uco.ucomap.dto.VpsTokenDTO;
import co.edu.uco.ucomap.service.MultiSetService;
import co.edu.uco.ucomap.service.MultiSetUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/multiset")
@RequiredArgsConstructor
public class MultiSetController {

    private final MultiSetService multiSetService;
    private final MultiSetUsageService usageService;

    /** Estimación local del cupo: no gasta llamadas de MultiSet. */
    @GetMapping("/usage")
    public ResponseEntity<ApiSuccess<MultiSetUsageDTO.Response>> getUsage() {
        return ResponseEntity.ok(ApiSuccess.of(usageService.usage()));
    }

    @PutMapping("/usage")
    public ResponseEntity<ApiSuccess<MultiSetUsageDTO.Response>> updateUsage(
            @Valid @RequestBody MultiSetUsageDTO.Request body) {
        return ResponseEntity.ok(ApiSuccess.of(usageService.update(body)));
    }

    @GetMapping("/map-meshes")
    public ResponseEntity<ApiSuccess<List<MapMeshDTO>>> getMapMeshes(
            @RequestParam(defaultValue = "textured") String type) {
        return ResponseEntity.ok(ApiSuccess.of(multiSetService.getMapMeshes(!"raw".equalsIgnoreCase(type))));
    }

    /** Sin envoltura ApiSuccess: el SDK de MultiSet lee {@code token} en la raíz (endpoints.authUrl). */
    @PostMapping("/token")
    public ResponseEntity<VpsTokenDTO> getVpsToken() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(multiSetService.getVpsToken());
    }
}
