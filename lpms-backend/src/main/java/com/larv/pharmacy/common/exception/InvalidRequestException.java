package com.larv.pharmacy.common.exception;

/**
 * Raised when the requested operation would leave the system in an
 * inconsistent state (insufficient stock, expired stock, bad sale, ...).
 */
public class InvalidRequestException extends PharmacyException {

    public InvalidRequestException(String code, String message) {
        super(code, org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    public InvalidRequestException(String message) {
        this("INVALID_REQUEST", message);
    }
}
