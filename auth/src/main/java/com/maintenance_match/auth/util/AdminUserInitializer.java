package com.maintenance_match.auth.util;

import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "admin@maintenancematch.com";
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            log.info("No admin user found. Creating a default admin user.");
            User adminUser = User.builder()
                    .firstName("Admin")
                    .lastName("User")
                    .email(adminEmail)
                    .password(passwordEncoder.encode("AdminPassword123"))
                    .phoneNumber("0000000000")
                    .role(Role.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(adminUser);
            log.info("Default admin user created with email: {}", adminEmail);
        } else {
            log.info("Admin user already exists.");
        }
    }
}
