package com.larv.pharmacy.user.dto;

import com.larv.pharmacy.user.Role;
import com.larv.pharmacy.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        Role role,
        boolean enabled,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
