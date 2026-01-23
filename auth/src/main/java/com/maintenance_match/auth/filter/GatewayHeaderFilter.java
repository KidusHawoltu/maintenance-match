package com.maintenance_match.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 1. Check for the headers injected by the API Gateway
        String userId = request.getHeader("X-User-ID");
        String userRole = request.getHeader("X-User-Role");

        // 2. If headers are present, create an Authentication object
        if (userId != null && userRole != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Create a token with the User ID as the principal and the Role as authority
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(userRole))
            );

            // 3. Set the context
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // 4. Continue the chain
        filterChain.doFilter(request, response);
    }
}