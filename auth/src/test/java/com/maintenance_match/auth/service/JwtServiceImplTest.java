package com.maintenance_match.auth.service;

import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.PublicKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtServiceImplTest {

    private JwtServiceImpl jwtService;
    private User userDetails;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl();
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtService, "privateKeyResource", new ClassPathResource("keys/private_key.pem"));
        ReflectionTestUtils.setField(jwtService, "publicKeyResource", new ClassPathResource("keys/public_key.pem"));
        jwtService.init();

        userId = UUID.randomUUID();
        userDetails = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("password")
                .phoneNumber("1234567890")
                .role(Role.MAINTAINER)
                .build();
    }

    @Test
    void generateToken_shouldCreateValidJwtWithUuidAsSubject() {
        String token = jwtService.generateToken(userDetails);
        PublicKey publicKey = jwtService.getPublicKey();
        Claims claims = Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token).getBody();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("MAINTAINER");
        assertThat(claims.get("phoneNumber", String.class)).isEqualTo("1234567890");
    }

    @Test
    void extractUsername_shouldReturnUuidString() {
        String token = jwtService.generateToken(userDetails);
        String extractedSubject = jwtService.extractUsername(token);
        assertThat(extractedSubject).isEqualTo(userId.toString());
    }

    @Test
    void isTokenValid_withValidTokenAndUser_shouldReturnTrue() {
        String token = jwtService.generateToken(userDetails);
        boolean isValid = jwtService.isTokenValid(token, userDetails);
        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_withDifferentUser_shouldReturnFalse() {
        // Given a token generated for our main user
        String token = jwtService.generateToken(userDetails);

        // And a different user object
        UserDetails otherUserDetails = User.builder()
                .id(UUID.randomUUID()) // Different ID
                .email("other@example.com")
                .build();

        // When we check the token's validity against this OTHER user
        boolean isValid = jwtService.isTokenValid(token, otherUserDetails);

        // Then it should be false because the subject (UUID) in the token doesn't match
        assertThat(isValid).isFalse();
    }

    @Test
    void isTokenValid_withExpiredToken_shouldReturnFalse() throws InterruptedException {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 1L);
        String expiredToken = jwtService.generateToken(userDetails);
        Thread.sleep(5);
        boolean isValid = jwtService.isTokenValid(expiredToken, userDetails);
        assertThat(isValid).isFalse();
    }
}
