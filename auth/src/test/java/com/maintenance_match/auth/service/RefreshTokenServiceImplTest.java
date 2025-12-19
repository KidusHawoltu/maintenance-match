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
public class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        // Set the expiration duration using ReflectionTestUtils since it's a @Value field
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 600000L); // 10 minutes
        user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
    }

    @Test
    void createRefreshToken_shouldReturnNewToken() {
        // Given
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Then
        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.getUser()).isEqualTo(user);
        assertThat(refreshToken.getToken()).isNotNull();
        assertThat(refreshToken.getExpiryDate()).isAfter(Instant.now());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void verifyExpiration_whenTokenIsNotExpired_shouldReturnToken() {
        // Given
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(10000)) // Expires in 10 seconds
                .build();

        // When
        RefreshToken verifiedToken = refreshTokenService.verifyExpiration(token);

        // Then
        assertThat(verifiedToken).isEqualTo(token);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void verifyExpiration_whenTokenIsExpired_shouldThrowExceptionAndDeleteToken() {
        // Given
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().minusMillis(10000)) // Expired 10 seconds ago
                .build();

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(token))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("Refresh token was expired");

        verify(refreshTokenRepository, times(1)).delete(token);
    }

    @Test
    void rotateRefreshToken_withValidToken_shouldDeleteOldAndCreateNew() {
        // Given
        String oldTokenValue = UUID.randomUUID().toString();
        RefreshToken oldToken = RefreshToken.builder()
                .token(oldTokenValue)
                .user(user)
                .expiryDate(Instant.now().plusMillis(10000))
                .build();

        when(refreshTokenRepository.findByToken(oldTokenValue)).thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(oldTokenValue);

        // Then
        verify(refreshTokenRepository, times(1)).delete(oldToken);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
        assertThat(newRefreshToken).isNotNull();
        assertThat(newRefreshToken.getToken()).isNotEqualTo(oldTokenValue);
        assertThat(newRefreshToken.getUser()).isEqualTo(user);
    }
}
