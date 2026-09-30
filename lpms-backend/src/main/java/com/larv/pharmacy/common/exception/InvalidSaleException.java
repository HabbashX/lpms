package com.larv.pharmacy.common.exception;

public class InvalidSaleException extends PharmacyException {

    public InvalidSaleException(String message) {
        super("INVALID_SALE", org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }
}
