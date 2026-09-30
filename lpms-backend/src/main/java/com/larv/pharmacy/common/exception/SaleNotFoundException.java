package com.larv.pharmacy.common.exception;

public class SaleNotFoundException extends ResourceNotFoundException {

    public SaleNotFoundException(Long id) {
        super("Sale", id);
    }
}
