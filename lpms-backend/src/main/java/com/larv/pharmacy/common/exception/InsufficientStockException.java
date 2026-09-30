package com.larv.pharmacy.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends PharmacyException {

    private final String drugName;
    private final int available;
    private final int requested;

    public InsufficientStockException(String drugName, int available, int requested) {
        super("INSUFFICIENT_STOCK", HttpStatus.CONFLICT,
                "Insufficient stock for " + drugName + " (available: " + available + ", requested: " + requested + ")");
        this.drugName = drugName;
        this.available = available;
        this.requested = requested;
    }

    public String getDrugName() {
        return drugName;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
