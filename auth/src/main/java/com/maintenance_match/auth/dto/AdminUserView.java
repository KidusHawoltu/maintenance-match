package com.maintenance_match.auth.dto;

import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AdminUserView {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private Role role;
    private ApprovalStatus approvalStatus;
    private boolean isActive;

    public static AdminUserView fromEntity(User user) {
        return AdminUserView.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .approvalStatus(user.getApprovalStatus())
                .isActive(user.isActive())
                .build();
    }
}
