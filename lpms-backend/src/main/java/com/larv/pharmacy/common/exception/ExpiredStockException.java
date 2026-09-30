package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class ExpiredStockException extends PharmacyException {

    public ExpiredStockException(String message) {
        super("EXPIRED_STOCK", HttpStatus.CONFLICT, message);
    }
}
