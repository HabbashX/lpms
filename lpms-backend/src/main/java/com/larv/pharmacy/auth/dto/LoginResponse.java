package com.larv.pharmacy.auth.dto;

import com.larv.pharmacy.user.Role;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserSummary user) {

    public record UserSummary(Long id, String username, Role role) {
    }
}
