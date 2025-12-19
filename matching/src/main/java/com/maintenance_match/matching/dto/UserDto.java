package com.maintenance_match.matching.dto;

import lombok.Data;
import java.util.UUID;

// This DTO represents the user data we expect to get back from the auth service.
@Data
public class UserDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
}
