package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends PharmacyException {

    public UsernameAlreadyExistsException(String username) {
        super("USERNAME_ALREADY_EXISTS", HttpStatus.CONFLICT, "Username already exists: " + username);
    }
}
