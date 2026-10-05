package com.larv.pharmacy.auth.dto;

/** Optional body for logout: also revokes the given refresh token. */
public record LogoutRequest(String refreshToken) {
}
