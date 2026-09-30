package com.larv.pharmacy.security;

import com.larv.pharmacy.common.exception.InvalidRequestException;

/**
 * Shared password rules for account creation, admin resets and self-service
 * password changes.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password, String username) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new InvalidRequestException("WEAK_PASSWORD",
                    "Password must be between 8 and 72 characters long");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase(java.util.Locale.ROOT).contains(username.toLowerCase(java.util.Locale.ROOT))) {
            throw new InvalidRequestException("WEAK_PASSWORD", "Password must not contain the username");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new InvalidRequestException("WEAK_PASSWORD",
                    "Password must contain at least one letter and one digit");
        }
    }
}
