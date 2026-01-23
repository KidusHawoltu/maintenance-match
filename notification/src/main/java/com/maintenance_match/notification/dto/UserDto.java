package com.maintenance_match.notification.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    // We will add 'emailNotificationsEnabled' later when we update the Auth Service
}