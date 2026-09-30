package com.larv.pharmacy.common.exception;

public class CustomerAccountException extends PharmacyException {

    public CustomerAccountException(String code, String message) {
        super(code, org.springframework.http.HttpStatus.CONFLICT, message);
    }

    public CustomerAccountException(String message) {
        this("CUSTOMER_ACCOUNT_ERROR", message);
    }
}
