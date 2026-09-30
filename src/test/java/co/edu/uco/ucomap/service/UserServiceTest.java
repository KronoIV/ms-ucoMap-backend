package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.UserDTO;
import co.edu.uco.ucomap.model.Role;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService service;

    private static UserDTO.CreateRequest createRequest(String email, String password) {
        UserDTO.CreateRequest r = new UserDTO.CreateRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private static User stored() {
        Instant old = Instant.parse("2025-01-01T00:00:00Z");
        return User.builder().id("u1").email("admin@uco.edu.co").passwordHash("old-hash")
                .role(Role.ADMIN).createdAt(old).updatedAt(old).passwordChangedAt(old).build();
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(status));
    }

    @Test
    void createStoresHashedPasswordAsAdmin() {
        when(userRepository.existsByEmail("nuevo@uco.edu.co")).thenReturn(false);
        when(passwordEncoder.encode("Clave12345")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO.Response response = service.createUser(createRequest("nuevo@uco.edu.co", "Clave12345"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.getEmail()).isEqualTo("nuevo@uco.edu.co");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("admin@uco.edu.co")).thenReturn(true);

        assertStatus(() -> service.createUser(createRequest("admin@uco.edu.co", "Clave12345")), HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changingPasswordInvalidatesPreviousSessions() {
        User user = stored();
        Instant before = user.getPasswordChangedAt();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("OtraClave123")).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);
        UserDTO.UpdateRequest request = new UserDTO.UpdateRequest();
        request.setPassword("OtraClave123");

        service.updateUser("u1", request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordChangedAt()).isAfter(before);
    }

    @Test
    void deactivatingUserDoesNotTouchPassword() {
        User user = stored();
        Instant before = user.getPasswordChangedAt();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        UserDTO.UpdateRequest request = new UserDTO.UpdateRequest();
        request.setActive(false);

        UserDTO.Response response = service.updateUser("u1", request);

        assertThat(response.isActive()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        assertThat(user.getPasswordChangedAt()).isEqualTo(before);
    }

    @Test
    void emptyUpdateIsRejected() {
        assertStatus(() -> service.updateUser("u1", new UserDTO.UpdateRequest()), HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changingEmailToOneInUseIsRejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(stored()));
        when(userRepository.existsByEmail("otro@uco.edu.co")).thenReturn(true);
        UserDTO.UpdateRequest request = new UserDTO.UpdateRequest();
        request.setEmail("otro@uco.edu.co");

        assertStatus(() -> service.updateUser("u1", request), HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deletingUnknownUserReturnsNotFound() {
        when(userRepository.findById("x")).thenReturn(Optional.empty());

        assertStatus(() -> service.deleteUser("x"), HttpStatus.NOT_FOUND);
        verify(userRepository, never()).deleteById(any());
    }
}
