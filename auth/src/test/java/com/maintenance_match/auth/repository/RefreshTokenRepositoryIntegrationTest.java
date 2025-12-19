package com.maintenance_match.auth.repository;

import com.maintenance_match.auth.AbstractIntegrationTest;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RefreshTokenRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        User user = User.builder()
                .email("tokenuser@example.com")
                .password("password")
                .phoneNumber("0987654321")
                .role(Role.USER)
                .build();
        savedUser = userRepository.save(user);
    }

    @Test
    void whenFindByToken_andTokenExists_thenReturnsToken() {
        // Given
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .token(tokenValue)
                .user(savedUser)
                .expiryDate(Instant.now().plusMillis(100000))
                .build();
        refreshTokenRepository.save(token);

        // When
        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken(tokenValue);

        // Then
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getToken()).isEqualTo(tokenValue);
        assertThat(foundToken.get().getUser().getId()).isEqualTo(savedUser.getId());
    }

    @Test
    void whenFindByToken_andTokenDoesNotExist_thenReturnsEmpty() {
        // When
        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken(UUID.randomUUID().toString());

        // Then
        assertThat(foundToken).isNotPresent();
    }
}
