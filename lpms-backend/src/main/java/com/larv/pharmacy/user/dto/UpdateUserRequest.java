package com.larv.pharmacy.user.dto;

import com.larv.pharmacy.user.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]*$", message = "username may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotNull(message = "role is required")
        Role role) {
}
