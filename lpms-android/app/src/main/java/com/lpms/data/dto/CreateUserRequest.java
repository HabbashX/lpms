package com.lpms.data.dto;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

/**
 * {@code POST /users} body → 201. ADMIN only.
 *
 * <p>Validation: username 3–50 chars matching {@code ^[a-zA-Z0-9._-]+$}, password
 * 8–72 chars, role one of ADMIN / PHARMACIST / EMPLOYEE. There is no public
 * registration endpoint.</p>
 */
public final class CreateUserRequest {

    @SerializedName("username")
    private final String username;

    @SerializedName("password")
    private final String password;

    @SerializedName("role")
    private final String role;

    public CreateUserRequest(@NonNull String username, @NonNull String password, @NonNull String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getPassword() {
        return password;
    }

    @NonNull
    public String getRole() {
        return role;
    }
}