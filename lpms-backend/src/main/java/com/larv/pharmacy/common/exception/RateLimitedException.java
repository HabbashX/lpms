package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends PharmacyException {

    public RateLimitedException(String message) {
        super("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
