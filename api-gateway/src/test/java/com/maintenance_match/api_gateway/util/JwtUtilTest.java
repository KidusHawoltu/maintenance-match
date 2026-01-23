package com.maintenance_match.api_gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String userId;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        userId = UUID.randomUUID().toString();

        // 1. Generate a real RSA Key Pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }

    // Helper method to create a test token signed with our test private key
    private String createToken(String subject, Date expiration, String role) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    @Test
    void extractUsername_shouldReturnCorrectSubject() {
        // Given
        String token = createToken(userId, new Date(System.currentTimeMillis() + 100000), "USER");

        // When
        String extractedUser = jwtUtil.extractUsername(token, publicKey);

        // Then
        assertThat(extractedUser).isEqualTo(userId);
    }

    @Test
    void isTokenValid_whenTokenIsFresh_shouldReturnTrue() {
        // Given
        String token = createToken(userId, new Date(System.currentTimeMillis() + 100000), "USER");

        // When
        boolean isValid = jwtUtil.isTokenValid(token, publicKey);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_whenTokenIsExpired_shouldThrowException() {
        // Given: A token that expired 1 second ago
        String token = createToken(userId, new Date(System.currentTimeMillis() - 1000), "USER");

        // When/Then: JJWT throws ExpiredJwtException during parsing inside extractAllClaims
        assertThatThrownBy(() -> jwtUtil.isTokenValid(token, publicKey))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void getPublicKeyFromString_shouldConvertBase64ToKeyObject() {
        // Given
        String base64Key = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        // When
        PublicKey resultKey = jwtUtil.getPublicKeyFromString(base64Key);

        // Then
        assertThat(resultKey).isNotNull();
        assertThat(resultKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(resultKey.getEncoded()).isEqualTo(publicKey.getEncoded());
    }

    @Test
    void getPublicKeyFromString_withInvalidString_shouldThrowRuntimeException() {
        // Given
        String invalidKey = "not-a-base64-key";

        // When/Then
        assertThatThrownBy(() -> jwtUtil.getPublicKeyFromString(invalidKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse public key");
    }

    @Test
    void extractClaim_shouldReturnRoleClaim() {
        // Given
        String token = createToken(userId, new Date(System.currentTimeMillis() + 100000), "ADMIN");

        // When
        String role = jwtUtil.extractClaim(token, publicKey, claims -> claims.get("role", String.class));

        // Then
        assertThat(role).isEqualTo("ADMIN");
    }
}
