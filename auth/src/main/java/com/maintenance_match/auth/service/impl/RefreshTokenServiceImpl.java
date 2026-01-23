package com.maintenance_match.auth.service.impl;

import com.maintenance_match.auth.exception.TokenRefreshException;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.RefreshTokenRepository;
import com.maintenance_match.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private Long refreshTokenDurationMs;

    @Override
    public RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.findByUser(user).ifPresent(refreshTokenRepository::delete);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .token(UUID.randomUUID().toString())
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please make a new sign-in request.");
        }
        return token;
    }

    @Override
    public RefreshToken rotateRefreshToken(String oldToken) {
        RefreshToken refreshToken = this.findByToken(oldToken)
                .map(this::verifyExpiration)
                .orElseThrow(() -> new TokenRefreshException(oldToken, "Refresh token not found or expired."));

        User user = refreshToken.getUser();

        return this.createRefreshToken(user);
    }
}
