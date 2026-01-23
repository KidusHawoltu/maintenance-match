package com.maintenance_match.auth.service;

import com.maintenance_match.auth.exception.TokenRefreshException;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.RefreshTokenRepository;
import com.maintenance_match.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User testUser;
    private final Long durationMs = 600000L; // 10 minutes

    @BeforeEach
    void setUp() {
        // Inject the @Value field
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", durationMs);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("tester@example.com")
                .build();
    }

    @Test
    void createRefreshToken_shouldDeleteOldTokenAndSaveNewOne() {
        // Given
        RefreshToken oldToken = RefreshToken.builder().token("old-token").build();
        when(refreshTokenRepository.findByUser(testUser)).thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RefreshToken result = refreshTokenService.createRefreshToken(testUser);

        // Then
        verify(refreshTokenRepository).delete(oldToken); // Ensure old token is cleaned up
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(result.getToken()).isNotNull();
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getExpiryDate()).isAfter(Instant.now());
    }

    @Test
    void verifyExpiration_whenValid_shouldReturnToken() {
        // Given
        RefreshToken token = RefreshToken.builder()
                .token("valid-token")
                .expiryDate(Instant.now().plusSeconds(60))
                .build();

        // When
        RefreshToken result = refreshTokenService.verifyExpiration(token);

        // Then
        assertThat(result).isEqualTo(token);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void verifyExpiration_whenExpired_shouldDeleteAndThrowException() {
        // Given
        String tokenStr = "expired-token";
        RefreshToken token = RefreshToken.builder()
                .token(tokenStr)
                .expiryDate(Instant.now().minusSeconds(60))
                .build();

        // When / Then
        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(token))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void rotateRefreshToken_Success_shouldDeleteOldAndReturnNew() {
        // Given
        String oldTokenStr = "valid-old-token";
        RefreshToken oldToken = RefreshToken.builder()
                .token(oldTokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(600))
                .build();

        when(refreshTokenRepository.findByToken(oldTokenStr)).thenReturn(Optional.of(oldToken));
        // Mock internal call to createRefreshToken:
        when(refreshTokenRepository.findByUser(testUser)).thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RefreshToken result = refreshTokenService.rotateRefreshToken(oldTokenStr);

        // Then
        verify(refreshTokenRepository).delete(oldToken); // Deletes old token before creating new
        assertThat(result.getToken()).isNotEqualTo(oldTokenStr);
        assertThat(result.getUser()).isEqualTo(testUser);
    }

    @Test
    void rotateRefreshToken_NotFound_shouldThrowException() {
        // Given
        when(refreshTokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("invalid"))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("not found");
    }
}
