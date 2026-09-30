package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-specific exceptions. Carries a stable machine
 * readable {@code code} plus the HTTP status returned by the global handler.
 */
public abstract class PharmacyException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected PharmacyException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    protected PharmacyException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
