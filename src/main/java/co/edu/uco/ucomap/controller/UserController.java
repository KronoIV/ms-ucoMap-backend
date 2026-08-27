package co.edu.uco.ucomap.controller;

import co.edu.uco.ucomap.common.dto.ApiSuccess;
import co.edu.uco.ucomap.dto.UserDTO;
import co.edu.uco.ucomap.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiSuccess<List<UserDTO.Response>>> listUsers() {
        return ResponseEntity.ok(ApiSuccess.of(userService.listUsers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiSuccess<UserDTO.Response>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiSuccess<UserDTO.Response>> createUser(@Valid @RequestBody UserDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccess.of(userService.createUser(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccess<UserDTO.Response>> updateUser(@PathVariable String id,
                                                                   @Valid @RequestBody UserDTO.UpdateRequest request) {
        return ResponseEntity.ok(ApiSuccess.of(userService.updateUser(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccess<Void>> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiSuccess.of(null));
    }
}
