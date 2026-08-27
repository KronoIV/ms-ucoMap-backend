package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.AppSettingDTO;
import co.edu.uco.ucomap.service.AppSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final AppSettingService settingService;

    @GetMapping
    public ResponseEntity<ApiSuccess<List<AppSettingDTO.Response>>> getAll() {
        return ResponseEntity.ok(ApiSuccess.of(settingService.findAll()));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiSuccess<AppSettingDTO.Response>> getByKey(@PathVariable String key) {
        return ResponseEntity.ok(ApiSuccess.of(settingService.findByKey(key)));
    }

    @PostMapping
    public ResponseEntity<ApiSuccess<AppSettingDTO.Response>> create(
            @Valid @RequestBody AppSettingDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccess.of(settingService.create(request)));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiSuccess<AppSettingDTO.Response>> update(
            @PathVariable String key,
            @Valid @RequestBody AppSettingDTO.Request request) {
        return ResponseEntity.ok(ApiSuccess.of(settingService.update(key, request)));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String key) {
        settingService.delete(key);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
