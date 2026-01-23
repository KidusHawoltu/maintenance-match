package com.maintenance_match.auth.repository;

import com.maintenance_match.auth.AbstractIntegrationTest;
import com.maintenance_match.auth.model.ApprovalStatus;
import com.maintenance_match.auth.model.Role;
import com.maintenance_match.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void findByEmail_shouldReturnUser_whenUserExists() {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .password("encoded_pass")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("123456789")
                .role(Role.USER)
                .isActive(true)
                .build();
        userRepository.save(user);

        // When
        Optional<User> result = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().getFirstName()).isEqualTo("John");
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenUserDoesNotExist() {
        // When
        Optional<User> result = userRepository.findByEmail("notfound@example.com");

        // Then
        assertThat(result).isNotPresent();
    }

    @Test
    void findByApprovalStatus_shouldReturnListOfUsers() {
        // Given
        User m1 = User.builder().email("m1@test.com").password("p").phoneNumber("1").role(Role.MAINTAINER)
                .approvalStatus(ApprovalStatus.PENDING).isActive(false).build();
        User m2 = User.builder().email("m2@test.com").password("p").phoneNumber("2").role(Role.MAINTAINER)
                .approvalStatus(ApprovalStatus.PENDING).isActive(false).build();
        User m3 = User.builder().email("m3@test.com").password("p").phoneNumber("3").role(Role.MAINTAINER)
                .approvalStatus(ApprovalStatus.APPROVED).isActive(true).build();

        userRepository.saveAll(List.of(m1, m2, m3));

        // When
        List<User> pendingUsers = userRepository.findByApprovalStatus(ApprovalStatus.PENDING);
        List<User> approvedUsers = userRepository.findByApprovalStatus(ApprovalStatus.APPROVED);

        // Then
        assertThat(pendingUsers).hasSize(2);
        assertThat(pendingUsers).extracting(User::getEmail).containsExactlyInAnyOrder("m1@test.com", "m2@test.com");

        assertThat(approvedUsers).hasSize(1);
        assertThat(approvedUsers.get(0).getEmail()).isEqualTo("m3@test.com");
    }

    @Test
    void findByApprovalStatus_shouldReturnEmptyList_whenNoMatches() {
        // When
        List<User> result = userRepository.findByApprovalStatus(ApprovalStatus.REJECTED);

        // Then
        assertThat(result).isEmpty();
    }
}
