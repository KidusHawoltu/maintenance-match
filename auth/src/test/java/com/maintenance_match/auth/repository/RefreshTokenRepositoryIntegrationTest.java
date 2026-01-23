package com.maintenance_match.auth.repository;

import com.maintenance_match.auth.AbstractIntegrationTest;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Create a user to associate tokens with
        testUser = User.builder()
                .email("token.tester@example.com")
                .password("password")
                .phoneNumber("123456789")
                .role(Role.USER)
                .isActive(true)
                .build();
        userRepository.save(testUser);
    }

    @Test
    void findByToken_shouldReturnToken_whenTokenExists() {
        // Given
        String rawToken = UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .token(rawToken)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();
        refreshTokenRepository.save(token);

        // When
        Optional<RefreshToken> result = refreshTokenRepository.findByToken(rawToken);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo(rawToken);
        assertThat(result.get().getUser().getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    void findByUser_shouldReturnToken_whenUserHasOne() {
        // Given
        String rawToken = "active-token-123";
        RefreshToken token = RefreshToken.builder()
                .token(rawToken)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();
        refreshTokenRepository.save(token);

        // When
        Optional<RefreshToken> result = refreshTokenRepository.findByUser(testUser);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getToken()).isEqualTo(rawToken);
    }

    @Test
    @Transactional // Required for custom delete methods in tests
    void deleteByUser_shouldRemoveToken() {
        // Given
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();
        refreshTokenRepository.save(token);
        assertThat(refreshTokenRepository.findByUser(testUser)).isPresent();

        // When
        refreshTokenRepository.deleteByUser(testUser);

        // Then
        assertThat(refreshTokenRepository.findByUser(testUser)).isNotPresent();
    }

    @Test
    void findByToken_shouldReturnEmpty_whenTokenDoesNotExist() {
        // When
        Optional<RefreshToken> result = refreshTokenRepository.findByToken("non-existent-token");

        // Then
        assertThat(result).isNotPresent();
    }
}
