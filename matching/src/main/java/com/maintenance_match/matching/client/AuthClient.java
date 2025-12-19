package com.maintenance_match.matching.client;

import com.maintenance_match.matching.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "auth-client", url = "${app.clients.auth-url}")
public interface AuthClient {

    @GetMapping("/api/internal/users/{userId}")
    UserDto getUserById(@PathVariable("userId") UUID userId);
}
