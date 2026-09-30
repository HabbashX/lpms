package com.larv.pharmacy.common.exception;

public class CustomerNotFoundException extends ResourceNotFoundException {

    public CustomerNotFoundException(Long id) {
        super("Customer", id);
    }

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
