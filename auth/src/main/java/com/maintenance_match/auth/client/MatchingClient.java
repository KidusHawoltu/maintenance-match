package com.maintenance_match.auth.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "matching-client", url = "${app.clients.matching-url}")
public interface MatchingClient {

    @PostMapping("/api/internal/maintainers")
    void createMaintainerProfile(@RequestBody CreateMaintainerProfileRequest request);

    @Data
    class CreateMaintainerProfileRequest {
        private UUID userId;
        private String name;
    }
}
