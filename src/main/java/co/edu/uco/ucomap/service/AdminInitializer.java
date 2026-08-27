package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Role;
import co.edu.uco.ucomap.model.User;
import co.edu.uco.ucomap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.data.init-on-startup:true}")
    private boolean initOnStartup;

    @Override
    public void run(String... args) {
        if (!initOnStartup) return;

        if (userRepository.existsByEmail("admin@admin.com")) {
            log.debug("Admin user already exists, skipping creation");
            return;
        }

        Instant now = Instant.now();
        User admin = User.builder()
                .id(UUID.randomUUID().toString())
                .email("admin@admin.com")
                .passwordHash(passwordEncoder.encode("1234"))
                .role(Role.ADMIN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        userRepository.save(admin);
        log.info("Default admin user created: admin@admin.com");
    }
}
