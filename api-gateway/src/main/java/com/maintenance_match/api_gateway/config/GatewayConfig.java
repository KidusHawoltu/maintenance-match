package com.maintenance_match.api_gateway.config;

import com.maintenance_match.api_gateway.dto.PublicKeyResponse;
import com.maintenance_match.api_gateway.filter.AuthenticationFilter;
import com.maintenance_match.api_gateway.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.PublicKey;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GatewayConfig {

    // Spring will inject the AuthenticationFilter and JwtUtil beans that already exist
    private final AuthenticationFilter authenticationFilter;
    private final JwtUtil jwtUtil;

    // Spring will inject the value from application.yml
    @Value("${app.clients.auth-public-key-url}")
    private String publicKeyUrl;

    /**
     * This method runs once after the bean is constructed.
     * It makes a synchronous (blocking) call to the auth service to fetch the JWT public key.
     * This is a critical startup step to ensure the gateway can validate tokens.
     */
    @PostConstruct
    public void init() {
        log.info("Attempting to fetch JWT public key from auth service at: {}", publicKeyUrl);
        try {
            // Use WebClient to make the HTTP GET request
            PublicKeyResponse response = WebClient.create(publicKeyUrl)
                    .get()
                    .retrieve()
                    .bodyToMono(PublicKeyResponse.class)
                    .block(); // .block() makes the call synchronous, which is what we need on startup

            if (response != null && response.getPublicKey() != null) {
                // Convert the Base64 string key into a PublicKey object
                PublicKey publicKey = jwtUtil.getPublicKeyFromString(response.getPublicKey());

                // Set the public key on our AuthenticationFilter instance
                authenticationFilter.setPublicKey(publicKey);

                log.info(">>> Successfully fetched and configured public key. Gateway security is now active. <<<");
            } else {
                log.error("!!! Fetched a null or invalid response from auth service for public key. !!!");
            }
        } catch (Exception e) {
            // If this fails, the gateway cannot validate any tokens.
            // This is a fatal error for the gateway's security functionality.
            log.error("!!!!!! FAILED TO FETCH PUBLIC KEY. GATEWAY CANNOT VALIDATE TOKENS. !!!!!!", e);
            // In a production system, you might force the application to exit here to prevent it from running in an insecure state.
        }
    }
}