package com.maintenance_match.matching.dto;

import com.maintenance_match.matching.model.Maintainer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintainerDto {
    private UUID id;
    private String name;
    private double latitude;
    private double longitude;

    public static MaintainerDto fromEntity(Maintainer maintainer) {
        return MaintainerDto.builder()
                .id(maintainer.getId())
                .name(maintainer.getName())
                .latitude(maintainer.getLocation().getY())
                .longitude(maintainer.getLocation().getX())
                .build();
    }
}
