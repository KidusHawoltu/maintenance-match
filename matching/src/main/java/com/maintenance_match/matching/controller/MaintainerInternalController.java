package com.maintenance_match.matching.controller;

import com.maintenance_match.matching.model.Maintainer;
import com.maintenance_match.matching.repository.MaintainerRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/maintainers")
@RequiredArgsConstructor
//@Hidden
public class MaintainerInternalController {

    private final MaintainerRepository maintainerRepository;

    @PostMapping
    public ResponseEntity<Void> createMaintainerProfile(@RequestBody CreateMaintainerProfileRequest request) {
        if (maintainerRepository.findByUserId(request.getUserId()).isPresent()) {
            return ResponseEntity.ok().build();
        }

        Maintainer newProfile = Maintainer.builder()
                .userId(request.getUserId())
                .name(request.getName())
                .isAvailable(true)
                .build();

        maintainerRepository.save(newProfile);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class CreateMaintainerProfileRequest {
        private UUID userId;
        private String name;
    }
}
