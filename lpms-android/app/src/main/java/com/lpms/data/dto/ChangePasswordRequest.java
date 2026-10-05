package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /auth/change-password} body → 204.
 *
 * <p>On success the backend invalidates all existing access and refresh tokens, so
 * the app must discard local tokens and force a fresh sign-in.</p>
 *
 * <p>Client-side policy (mirrored by the server):
 * 8–72 characters, at least one letter and one digit, must not contain the username
 * (case-insensitive), and must differ from the current password.</p>
 */
public final class ChangePasswordRequest {

    @SerializedName("currentPassword")
    private final String currentPassword;

    @SerializedName("newPassword")
    private final String newPassword;

    public ChangePasswordRequest(@NonNull String currentPassword, @NonNull String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    @NonNull
    public String getCurrentPassword() {
        return currentPassword;
    }

    @NonNull
    public String getNewPassword() {
        return newPassword;
    }
}