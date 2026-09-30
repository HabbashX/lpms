package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class RefundException extends PharmacyException {

    public RefundException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }

    public RefundException(String message) {
        this("INVALID_REFUND", message);
    }
}
