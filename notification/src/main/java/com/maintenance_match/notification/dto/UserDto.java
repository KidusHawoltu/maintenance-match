package com.maintenance_match.notification.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;
}