package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by the security filter when the authenticated user still has the
 * bootstrap password and must rotate it before using the API.
 */
public class PasswordChangeRequiredException extends PharmacyException {

    public PasswordChangeRequiredException() {
        super("PASSWORD_CHANGE_REQUIRED", HttpStatus.FORBIDDEN,
                "You must change your password before using the API");
    }
}
