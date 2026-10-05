package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * {@code PATCH /users/{id}/password} body. ADMIN only.
 *
 * <p>Sets a temporary password: the user's {@code mustChangePassword} becomes true,
 * so on their next login they are forced through the non-dismissable Change Password
 * screen. All of their existing tokens are invalidated.</p>
 */
public final class UpdateUserPasswordRequest {

    @SerializedName("password")
    private final String password;

    public UpdateUserPasswordRequest(@NonNull String password) {
        this.password = password;
    }

    @NonNull
    public String getPassword() {
        return password;
    }
}