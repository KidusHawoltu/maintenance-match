package com.maintenance_match.api_gateway.filter;

import com.maintenance_match.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    // This field will be populated by a WebClient call on startup
    @Setter
    private PublicKey publicKey;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            log.info(">>> [AuthFilter] Intercepting request to: {}", request.getURI().getPath());

            if (this.publicKey == null) {
                log.error(">>> [AuthFilter] CRITICAL: Public Key is NULL. Cannot validate tokens. Check auth service connection on startup.");
                return this.onError(exchange, "Gateway security is not configured", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Check for the Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                // Check if the request path is for a public endpoint (like login or signup)
                if (isPublicEndpoint(request)) {
                    log.info(">>> [AuthFilter] Path is public. Skipping authentication.");
                    return chain.filter(exchange); // If public, let it pass without checks
                }
                log.warn(">>> [AuthFilter] Request is missing Authorization Header. Path: {}", request.getURI().getPath());
                return this.onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                // Check if the request path is for a public endpoint (like login or signup)
                if (isPublicEndpoint(request)) {
                    log.info(">>> [AuthFilter] Path is public. Skipping authentication.");
                    return chain.filter(exchange); // If public, let it pass without checks
                }
                log.warn(">>> [AuthFilter] Authorization Header is malformed. Path: {}", request.getURI().getPath());
                return this.onError(exchange, "Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix

            try {
                log.info(">>> [AuthFilter] Validating token...");
                if (!jwtUtil.isTokenValid(token, this.publicKey)) {
                    // Check if the request path is for a public endpoint (like login or signup)
                    if (isPublicEndpoint(request)) {
                        log.info(">>> [AuthFilter] Path is public. Skipping authentication.");
                        return chain.filter(exchange); // If public, let it pass without checks
                    }
                    log.warn(">>> [AuthFilter] Token validation failed (likely expired or invalid signature).");
                    return this.onError(exchange, "Invalid or Expired Token", HttpStatus.UNAUTHORIZED);
                }
                log.info(">>> [AuthFilter] Token is valid.");

                // Extract all claims from the token
                Claims claims = jwtUtil.extractAllClaims(token, this.publicKey);
                String userId = claims.getSubject();
                String userRole = claims.get("role", String.class);

                if (userRole == null) {
                    // Check if the request path is for a public endpoint (like login or signup)
                    if (isPublicEndpoint(request)) {
                        log.info(">>> [AuthFilter] Path is public. Skipping authentication.");
                        return chain.filter(exchange); // If public, let it pass without checks
                    }
                    log.warn(">>> [AuthFilter] Token is valid but is missing 'role' claim.");
                    return this.onError(exchange, "Token missing required claims", HttpStatus.UNAUTHORIZED);
                }

                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-ID", userId)
                        .header("X-User-Role", userRole)
                        .build();

                log.info(">>> [AuthFilter] Forwarding request for user {} with role {} to path {}.", userId, userRole, request.getURI().getPath());

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
                // Check if the request path is for a public endpoint (like login or signup)
                if (isPublicEndpoint(request)) {
                    log.info(">>> [AuthFilter] Path is public. Skipping authentication.");
                    return chain.filter(exchange); // If public, let it pass without checks
                }
                log.error(">>> [AuthFilter] An unexpected error occurred during token parsing: {}", e.getMessage());
                return this.onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path.contains("/actuator") ||
                path.contains("/api/auth") ||
                path.contains("/swagger-ui") ||
                path.contains("/swagger") ||
                path.contains("/v3/api-docs");
    }

    public static class Config {
        // Configuration properties for the filter can be defined here if needed.
    }
}