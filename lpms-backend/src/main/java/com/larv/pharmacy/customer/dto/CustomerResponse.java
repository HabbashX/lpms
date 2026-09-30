package com.larv.pharmacy.customer.dto;

import com.larv.pharmacy.customer.Customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String address,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getNotes(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
