package com.maintenance_match.notification.client;

import com.maintenance_match.notification.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthClient {

    private final RestTemplate restTemplate;

    @Value("${app.clients.auth-url}")
    private String authUrl;

    public UserDto getUserById(UUID userId) {
        String url = String.format("%s/api/internal/users/%s", authUrl, userId);
        try {
            return restTemplate.getForObject(url, UserDto.class);
        } catch (RestClientException ex) {
            log.warn("Failed to fetch user {} from auth service: {}", userId, ex.getMessage());
            return null;
        }
    }
}