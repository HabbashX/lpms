package com.larv.pharmacy.common.exception;

public class DrugNotFoundException extends ResourceNotFoundException {

    public DrugNotFoundException(Long id) {
        super("Drug", id);
    }

    public DrugNotFoundException(String message) {
        super(message);
    }
}
