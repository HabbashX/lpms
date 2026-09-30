package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Deliberately identical message for unknown username and wrong password in
 * order to prevent account enumeration.
 */
public class InvalidCredentialsException extends PharmacyException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
