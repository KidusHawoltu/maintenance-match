package com.maintenance_match.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMaintainerProfileRequest {
    private String name;
    private Boolean isAvailable;
    private Integer capacity;
    private Double latitude;
    private Double longitude;
}
