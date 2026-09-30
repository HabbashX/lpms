package com.larv.pharmacy.common.exception;

/**
 * Business-rule violation that cannot be fixed by changing the request shape
 * (e.g. paying more than the outstanding debt). Returns HTTP 409.
 */
public class BusinessRuleException extends PharmacyException {

    public BusinessRuleException(String code, String message) {
        super(code, org.springframework.http.HttpStatus.CONFLICT, message);
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }
}
