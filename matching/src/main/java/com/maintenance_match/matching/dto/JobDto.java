package com.maintenance_match.matching.dto;

import com.maintenance_match.matching.model.Job;
import com.maintenance_match.matching.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {
    private UUID id;
    private JobStatus status;
    private String problemDescription;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private ParticipantDto user;
    private ParticipantDto maintainer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDto {
        private UUID id;
        private String name;
        private String phoneNumber;
    }
}
