package com.maintenance_match.auth.service;

import com.maintenance_match.auth.dto.LoginRequest;
import com.maintenance_match.auth.dto.RefreshTokenRequest;
import com.maintenance_match.auth.dto.SignUpRequest;
import com.maintenance_match.auth.exception.BadRequestException;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.RefreshToken;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import com.maintenance_match.auth.repository.UserRepository;
import com.maintenance_match.auth.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private SignUpRequest signUpRequest;
    private User user;

    @BeforeEach
    void setUp() {
        signUpRequest = SignUpRequest.builder()
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("1234567890")
                .build();

        user = User.builder()
                .id(java.util.UUID.randomUUID())
                .email("test@example.com")
                .password("encodedPassword")
                .build();
    }

    @Test
    void signUpUser_whenEmailNotExists_shouldRegisterActiveUserAndReturnTokens() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(signUpRequest.getPassword())).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(any(User.class)))
                .thenReturn(RefreshToken.builder().token("refreshToken").build());

        // When
        var response = authenticationService.signUpUser(signUpRequest);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.isActive()).isTrue();
        assertThat(savedUser.getApprovalStatus()).isNull();

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("accessToken");
    }

    @Test
    void signUpUser_whenEmailExists_shouldThrowBadRequestException() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> authenticationService.signUpUser(signUpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email address already in use.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void signUpMaintainer_whenEmailNotExists_shouldRegisterInactiveMaintainer() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(signUpRequest.getPassword())).thenReturn("encodedPassword");

        // When
        authenticationService.signUpMaintainer(signUpRequest);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.MAINTAINER);
        assertThat(savedUser.isActive()).isFalse();
        assertThat(savedUser.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);

        // Verify that no tokens were generated
        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void signUpMaintainer_whenEmailExists_shouldThrowBadRequestException() {
        // Given
        when(userRepository.findByEmail(signUpRequest.getEmail())).thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> authenticationService.signUpMaintainer(signUpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email address already in use.");
    }

    @Test
    void login_withValidCredentials_shouldReturnTokens() {
        // Given
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("newAccessToken");
        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(RefreshToken.builder().token("newRefreshToken").build());

        // When
        var response = authenticationService.login(loginRequest);

        // Then
        // Verify that the authentication manager was called
        verify(authenticationManager, times(1))
                .authenticate(any());

        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    void refreshToken_withValidToken_shouldReturnNewTokens() {
        // Given
        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest("validRefreshToken");

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token("newRotatedToken")
                .user(user) // Use the user from setUp()
                .build();

        when(refreshTokenService.rotateRefreshToken(refreshTokenRequest.getRefreshToken()))
                .thenReturn(newRefreshToken);
        when(jwtService.generateToken(user)).thenReturn("newAccessToken");

        // When
        var response = authenticationService.refreshToken(refreshTokenRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        assertThat(response.getRefreshToken()).isEqualTo("newRotatedToken");
        verify(refreshTokenService, times(1)).rotateRefreshToken("validRefreshToken");
        verify(jwtService, times(1)).generateToken(user);
    }
}
