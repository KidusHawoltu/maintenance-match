package com.maintenance_match.api_gateway.filter;

import com.maintenance_match.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.Setter;
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

            // Check if the request path is for a public endpoint (like login or signup)
            if (isPublicEndpoint(request)) {
                return chain.filter(exchange); // If public, let it pass without checks
            }

            // Check for the Authorization header
            if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                return this.onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return this.onError(exchange, "Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix

            try {
                if (!jwtUtil.isTokenValid(token, this.publicKey)) {
                    return this.onError(exchange, "Invalid or Expired Token", HttpStatus.UNAUTHORIZED);
                }

                // Extract all claims from the token
                Claims claims = jwtUtil.extractAllClaims(token, this.publicKey);
                String userId = claims.getSubject();
                String userRole = claims.get("role", String.class);

                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-ID", userId)
                        .header("X-User-Role", userRole)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
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
        return path.contains("/api/auth/") ||
                path.contains("/swagger-ui") ||
                path.contains("/swagger") ||
                path.contains("/v3/api-docs");
    }

    public static class Config {
        // Configuration properties for the filter can be defined here if needed.
    }
}
