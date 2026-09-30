package com.larv.pharmacy.common.exception;

public class ResourceNotFoundException extends PharmacyException {

    public ResourceNotFoundException(String entityType, Object id) {
        super("NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND,
                entityType + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND, message);
    }
}
