package com.maintenance_match.auth.service;

import com.maintenance_match.auth.dto.AdminUserView;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    /**
     * Retrieves a list of all users in the system.
     * @return A list of users formatted for admin view.
     */
    List<AdminUserView> getAllUsers();

    /**
     * Retrieves a list of all maintainers pending approval.
     * @return A list of pending maintainers.
     */
    List<AdminUserView> getPendingMaintainers();

    /**
     * Approves a pending maintainer, activates their account, and triggers profile creation.
     * @param userId The ID of the user to approve.
     * @return The updated user view.
     */
    AdminUserView approveMaintainer(UUID userId);

    /**
     * Rejects a pending maintainer.
     * @param userId The ID of the user to reject.
     * @return The updated user view.
     */
    AdminUserView rejectMaintainer(UUID userId);

    /**
     * Disables a user's account, preventing them from logging in.
     * @param userId The ID of the user to disable.
     * @return The updated user view.
     */
    AdminUserView disableUser(UUID userId);
}
