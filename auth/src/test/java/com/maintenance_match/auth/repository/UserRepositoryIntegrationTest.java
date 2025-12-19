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

public class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void whenFindByEmail_andUserExists_thenReturnsUser() {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .password("password")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("1234567890")
                .role(Role.USER)
                .build();
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(foundUser.get().getRole()).isEqualTo(Role.USER);
        assertThat(foundUser.get().getPhoneNumber()).isEqualTo("1234567890");
    }

    @Test
    void whenFindByEmail_andUserDoesNotExist_thenReturnsEmpty() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isNotPresent();
    }

    @Test
    void findByApprovalStatus_shouldReturnOnlyUsersWithThatStatus() {
        // --- Given ---
        User pendingMaintainer = User.builder()
                .email("pending@example.com").password("pw").phoneNumber("111")
                .role(Role.MAINTAINER).approvalStatus(ApprovalStatus.PENDING).isActive(false)
                .build();

        User anotherPendingMaintainer = User.builder()
                .email("pending2@example.com").password("pw").phoneNumber("222")
                .role(Role.MAINTAINER).approvalStatus(ApprovalStatus.PENDING).isActive(false)
                .build();

        User approvedMaintainer = User.builder()
                .email("approved@example.com").password("pw").phoneNumber("333")
                .role(Role.MAINTAINER).approvalStatus(ApprovalStatus.APPROVED).isActive(true)
                .build();

        User regularUser = User.builder()
                .email("user@example.com").password("pw").phoneNumber("444")
                .role(Role.USER).approvalStatus(null) // Regular users have no status
                .build();

        userRepository.saveAll(List.of(pendingMaintainer, anotherPendingMaintainer, approvedMaintainer, regularUser));

        // --- When ---
        List<User> foundUsers = userRepository.findByApprovalStatus(ApprovalStatus.PENDING);

        // --- Then ---
        assertThat(foundUsers).hasSize(2);
        assertThat(foundUsers)
                .extracting(User::getEmail)
                .containsExactlyInAnyOrder("pending@example.com", "pending2@example.com");
    }

    @Test
    void findByApprovalStatus_whenNoUsersMatch_shouldReturnEmptyList() {
        // --- Given ---
        User pendingMaintainer = User.builder()
                .email("pending@example.com").password("pw").phoneNumber("111")
                .role(Role.MAINTAINER).approvalStatus(ApprovalStatus.PENDING).isActive(false)
                .build();
        userRepository.save(pendingMaintainer);

        // --- When ---
        List<User> foundUsers = userRepository.findByApprovalStatus(ApprovalStatus.REJECTED);

        // --- Then ---
        assertThat(foundUsers).isEmpty();
    }
}
