package com.larv.pharmacy.auth.dto;

import com.larv.pharmacy.user.Role;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user) {

    public record UserSummary(Long id, String username, Role role) {
    }
}
