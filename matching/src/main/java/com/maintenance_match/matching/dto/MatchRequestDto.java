package com.maintenance_match.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchRequestDto {

    @NotNull(message = "Maintainer ID cannot be null")
    private UUID maintainerId;

    @NotBlank(message = "Problem description cannot be blank")
    private String problemDescription;

    @NotNull(message = "Latitude cannot be null")
    private Double latitude;

    @NotNull(message = "Longitude cannot be null")
    private Double longitude;
}
