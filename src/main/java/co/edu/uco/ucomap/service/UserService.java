package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.UserDTO;
import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.Role;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO.Response> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UserDTO.Response getUserById(String id) {
        return toResponseDTO(findOrThrow(id));
    }

    public UserDTO.Response createUser(UserDTO.CreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ErrorCode.CONFLICT.getMessage());
        }

        Instant now = Instant.now();
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponseDTO(userRepository.save(user));
    }

    public UserDTO.Response updateUser(String id, UserDTO.UpdateRequest request) {
        if (request.getEmail() == null && request.getPassword() == null && request.getActive() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.getMessage());
        }

        User user = findOrThrow(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        ErrorCode.CONFLICT.getMessage());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        user.setUpdatedAt(Instant.now());
        return toResponseDTO(userRepository.save(user));
    }

    public void deleteUser(String id) {
        findOrThrow(id);
        userRepository.deleteById(id);
    }

    private User findOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    private UserDTO.Response toResponseDTO(User user) {
        return UserDTO.Response.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
